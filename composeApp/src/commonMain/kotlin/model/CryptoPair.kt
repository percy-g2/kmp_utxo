package model

sealed class CryptoPair(val symbol: String) {
    data object BTCUSDT : CryptoPair("BTCUSDT")
    data object ETHUSDT : CryptoPair("ETHUSDT")
    data object BNBUSDT : CryptoPair("BNBUSDT")
    data object ADAUSDT : CryptoPair("ADAUSDT")
    data object XRPUSDT : CryptoPair("XRPUSDT")
    data object SOLUSDT : CryptoPair("SOLUSDT")
    data object DOTUSDT : CryptoPair("DOTUSDT")
    data object AVAXUSDT : CryptoPair("AVAXUSDT")
    data object DOGEUSDT : CryptoPair("DOGEUSDT")
    data object SHIBUSDT : CryptoPair("SHIBUSDT")
    data object MATICUSDT : CryptoPair("MATICUSDT")
    data object LTCUSDT : CryptoPair("LTCUSDT")
    data object LINKUSDT : CryptoPair("LINKUSDT")
    data object UNIUSDT : CryptoPair("UNIUSDT")
    data object BCHUSDT : CryptoPair("BCHUSDT")
    data object XLMUSDT : CryptoPair("XLMUSDT")
    data object ATOMUSDT : CryptoPair("ATOMUSDT")
    data object ICPUSDT : CryptoPair("ICPUSDT")
    data object SANDUSDT : CryptoPair("SANDUSDT")
    data object AAVEUSDT : CryptoPair("AAVEUSDT")
    data object FILUSDT : CryptoPair("FILUSDT")
    data object TRXUSDT : CryptoPair("TRXUSDT")
    data object VETUSDT : CryptoPair("VETUSDT")
    data object GRTUSDT : CryptoPair("GRTUSDT")
    data object EOSUSDT : CryptoPair("EOSUSDT")
    data object HBARUSDT : CryptoPair("HBARUSDT")
    data object NEARUSDT : CryptoPair("NEARUSDT")
    data object FTMUSDT : CryptoPair("FTMUSDT")
    data object LUNAUSDT : CryptoPair("LUNAUSDT")
    data object ALGOUSDT : CryptoPair("ALGOUSDT")
    data object EGLDUSDT : CryptoPair("EGLDUSDT")
    data object THETAUSDT : CryptoPair("THETAUSDT")
    data object KSMUSDT : CryptoPair("KSMUSDT")
    data object CHZUSDT : CryptoPair("CHZUSDT")
    data object MKRUSDT : CryptoPair("MKRUSDT")
    data object COMPUSDT : CryptoPair("COMPUSDT")
    data object ZILUSDT : CryptoPair("ZILUSDT")
    data object GALAUSDT : CryptoPair("GALAUSDT")
    data object ENJUSDT : CryptoPair("ENJUSDT")
    data object SUSHIUSDT : CryptoPair("SUSHIUSDT")
    data object RAYUSDT : CryptoPair("RAYUSDT")
    data object SRMUSDT : CryptoPair("SRMUSDT")
    data object LRCUSDT : CryptoPair("LRCUSDT")
    data object CELRUSDT : CryptoPair("CELRUSDT")
    data object CVCUSDT : CryptoPair("CVCUSDT")
    data object HOTUSDT : CryptoPair("HOTUSDT")
    data object STMXUSDT : CryptoPair("STMXUSDT")
    data object STORJUSDT : CryptoPair("STORJUSDT")
    data object RENUSDT : CryptoPair("RENUSDT")
    data object BALUSDT : CryptoPair("BALUSDT")
    data object OCEANUSDT : CryptoPair("OCEANUSDT")
    data object NKNUSDT : CryptoPair("NKNUSDT")
    data object ANTUSDT : CryptoPair("ANTUSDT")
    data object KEEPUSDT : CryptoPair("KEEPUSDT")
    data object RSRUSDT : CryptoPair("RSRUSDT")
    data object COTIUSDT : CryptoPair("COTIUSDT")
    data object DGBUSDT : CryptoPair("DGBUSDT")
    data object CTKUSDT : CryptoPair("CTKUSDT")
    data object ALICEUSDT : CryptoPair("ALICEUSDT")
    data object AXSUSDT : CryptoPair("AXSUSDT")
    data object CELUSDT : CryptoPair("CELUSDT")
    data object CTSIUSDT : CryptoPair("CTSIUSDT")
    data object LTOUSDT : CryptoPair("LTOUSDT")
    data object OXTUSDT : CryptoPair("OXTUSDT")
    data object SXPUSDT : CryptoPair("SXPUSDT")
    data object DENTUSDT : CryptoPair("DENTUSDT")
    data object KAVAUSDT : CryptoPair("KAVAUSDT")
    data object ZRXUSDT : CryptoPair("ZRXUSDT")

    companion object {
        fun fromString(symbol: String): CryptoPair? {
            return when (symbol) {
                "BTCUSDT" -> BTCUSDT
                "ETHUSDT" -> ETHUSDT
                "BNBUSDT" -> BNBUSDT
                "ADAUSDT" -> ADAUSDT
                "XRPUSDT" -> XRPUSDT
                "SOLUSDT" -> SOLUSDT
                "DOTUSDT" -> DOTUSDT
                "AVAXUSDT" -> AVAXUSDT
                "DOGEUSDT" -> DOGEUSDT
                "SHIBUSDT" -> SHIBUSDT
                "MATICUSDT" -> MATICUSDT
                "LTCUSDT" -> LTCUSDT
                "LINKUSDT" -> LINKUSDT
                "UNIUSDT" -> UNIUSDT
                "BCHUSDT" -> BCHUSDT
                "XLMUSDT" -> XLMUSDT
                "ATOMUSDT" -> ATOMUSDT
                "ICPUSDT" -> ICPUSDT
                "SANDUSDT" -> SANDUSDT
                "AAVEUSDT" -> AAVEUSDT
                "FILUSDT" -> FILUSDT
                "TRXUSDT" -> TRXUSDT
                "VETUSDT" -> VETUSDT
                "GRTUSDT" -> GRTUSDT
                "EOSUSDT" -> EOSUSDT
                "HBARUSDT" -> HBARUSDT
                "NEARUSDT" -> NEARUSDT
                "FTMUSDT" -> FTMUSDT
                "LUNAUSDT" -> LUNAUSDT
                "ALGOUSDT" -> ALGOUSDT
                "EGLDUSDT" -> EGLDUSDT
                "THETAUSDT" -> THETAUSDT
                "KSMUSDT" -> KSMUSDT
                "CHZUSDT" -> CHZUSDT
                "MKRUSDT" -> MKRUSDT
                "COMPUSDT" -> COMPUSDT
                "ZILUSDT" -> ZILUSDT
                "GALAUSDT" -> GALAUSDT
                "ENJUSDT" -> ENJUSDT
                "SUSHIUSDT" -> SUSHIUSDT
                "RAYUSDT" -> RAYUSDT
                "SRMUSDT" -> SRMUSDT
                "LRCUSDT" -> LRCUSDT
                "CELRUSDT" -> CELRUSDT
                "CVCUSDT" -> CVCUSDT
                "HOTUSDT" -> HOTUSDT
                "STMXUSDT" -> STMXUSDT
                "STORJUSDT" -> STORJUSDT
                "RENUSDT" -> RENUSDT
                "BALUSDT" -> BALUSDT
                "OCEANUSDT" -> OCEANUSDT
                "NKNUSDT" -> NKNUSDT
                "ANTUSDT" -> ANTUSDT
                "KEEPUSDT" -> KEEPUSDT
                "RSRUSDT" -> RSRUSDT
                "COTIUSDT" -> COTIUSDT
                "DGBUSDT" -> DGBUSDT
                "CTKUSDT" -> CTKUSDT
                "ALICEUSDT" -> ALICEUSDT
                "AXSUSDT" -> AXSUSDT
                "CELUSDT" -> CELUSDT
                "CTSIUSDT" -> CTSIUSDT
                "LTOUSDT" -> LTOUSDT
                "OXTUSDT" -> OXTUSDT
                "SXPUSDT" -> SXPUSDT
                "DENTUSDT" -> DENTUSDT
                "KAVAUSDT" -> KAVAUSDT
                "ZRXUSDT" -> ZRXUSDT
                else -> null
            }
        }

        fun getAllPairs(): List<String> {
            return listOf(
                BTCUSDT.symbol, ETHUSDT.symbol, BNBUSDT.symbol, ADAUSDT.symbol, XRPUSDT.symbol,
                SOLUSDT.symbol, DOTUSDT.symbol, AVAXUSDT.symbol, DOGEUSDT.symbol, SHIBUSDT.symbol,
                MATICUSDT.symbol, LTCUSDT.symbol, LINKUSDT.symbol, UNIUSDT.symbol, BCHUSDT.symbol,
                XLMUSDT.symbol, ATOMUSDT.symbol, ICPUSDT.symbol, SANDUSDT.symbol, AAVEUSDT.symbol,
                FILUSDT.symbol, TRXUSDT.symbol, VETUSDT.symbol, GRTUSDT.symbol, EOSUSDT.symbol,
                HBARUSDT.symbol, NEARUSDT.symbol, FTMUSDT.symbol, LUNAUSDT.symbol, ALGOUSDT.symbol,
                EGLDUSDT.symbol, THETAUSDT.symbol, KSMUSDT.symbol, CHZUSDT.symbol, MKRUSDT.symbol,
                COMPUSDT.symbol, ZILUSDT.symbol, GALAUSDT.symbol, ENJUSDT.symbol, SUSHIUSDT.symbol,
                RAYUSDT.symbol, SRMUSDT.symbol, LRCUSDT.symbol, CELRUSDT.symbol, CVCUSDT.symbol,
                HOTUSDT.symbol, STMXUSDT.symbol, STORJUSDT.symbol, RENUSDT.symbol, BALUSDT.symbol,
                OCEANUSDT.symbol, NKNUSDT.symbol, ANTUSDT.symbol, KEEPUSDT.symbol, RSRUSDT.symbol,
                COTIUSDT.symbol, DGBUSDT.symbol, CTKUSDT.symbol, ALICEUSDT.symbol, AXSUSDT.symbol,
                CELUSDT.symbol, CTSIUSDT.symbol, LTOUSDT.symbol, OXTUSDT.symbol, SXPUSDT.symbol,
                DENTUSDT.symbol, KAVAUSDT.symbol, ZRXUSDT.symbol
            )
        }
    }
}
