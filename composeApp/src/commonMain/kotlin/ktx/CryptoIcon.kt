package ktx

/**
 * Per-platform flag: should we route coin icon requests through a browser-safe
 * proxy?
 *
 * On wasmJs the Binance static CDN (`bin.bnbstatic.com`) returns HTTP 503 to
 * browser-origin requests — its WAF keys on headers like `Origin` and
 * `Sec-Fetch-Site: cross-site` that native Ktor HTTP engines do not send.
 * The web build therefore proxies via `images.weserv.nl`, a long-running
 * CORS-friendly image proxy that re-serves the same bytes with
 * `Access-Control-Allow-Origin: *`.
 *
 * Native targets (Android/iOS/desktop) use a native HTTP stack that sails
 * through Binance's WAF unchanged, so they hit the CDN directly for the
 * lowest latency and the broadest Binance-native icon coverage.
 */
internal expect val useBrowserSafeIconCdn: Boolean

/**
 * Build the icon URL for a given base asset (e.g. `"BTC"`, `"币安人生"`).
 *
 * Pure-ASCII symbols are uppercased to match Binance's file-naming convention
 * (`BTC.png`, `ETH.png`). CJK / mixed symbols are kept verbatim since Binance
 * stores them that way (`币安人生.png`). The resulting filename is percent-
 * encoded so non-ASCII characters survive URL transport intact.
 *
 * Returns an empty string only for empty input — callers can treat that as a
 * signal to render a text fallback instead of making any request.
 */
fun cryptoIconUrl(baseAsset: String): String {
    if (baseAsset.isEmpty()) return ""
    val filename = if (isAsciiAlphaNum(baseAsset)) baseAsset.uppercase() else baseAsset
    val encoded = percentEncode(filename)
    val binancePath = "bin.bnbstatic.com/static/assets/logos/$encoded.png"
    return if (useBrowserSafeIconCdn) {
        "https://images.weserv.nl/?url=$binancePath"
    } else {
        "https://$binancePath"
    }
}

private fun isAsciiAlphaNum(s: String): Boolean =
    s.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

/** RFC 3986 percent-encoding for a path segment: unreserved chars pass through, everything else becomes %XX. */
private fun percentEncode(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val isUnreserved =
            (b in 'A'.code..'Z'.code) ||
            (b in 'a'.code..'z'.code) ||
            (b in '0'.code..'9'.code) ||
            b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code
        if (isUnreserved) {
            append(b.toChar())
        } else {
            append('%')
            append(HEX[b ushr 4])
            append(HEX[b and 0x0F])
        }
    }
}

private val HEX = "0123456789ABCDEF".toCharArray()
