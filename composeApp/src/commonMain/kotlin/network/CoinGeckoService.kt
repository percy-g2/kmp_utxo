package network

import createNewsHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logging.AppLogger

/**
 * CoinGecko API response models
 */
@Serializable
data class CoinGeckoImageUrls(
    val thumb: String? = null,
    val small: String? = null,
    val large: String? = null
)

@Serializable
data class CoinGeckoCoinData(
    val id: String,
    val symbol: String,
    val name: String,
    val image: CoinGeckoImageUrls? = null
)

/**
 * Service for fetching cryptocurrency icon URLs from CoinGecko API.
 * Uses API-proxied URLs which are CDN-safe and don't have 403 issues.
 */
class CoinGeckoService {
    private val httpClient = createNewsHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Cache for coin data to avoid excessive API calls
    private val coinDataCache = mutableMapOf<String, CoinGeckoCoinData?>()
    private val cacheMutex = Mutex()
    
    // Symbol to CoinGecko ID mapping for common coins
    // This helps avoid the need to fetch the full coins list
    private val symbolToCoinId = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "BNB" to "binancecoin",
        "ADA" to "cardano",
        "SOL" to "solana",
        "XRP" to "ripple",
        "DOT" to "polkadot",
        "DOGE" to "dogecoin",
        "MATIC" to "matic-network",
        "AVAX" to "avalanche-2",
        "LINK" to "chainlink",
        "UNI" to "uniswap",
        "LTC" to "litecoin",
        "ATOM" to "cosmos",
        "ETC" to "ethereum-classic",
        "XLM" to "stellar",
        "ALGO" to "algorand",
        "VET" to "vechain",
        "FIL" to "filecoin",
        "TRX" to "tron",
        "EOS" to "eos",
        "AAVE" to "aave",
        "MKR" to "maker",
        "COMP" to "compound-governance-token",
        "YFI" to "yearn-finance",
        "SUSHI" to "sushi",
        "SNX" to "synthetix-network-token",
        "CRV" to "curve-dao-token",
        "1INCH" to "1inch",
        "BAL" to "balancer",
        "ZRX" to "0x",
        "ENJ" to "enjincoin",
        "MANA" to "decentraland",
        "SAND" to "the-sandbox",
        "AXS" to "axie-infinity",
        "GALA" to "gala",
        "CHZ" to "chiliz",
        "FLOW" to "flow",
        "THETA" to "theta-token",
        "HBAR" to "hedera-hashgraph",
        "NEAR" to "near",
        "FTM" to "fantom",
        "ICP" to "internet-computer",
        "APT" to "aptos",
        "ARB" to "arbitrum",
        "OP" to "optimism",
        "SUI" to "sui",
        "SEI" to "sei-network",
        "TIA" to "celestia",
        "INJ" to "injective-protocol",
        "RUNE" to "thorchain",
        "KAVA" to "kava",
        "WAVES" to "waves",
        "ZEC" to "zcash",
        "DASH" to "dash",
        // Stablecoins
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "BUSD" to "binance-usd",
        "DAI" to "dai",
        "TUSD" to "true-usd",
        "FDUSD" to "first-digital-usd"
    )
    
    /**
     * Gets the CoinGecko coin ID for a given symbol.
     * First checks the mapping, then tries to fetch from API if not found.
     */
    private fun getCoinId(symbol: String): String? {
        val upperSymbol = symbol.uppercase()
        return symbolToCoinId[upperSymbol]
    }
    
    /**
     * Fetches coin data from CoinGecko API.
     * Uses caching to avoid excessive API calls.
     */
    private suspend fun fetchCoinData(coinId: String): CoinGeckoCoinData? {
        // Check cache first
        cacheMutex.withLock {
            coinDataCache[coinId]?.let { return it }
        }
        
        try {
            val response: HttpResponse = httpClient.get("https://api.coingecko.com/api/v3/coins/$coinId") {
                // Add localization parameter to reduce response size
                // We only need the image URLs
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val coinData: CoinGeckoCoinData = response.body()
                    
                    // Cache the result
                    cacheMutex.withLock {
                        coinDataCache[coinId] = coinData
                    }
                    
                    AppLogger.logger.d { "Fetched CoinGecko data for $coinId: ${coinData.image?.small}" }
                    return coinData
                }
                HttpStatusCode.NotFound -> {
                    AppLogger.logger.w { "CoinGecko coin not found: $coinId" }
                    // Cache null to avoid retrying
                    cacheMutex.withLock {
                        coinDataCache[coinId] = null
                    }
                    return null
                }
                HttpStatusCode.TooManyRequests -> {
                    AppLogger.logger.w { "CoinGecko rate limit exceeded for $coinId" }
                    return null
                }
                else -> {
                    AppLogger.logger.w { "CoinGecko API error for $coinId: ${response.status}" }
                    return null
                }
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Failed to fetch CoinGecko data for $coinId" }
            return null
        }
    }
    
    /**
     * Gets the icon URL for a cryptocurrency symbol using CoinGecko API.
     * Returns null if the coin is not found or API call fails.
     * 
     * @param symbol The cryptocurrency symbol (e.g., "BTC", "ETH")
     * @param size Preferred size: "thumb", "small", or "large"
     * @return The icon URL or null if not available
     */
    suspend fun getIconUrl(symbol: String, size: String = "small"): String? {
        val coinId = getCoinId(symbol) ?: return null
        
        val coinData = fetchCoinData(coinId) ?: return null
        
        return when (size.lowercase()) {
            "thumb" -> coinData.image?.thumb
            "small" -> coinData.image?.small
            "large" -> coinData.image?.large
            else -> coinData.image?.small
        }
    }
    
    /**
     * Clears the cache (useful for testing or memory management)
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            coinDataCache.clear()
        }
    }
}

// Singleton instance
private val coinGeckoService = CoinGeckoService()

/**
 * Gets the icon URL for a cryptocurrency symbol using CoinGecko API.
 * This is a convenience function that uses the singleton service.
 */
suspend fun getCoinGeckoIconUrl(symbol: String, size: String = "small"): String? {
    return coinGeckoService.getIconUrl(symbol, size)
}

