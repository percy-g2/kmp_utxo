package ktx

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