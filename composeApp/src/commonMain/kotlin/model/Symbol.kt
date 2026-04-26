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

@Serializable
internal data class ExchangeInfoResponse(val symbols: List<ExchangeSymbol>)

@Serializable
internal data class ExchangeSymbol(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val quoteAsset: String,
    val isSpotTradingAllowed: Boolean = false,
    val isMarginTradingAllowed: Boolean = false,
    val permissions: List<String> = emptyList()
)

internal fun ExchangeInfoResponse.toMarginSymbols(): MarginSymbols = MarginSymbols(
    code = "000000",
    message = null,
    messageDetail = null,
    data = symbols.map { s ->
        TradingPair(
            id = s.symbol,
            symbol = s.symbol,
            base = s.baseAsset,
            quote = s.quoteAsset,
            isMarginTrade = s.isMarginTradingAllowed || "MARGIN" in s.permissions,
            isBuyAllowed = s.status == "TRADING",
            isSellAllowed = s.status == "TRADING",
            status = s.status,
            delistedTime = null
        )
    }
)