package network

import createNewsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ktx.parseRssDate
import kotlinx.datetime.Instant
import model.NewsItem
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class NewsService {
    private val httpClient = createNewsHttpClient()
    private val cache = mutableMapOf<String, CachedNews>()
    private val cacheMutex = Mutex()
    private val cacheDurationMs = 5 * 60 * 1000L // 5 minutes cache

    private data class CachedNews(
        val news: List<NewsItem>,
        val timestamp: Long
    )

    @OptIn(ExperimentalTime::class)
    suspend fun fetchNewsForCoin(
        coinSymbol: String,
        enabledProviders: Set<String> = model.RssProvider.DEFAULT_ENABLED_PROVIDERS
    ): Result<List<NewsItem>> {
        return try {
            // Make a local copy to ensure we're using the correct providers
            val providersToUse = enabledProviders.toSet()
            
            // Include enabled providers in cache key to invalidate cache when providers change
            val providersKey = providersToUse.sorted().joinToString(",")
            val cacheKey = "${coinSymbol.uppercase()}_$providersKey"
            
            println("NewsService: Cache key for $coinSymbol: $cacheKey")
            println("NewsService: Enabled providers set: $providersToUse")
            
            // Check cache first
            cacheMutex.withLock {
                val cached = cache[cacheKey]
                if (cached != null && (Clock.System.now().toEpochMilliseconds() - cached.timestamp) < cacheDurationMs) {
                    println("NewsService: Returning cached news (${cached.news.size} items) for key: $cacheKey")
                    return Result.success(cached.news)
                } else {
                    println("NewsService: Cache miss or expired for key: $cacheKey")
                }
            }

            // Fetch from multiple RSS sources
            val allNews = mutableListOf<NewsItem>()
            
            // If no providers are enabled, return empty list immediately
            if (providersToUse.isEmpty()) {
                println("NewsService: No providers enabled, returning empty news list")
                return Result.success(emptyList())
            }
            
            // Fetch only from enabled providers - use local copy
            println("NewsService: Fetching news for $coinSymbol with enabled providers: $providersToUse")
            model.RssProvider.ALL_PROVIDERS.forEach { provider ->
                if (providersToUse.contains(provider.id)) {
                    println("NewsService: Fetching from ${provider.name} (${provider.id})")
                    val news = fetchRSSFeed(provider.url, coinSymbol)
                    if (news != null) {
                        allNews.addAll(news)
                        println("NewsService: Found ${news.size} news items from ${provider.name} for $coinSymbol")
                    } else {
                        println("NewsService: Failed to fetch or parse ${provider.name} RSS for $coinSymbol")
                    }
                } else {
                    println("NewsService: Skipping ${provider.name} (${provider.id}) - not enabled")
                }
            }
            println("NewsService: Total news items collected: ${allNews.size}")

            // Sort by parsed date (newest first) and limit to 50
            val sortedNews = allNews
                .sortedWith(compareByDescending<NewsItem> { item ->
                    try {
                        parseRssDate(item.pubDate)
                    } catch (e: Exception) {
                        // If parsing fails, use epoch 0 (oldest) so unparseable dates go to the end
                        println("NewsService: Failed to parse date '${item.pubDate}': ${e.message}")
                        Instant.fromEpochMilliseconds(0)
                    }
                })
                .take(50)

            // Update cache
            cacheMutex.withLock {
                cache[cacheKey] = CachedNews(sortedNews, Clock.System.now().toEpochMilliseconds())
            }

            Result.success(sortedNews)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchRSSFeed(url: String, coinSymbol: String): List<NewsItem>? {
        return try {
            val response: HttpResponse = httpClient.get(url)
            if (response.status == HttpStatusCode.OK) {
                val xmlContent = withContext(Dispatchers.Default) {
                    response.body<String>()
                }
                // Debug: log first 500 chars to see feed structure
                if (url.contains("coindesk", ignoreCase = true)) {
                    println("CoinDesk RSS sample (first 500 chars): ${xmlContent.take(500)}")
                }
                parseRSSFeed(xmlContent, coinSymbol)
            } else {
                println("RSS feed returned status ${response.status} for $url")
                null
            }
        } catch (e: Exception) {
            println("Error fetching RSS feed $url: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseRSSFeed(xml: String, coinSymbol: String): List<NewsItem> {
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
            // Decode HTML entities
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "...")
            // Clean up extra whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun clearCache() {
        cacheMutex.withLock {
            println("NewsService: Clearing all cache entries (${cache.size} entries)")
            cache.clear()
        }
    }

    fun close() {
        httpClient.close()
    }
}