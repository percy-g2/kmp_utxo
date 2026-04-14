package ktx

/** Base asset → Binance static CDN icon URL. */
fun cryptoIconUrl(baseAsset: String): String =
    "https://bin.bnbstatic.com/static/assets/logos/${baseAsset.uppercase()}.png"
