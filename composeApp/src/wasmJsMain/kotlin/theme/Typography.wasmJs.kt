package theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logging.AppLogger
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

@OptIn(InternalResourceApi::class)
@Composable
fun NotoSansFontFamily(): FontFamily? {
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(Unit) {
        try {
            // Single patched static font covering Latin + CJK + crypto signs.
            //
            // Skiko's FontMatcher picks one Font per (family, weight, style)
            // request and renders all glyphs through it — there is no per-glyph
            // fallback across Font siblings, so anything we render must live in
            // this one typeface. We start from NotoSansSC (30k+ glyphs: basic
            // Latin, Latin-1, digits, punctuation, CJK ideographs, `¢`, `Ξ`,
            // `Ð`, `◎`, etc.) and inject `₿` (U+20BF), `₮` (U+20AE), and `◈`
            // (U+25C8) so tickers like "BTC/USDT" and "DAI" render their
            // currency signs instead of .notdef boxes. The patched font is
            // instanced at weight 400, which saves ~7 MB vs. the original
            // variable font (10 MB vs. 17 MB). Skia synthesises Light/Bold for
            // the couple of UI slots that want non-Normal weights.
            val cjk = readResourceBytes("NotoSansSC.ttf")

            if (cjk.isNotEmpty()) {
                // Register the same bytes at every weight slot so the
                // FontMatcher always finds an exact weight match; Skia renders
                // non-Normal requests via faux-bold / faux-light.
                fontFamily = FontFamily(
                    Font("NotoSansSC_Light", cjk, weight = FontWeight.Light),
                    Font("NotoSansSC_Normal", cjk, weight = FontWeight.Normal),
                    Font("NotoSansSC_Medium", cjk, weight = FontWeight.Medium),
                    Font("NotoSansSC_SemiBold", cjk, weight = FontWeight.SemiBold),
                    Font("NotoSansSC_Bold", cjk, weight = FontWeight.Bold)
                )
            }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Error loading Noto Sans SC font" }
        }
    }

    return fontFamily
}

@Composable
fun WebTypography() = Typography().run {
    val fontFamily = NotoSansFontFamily()
    copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}

@Composable
actual fun platformTypography(): Typography {
    return WebTypography()
}