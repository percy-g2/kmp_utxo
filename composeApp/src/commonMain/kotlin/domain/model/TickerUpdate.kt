package domain.model

import model.TickerData

/**
 * Minimal ticker snapshot used for price alert evaluation.
 */
data class TickerUpdate(
    val symbol: String,
    val lastPrice: Double,
    val percentChange24h: Double,
)

fun TickerData.toTickerUpdate(): TickerUpdate {
    val price = lastPrice.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    val pct = priceChangePercent.toDoubleOrNull() ?: 0.0
    return TickerUpdate(symbol = symbol, lastPrice = price, percentChange24h = pct)
}

fun Map<String, TickerData>.toTickerUpdateMap(): Map<String, TickerUpdate> = mapValues { (_, v) -> v.toTickerUpdate() }
