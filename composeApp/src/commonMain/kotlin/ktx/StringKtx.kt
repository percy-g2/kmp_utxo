package ktx

fun String.formatPair(): String = if (contains("USDT")) {
    replace("USDT", "/USDT")
} else this