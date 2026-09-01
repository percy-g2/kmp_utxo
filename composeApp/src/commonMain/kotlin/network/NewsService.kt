package network

import createNewsHttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import ktx.decodeHtmlEntities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logging.AppLogger
import model.NewsItem
import wrapRssUrlForPlatform
import kotlin.time.ExperimentalTime

/**
 * Outcome of reading one provider's feed.
 *
 * The two cases have to stay distinguishable. [Success] with an empty list means the feed was read
 * fine and simply carries nothing about this coin; [Failed] means the transport is broken. Folding
 * both into an empty list is what let a total CORS outage on the web build masquerade as the
 * ordinary "no news available" state.
 */
sealed interface NewsFetchResult {
    data class Success(val items: List<NewsItem>) : NewsFetchResult
    data object Failed : NewsFetchResult
}

class NewsService {
    private val httpClient = createNewsHttpClient()

    @OptIn(ExperimentalTime::class)
    suspend fun fetchNewsFromProvider(
        provider: model.RssProvider,
        coinSymbol: String
    ): NewsFetchResult {
        return try {
            AppLogger.logger.d { "NewsService: Fetching from ${provider.name} (${provider.id}) for $coinSymbol" }
            val news = fetchRSSFeed(provider.url, coinSymbol)
            if (news != null) {
                AppLogger.logger.d { "NewsService: Found ${news.size} news items from ${provider.name} for $coinSymbol" }
                NewsFetchResult.Success(news)
            } else {
                AppLogger.logger.w { "NewsService: Failed to fetch or parse ${provider.name} RSS for $coinSymbol" }
                NewsFetchResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // See fetchRSSFeed: browser fetch failures arrive as kotlin.Error, not Exception.
            AppLogger.logger.e(throwable = e) { "NewsService: Error fetching from ${provider.name}" }
            NewsFetchResult.Failed
        }
    }

    private suspend fun fetchRSSFeed(url: String, coinSymbol: String): List<NewsItem>? {
        // wasmJS returns a fallback chain of CORS proxies; native platforms return [url].
        val candidates = wrapRssUrlForPlatform(url)
        for ((idx, candidate) in candidates.withIndex()) {
            try {
                val response: HttpResponse = httpClient.get(candidate)
                if (response.status == HttpStatusCode.OK) {
                    val xmlContent = withContext(Dispatchers.Default) {
                        response.body<String>()
                    }
                    if (url.contains("coindesk", ignoreCase = true)) {
                        AppLogger.logger.d { "CoinDesk RSS sample (first 500 chars): ${xmlContent.take(500)}" }
                    }
                    return parseRSSFeed(xmlContent, coinSymbol)
                }
                AppLogger.logger.w { "RSS candidate ${idx + 1}/${candidates.size} returned ${response.status} for $url" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                AppLogger.logger.w(throwable = e) { "RSS candidate ${idx + 1}/${candidates.size} timed out for $url" }
            } catch (e: Throwable) {
                // Throwable, not Exception: on the JS/Wasm engine Ktor reports a failed fetch as
                // `kotlin.Error("Fail to fetch")` (io.ktor.client.engine.js.compatibility.commonFetch),
                // and kotlin.Error is a Throwable that is NOT an Exception. Catching Exception here
                // let every browser-side network failure escape the news pipeline uncaught, which
                // killed the whole load job and left the shimmer placeholders spinning forever.
                AppLogger.logger.w(throwable = e) { "RSS candidate ${idx + 1}/${candidates.size} failed for $url" }
            }
        }
        AppLogger.logger.w { "All ${candidates.size} RSS candidates failed for $url" }
        return null
    }

    internal fun parseRSSFeed(xml: String, coinSymbol: String): List<NewsItem> {
        val newsItems = mutableListOf<NewsItem>()
        
        // Extract base coin symbol (e.g., "BTC" from "BTCUSDT")
        // Common quote currencies to remove (sorted by length descending to match longer ones first)
        val quoteCurrencies = listOf("USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", "DAI", "TUSD", "EUR", "GBP", "JPY")
        var baseSymbol = coinSymbol.uppercase()
        
        // Remove quote currency from the end
        for (quote in quoteCurrencies.sortedByDescending { it.length }) {
            if (baseSymbol.endsWith(quote, ignoreCase = true) && baseSymbol.length > quote.length) {
                baseSymbol = baseSymbol.removeSuffix(quote)
                break
            }
        }
        
        // If baseSymbol is empty or same as original after removal, try to extract differently
        if (baseSymbol.isEmpty() || baseSymbol == coinSymbol.uppercase()) {
            // Try to find a known base coin in the symbol
            val knownCoins = listOf("BTC", "ETH", "BNB", "ADA", "SOL", "XRP", "DOT", "DOGE", "MATIC", "AVAX", "LINK", "UNI", "LTC", "ATOM", "ETC", "XLM", "ALGO", "VET", "FIL", "TRX", "EOS", "AAVE", "MKR", "COMP", "YFI", "SUSHI", "SNX", "CRV", "1INCH", "BAL", "ZRX", "ENJ", "MANA", "SAND", "AXS", "GALA", "CHZ", "FLOW", "THETA", "HBAR", "NEAR", "FTM", "ICP", "APT", "ARB", "OP", "SUI", "SEI", "TIA", "INJ", "RUNE", "KAVA", "WAVES", "ZEC", "DASH", "XMR")
            for (coin in knownCoins.sortedByDescending { it.length }) {
                if (baseSymbol.startsWith(coin, ignoreCase = true)) {
                    baseSymbol = coin
                    break
                }
            }
        }
        
        // Simple XML parsing - look for <item> tags
        // Use (?s) flag to make . match newlines
        val itemPattern = Regex("(?s)<item>(.*?)</item>")
        val items = itemPattern.findAll(xml)

        items.forEach { match ->
            val itemXml = match.groupValues[1]
            
            val title = extractTagContent(itemXml, "title") ?: return@forEach
            var description = extractTagContent(itemXml, "description") ?: ""
            var link = extractTagContent(itemXml, "link") ?: ""
            val pubDate = extractTagContent(itemXml, "pubDate") ?: ""
            
            // Clean CDATA tags from description and link if present
            description = description.replace(Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1").trim()
            link = link.replace(Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1").trim()
            
            // Also try to extract description from content:encoded or content tag (used by some RSS feeds)
            if (description.isEmpty()) {
                description = extractTagContent(itemXml, "content:encoded") ?: 
                              extractTagContent(itemXml, "content") ?: 
                              extractTagContent(itemXml, "summary") ?: ""
                // Clean CDATA from alternative description sources
                description = description.replace(Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1").trim()
            }
            
            // Filter by coin symbol in title or description
            val searchText = "$title $description".uppercase()
            val upperBaseSymbol = baseSymbol.uppercase()
            val upperCoinSymbol = coinSymbol.uppercase()
            
            // Check if news item mentions the coin
            var shouldInclude = false
            
            // Direct symbol match
            if (upperBaseSymbol.isNotEmpty() && upperBaseSymbol.length >= 2) {
                // Check for exact symbol match with word boundaries
                // Escape special regex characters in the symbol
                val escapedSymbol = upperBaseSymbol.replace(Regex("[.*+?^${'$'}{}()|\\[\\]\\\\]"), "\\\\$0")
                val symbolPattern = Regex("\\b$escapedSymbol\\b", RegexOption.IGNORE_CASE)
                shouldInclude = symbolPattern.containsMatchIn(searchText) || 
                    searchText.contains(upperBaseSymbol, ignoreCase = true)
            }
            
            // Check full symbol (e.g., BTCUSDT)
            if (!shouldInclude && upperCoinSymbol.length > upperBaseSymbol.length) {
                shouldInclude = searchText.contains(upperCoinSymbol, ignoreCase = true)
            }
            
            // Check for common coin name variations (Bitcoin, Ethereum, etc.)
            if (!shouldInclude) {
                when (upperBaseSymbol) {
                    "BTC" -> shouldInclude = searchText.contains("BITCOIN", ignoreCase = true) || 
                        searchText.contains(" BTC ", ignoreCase = true) ||
                        searchText.startsWith("BTC", ignoreCase = true)
                    "ETH" -> shouldInclude = searchText.contains("ETHEREUM", ignoreCase = true) || 
                        searchText.contains(" ETH ", ignoreCase = true) ||
                        searchText.startsWith("ETH", ignoreCase = true)
                    "BNB" -> shouldInclude = searchText.contains("BINANCE", ignoreCase = true) || 
                        searchText.contains(" BNB ", ignoreCase = true)
                    "ADA" -> shouldInclude = searchText.contains("CARDANO", ignoreCase = true) || 
                        searchText.contains(" ADA ", ignoreCase = true)
                    "SOL" -> shouldInclude = searchText.contains("SOLANA", ignoreCase = true) || 
                        searchText.contains(" SOL ", ignoreCase = true)
                    "XRP" -> shouldInclude = searchText.contains("RIPPLE", ignoreCase = true) || 
                        searchText.contains(" XRP ", ignoreCase = true)
                    "DOT" -> shouldInclude = searchText.contains("POLKADOT", ignoreCase = true) || 
                        searchText.contains(" DOT ", ignoreCase = true)
                    "DOGE" -> shouldInclude = searchText.contains("DOGECOIN", ignoreCase = true) || 
                        searchText.contains("DOGE", ignoreCase = true)
                    "MATIC" -> shouldInclude = searchText.contains("POLYGON", ignoreCase = true) || 
                        searchText.contains("MATIC", ignoreCase = true)
                    "AVAX" -> shouldInclude = searchText.contains("AVALANCHE", ignoreCase = true) || 
                        searchText.contains("AVAX", ignoreCase = true)
                }
            }
            
            if (shouldInclude) {
                
                val source = when {
                    link.contains("coindesk", ignoreCase = true) -> "CoinDesk"
                    link.contains("cointelegraph", ignoreCase = true) -> "CoinTelegraph"
                    link.contains("decrypt.co", ignoreCase = true) -> "Decrypt"
                    link.contains("theblock.co", ignoreCase = true) -> "The Block"
                    link.contains("cryptoslate.com", ignoreCase = true) -> "CryptoSlate"
                    link.contains("u.today", ignoreCase = true) -> "U.Today"
                    link.contains("bitcoinmagazine.com", ignoreCase = true) -> "Bitcoin Magazine"
                    link.contains("beincrypto.com", ignoreCase = true) -> "BeInCrypto"
                    else -> "Crypto News"
                }
                
                // Clean description - remove HTML tags and CDATA, but keep text content
                val cleanedDescription = if (description.isNotEmpty()) {
                    cleanHtml(description)
                } else {
                    "" // Keep empty if no description found
                }
                
                newsItems.add(
                    NewsItem(
                        title = cleanHtml(title),
                        description = cleanedDescription,
                        link = link,
                        pubDate = pubDate,
                        source = source
                    )
                )
            }
        }
        
        return newsItems
    }

    private fun extractTagContent(xml: String, tagName: String): String? {
        // Use (?s) flag to make . match newlines
        // Try to match tag with or without namespace prefix (e.g., content:encoded)
        val patterns = listOf(
            Regex("(?s)<$tagName[^>]*>(.*?)</$tagName>"),  // Standard tag
            Regex("(?s)<[^:]*:$tagName[^>]*>(.*?)</[^:]*:$tagName>")  // Namespace prefixed tag
        )
        
        for (pattern in patterns) {
            val match = pattern.find(xml)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }

    private fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""

        return html
            // Remove CDATA tags first if present
            .replace(Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1")
            // Remove HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode HTML entities — numeric (&#8217;, &#x2019;) and named
            // (&amp; &rsquo; &mdash; …). Replaces the old hand-rolled subset that
            // left &#8217; and friends rendering literally.
            .decodeHtmlEntities()
            // Clean up extra whitespace (including any decoded &nbsp;)
            .replace(Regex("[\\s\\u00A0]+"), " ")
            .trim()
    }

    fun close() {
        httpClient.close()
    }
}