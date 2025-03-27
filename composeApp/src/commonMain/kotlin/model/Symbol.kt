package model

import kotlinx.serialization.Serializable

@Serializable
data class TickerDataInfo(
    val symbol: String,
    val quoteVolume: String,
    val quote: String
)


@Serializable
data class MarginSymbols(
    val code: String,
    val message: String? = null,
    val messageDetail: String? = null,
    val data: List<TradingPair>
)

@Serializable
data class TradingPair(
    val id: String,
    val symbol: String,
    val base: String,
    val quote: String,
    val isMarginTrade: Boolean,
    val isBuyAllowed: Boolean,
    val isSellAllowed: Boolean,
    val status: String,
    val delistedTime: String? = null
)

enum class CryptoSymbol(val currency: String, val symbol: String) {
    BTC("Bitcoin", "₿"),
    USDT("Tether", "₮"),
    USDC("USD Coin", "¢"),
    ETH("Ethereum", "Ξ"),
    DOGE("Dogecoin", "Ð"),
    FDUSD("First Digital USD", "F"),
    DAI("Dai", "◈"),
    SOL("Solana", "◎");

    companion object {
        fun fromCurrency(currency: String): CryptoSymbol? =
            entries.find { it.currency.equals(currency, ignoreCase = true) }

        fun fromSymbol(symbol: String): CryptoSymbol? =
            entries.find { it.symbol == symbol }
    }

    override fun toString(): String = "$currency ($symbol)"
}