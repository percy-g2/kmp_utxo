package ktx

/**
 * No overrides needed on wasmJs — the bundled `NotoSansSC.ttf` is a patched
 * static Noto Sans SC with `₿` (U+20BF), `₮` (U+20AE), and `◈` (U+25C8)
 * injected from Noto Sans / Noto Sans Symbols 2, so every glyph that
 * [toCryptoSymbol] can emit is already in the single font we ship to the web.
 */
internal actual fun platformCryptoSymbolOverride(key: String): String? = null
