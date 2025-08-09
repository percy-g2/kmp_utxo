package ktx

import model.TradingPair
import kotlin.math.roundToInt

fun String.toCryptoSymbol(): String = when(this.uppercase()) {
    "BTC" -> "₿"
    "USDT" -> "₮"
    "USDC" -> "¢"
    "ETH" -> "Ξ"
    "DOGE" -> "Ð"
    "FDUSD" -> "F"
    "DAI" -> "◈"
    "SOL" -> "◎"
    else -> this
}

fun String.formatVolume(): String {
    return try {
        val value = this.toDouble()
        when {
            value >= 1_000_000_000 -> {
                val billions = value / 1_000_000_000
                when {
                    billions >= 100 -> "${billions.roundToInt()}B"
                    billions >= 10 -> "${(billions * 10).roundToInt() / 10.0}B"
                    else -> "${(billions * 100).roundToInt() / 100.0}B"
                }
            }

            value >= 1_000_000 -> {
                val millions = value / 1_000_000
                when {
                    millions >= 100 -> "${millions.roundToInt()}M"
                    millions >= 10 -> "${(millions * 10).roundToInt() / 10.0}M"
                    else -> "${(millions * 100).roundToInt() / 100.0}M"
                }
            }

            value >= 1_000 -> {
                value.roundToInt().toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(",")
                    .reversed()
            }

            value >= 1 -> {
                "${(value * 100).roundToInt() / 100.0}"
            }

            else -> {
                "${(value * 10000).roundToInt() / 10000.0}"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        this
    }
}

fun String.formatPrice(symbol: String, tradingPairs: List<TradingPair>): String = runCatching {
    val selectedPair = tradingPairs.find { pair ->
        symbol.endsWith(pair.quote, ignoreCase = true)
    }?.quote.orEmpty()

    val updatedPrice = if (selectedPair == "USDT" || selectedPair == "USDC" || selectedPair == "FDUSD") {
        this.toDouble().formatAsCurrency()
    } else this

    "${selectedPair.toCryptoSymbol()} $updatedPrice"
}.getOrElse {
    this
}
