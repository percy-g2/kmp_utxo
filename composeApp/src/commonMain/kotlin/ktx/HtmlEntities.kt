package ktx

/**
 * Decodes HTML/XML character entities in [this] string, in pure common code
 * (no `java.*` or other platform dependency).
 *
 * The RSS feeds UTXO pulls (BeInCrypto, U.Today, CoinTelegraph, …) encode
 * punctuation as numeric character references directly in `<title>` — e.g.
 * `Cowen&#8217;s` (’), `Bitmine &#8211; now` (–). Because [network.NewsService]
 * extracts tag content with a regex rather than a real XML parser, nothing
 * decodes those references and they render literally. The old cleanup only
 * handled a handful of named refs (`&amp; &lt; &#39; …`), which is why some
 * titles decoded and others (`&#8217;`) did not.
 *
 * Handles:
 *  - decimal numeric refs — `&#8217;`
 *  - hex numeric refs — `&#x2019;` / `&#X2019;` (including code points above the
 *    BMP, emitted as a surrogate pair)
 *  - named refs — `&amp; &lt; &gt; &quot; &apos; &rsquo; &mdash; …`
 *
 * A single left-to-right pass decodes each reference exactly once, so a bare
 * `&` that is not part of a reference ("Tom & Jerry", "R&D; team") is preserved.
 * Unknown named refs and malformed / oversized sequences are left untouched.
 */
fun String.decodeHtmlEntities(): String {
    if (indexOf('&') < 0) return this // fast path: nothing to decode

    val sb = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '&') {
            val semi = indexOfSemicolon(i + 1)
            if (semi > i) {
                val decoded = decodeEntity(substring(i + 1, semi))
                if (decoded != null) {
                    sb.append(decoded)
                    i = semi + 1
                    continue
                }
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

/** Longest reference body we consider (e.g. `#x10FFFF` is 8 chars). */
private const val MAX_ENTITY_BODY = 12

/**
 * Index of the terminating `;` for a reference that starts just before [from],
 * or -1 if there isn't a plausible one. Bails on `&`/whitespace (never part of a
 * reference) so a stray `&` can't swallow following text, and caps the scan so a
 * lone `&` doesn't run to a far-away `;`.
 */
private fun String.indexOfSemicolon(from: Int): Int {
    val limit = minOf(length, from + MAX_ENTITY_BODY)
    var i = from
    while (i < limit) {
        when (this[i]) {
            ';' -> return i
            '&', ' ', '\n', '\t', '\r', '<', '>' -> return -1
        }
        i++
    }
    return -1
}

private fun decodeEntity(body: String): String? {
    if (body.isEmpty()) return null
    return if (body[0] == '#') {
        val rest = body.substring(1)
        val code = if (rest.isNotEmpty() && (rest[0] == 'x' || rest[0] == 'X')) {
            rest.substring(1).toIntOrNull(16)
        } else {
            rest.toIntOrNull(10)
        }
        code?.let { codePointToString(it) }
    } else {
        NAMED_ENTITIES[body]
    }
}

/** Converts a Unicode code point to a String, emitting a surrogate pair above the BMP. */
private fun codePointToString(cp: Int): String? = when {
    cp <= 0 || cp > 0x10FFFF -> null
    cp in 0xD800..0xDFFF -> null // lone surrogate: invalid
    cp <= 0xFFFF -> cp.toChar().toString()
    else -> {
        val v = cp - 0x10000
        val high = (0xD800 + (v shr 10)).toChar()
        val low = (0xDC00 + (v and 0x3FF)).toChar()
        "$high$low"
    }
}

/**
 * Common named references. Numeric refs cover everything else generically, so
 * this only needs the names that actually appear in feeds (punctuation, symbols,
 * currency, and accented letters common in author names). Case-sensitive, per
 * the HTML spec.
 */
private val NAMED_ENTITIES: Map<String, String> = mapOf(
    // XML predefined
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    // spaces & dashes
    "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "shy" to "­", "ndash" to "–", "mdash" to "—", "hellip" to "…",
    // quotes
    "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
    "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
    "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›",
    "prime" to "′", "Prime" to "″",
    // punctuation & symbols
    "middot" to "·", "bull" to "•", "dagger" to "†", "Dagger" to "‡",
    "permil" to "‰", "sect" to "§", "para" to "¶", "deg" to "°",
    "copy" to "©", "reg" to "®", "trade" to "™",
    // currency
    "cent" to "¢", "pound" to "£", "curren" to "¤", "yen" to "¥",
    "euro" to "€",
    // math / fractions
    "plusmn" to "±", "times" to "×", "divide" to "÷", "minus" to "−",
    "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
    "sup1" to "¹", "sup2" to "²", "sup3" to "³",
    "ne" to "≠", "le" to "≤", "ge" to "≥", "infin" to "∞",
    // common accented letters (feeds sometimes use named refs in author names)
    "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã",
    "auml" to "ä", "aring" to "å", "aelig" to "æ", "ccedil" to "ç",
    "egrave" to "è", "eacute" to "é", "ecirc" to "ê", "euml" to "ë",
    "igrave" to "ì", "iacute" to "í", "icirc" to "î", "iuml" to "ï",
    "ntilde" to "ñ", "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô",
    "otilde" to "õ", "ouml" to "ö", "oslash" to "ø", "ugrave" to "ù",
    "uacute" to "ú", "ucirc" to "û", "uuml" to "ü", "yacute" to "ý",
    "yuml" to "ÿ", "szlig" to "ß",
    "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â", "Auml" to "Ä",
    "Aring" to "Å", "AElig" to "Æ", "Ccedil" to "Ç", "Egrave" to "È",
    "Eacute" to "É", "Ecirc" to "Ê", "Euml" to "Ë", "Ntilde" to "Ñ",
    "Oacute" to "Ó", "Ouml" to "Ö", "Oslash" to "Ø", "Uuml" to "Ü",
)
