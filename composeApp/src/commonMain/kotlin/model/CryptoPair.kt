package model

sealed class CryptoPair(val symbol: String) {
    data object BTCUSDT : CryptoPair("BTCUSDT")
    data object ETHUSDT : CryptoPair("ETHUSDT")
    data object SOLUSDT : CryptoPair("SOLUSDT")

    companion object {
        fun fromString(symbol: String): CryptoPair? {
            return when (symbol) {
                "BTCUSDT" -> BTCUSDT
                "ETHUSDT" -> ETHUSDT
                "SOLUSDT" -> SOLUSDT
                else -> null
            }
        }
        fun getAllPairs(): List<String> {
            return listOf(BTCUSDT.symbol, ETHUSDT.symbol, SOLUSDT.symbol)
        }
    }
}
