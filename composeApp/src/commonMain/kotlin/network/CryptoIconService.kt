package network

import logging.AppLogger

object CryptoIconService {
    // Common quote currencies to extract base symbol
    private val quoteCurrencies = listOf(
        "USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", 
        "DAI", "TUSD", "EUR", "GBP", "JPY", "USD1"
    )

    /**
     * Extracts the base symbol from a trading pair symbol.
     * Example: "BTCUSDT" -> "BTC"
     */
    fun extractBaseSymbol(symbol: String, tradingPair: String? = null): String {
        var baseSymbol = symbol.uppercase()
        
        // First try with provided trading pair
        if (tradingPair != null && baseSymbol.endsWith(tradingPair.uppercase()) && baseSymbol.length > tradingPair.length) {
            return baseSymbol.removeSuffix(tradingPair.uppercase())
        }
        
        // Fallback: try common quote currencies (sorted by length descending to match longer ones first)
        for (quote in quoteCurrencies.sortedByDescending { it.length }) {
            if (baseSymbol.endsWith(quote, ignoreCase = true) && baseSymbol.length > quote.length) {
                return baseSymbol.removeSuffix(quote)
            }
        }
        
        // If no match found, return original symbol
        return baseSymbol
    }

    /**
     * Gets icon URL for a cryptocurrency symbol using CoinGecko API.
     * Uses only API-proxied URLs which are CDN-safe.
     * 
     * @param symbol The cryptocurrency symbol (e.g., "BTC", "ETH")
     * @param tradingPair Optional trading pair to help extract base symbol
     * @param size Preferred size: "thumb", "small", or "large" (default: "small")
     * @return The icon URL or null if not available
     */
    suspend fun getIconUrl(symbol: String, tradingPair: String? = null, size: String = "small"): String? {
        val baseSymbol = extractBaseSymbol(symbol, tradingPair)
        
        // Use only CoinGecko API (API-proxied URLs are CDN-safe)
        val coinGeckoUrl = getCoinGeckoIconUrl(baseSymbol, size)
        if (coinGeckoUrl != null) {
            AppLogger.logger.d { "Using CoinGecko API URL for $baseSymbol: $coinGeckoUrl" }
            return coinGeckoUrl
        }
        
        AppLogger.logger.w { "CoinGecko API did not return URL for $baseSymbol" }
        return null
    }

    /**
     * Gets icon URL from CoinGecko API.
     * Uses API-proxied URLs which are CDN-safe and don't have 403 issues.
     */
    private suspend fun getCoinGeckoIconUrl(symbol: String, size: String): String? {
        return try {
            // Use the CoinGeckoService to fetch the icon URL
            network.getCoinGeckoIconUrl(symbol, size)
        } catch (e: Exception) {
            AppLogger.logger.w(throwable = e) { "Failed to get CoinGecko icon URL for $symbol" }
            null
        }
    }

}

