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
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

@OptIn(InternalResourceApi::class)
@Composable
fun NotoSansFontFamily(): FontFamily? {
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(Unit) {
        try {
            // Load all fonts in parallel using coroutines
            val (light, normal, medium, semiBold, bold) = coroutineScope {
                listOf(
                    async { readResourceBytes("NotoSans-Light.ttf") },
                    async { readResourceBytes("NotoSans-Regular.ttf") },
                    async { readResourceBytes("NotoSans-Medium.ttf") },
                    async { readResourceBytes("NotoSans-SemiBold.ttf") },
                    async { readResourceBytes("NotoSans-Bold.ttf") }
                ).awaitAll()
            }

            // Create FontFamily only when all fonts are loaded
            if (light.isNotEmpty() && normal.isNotEmpty() &&
                medium.isNotEmpty() && semiBold.isNotEmpty() &&
                bold.isNotEmpty()) {

                fontFamily = FontFamily(
                    Font("NotoSans_Light", light, weight = FontWeight.Light),
                    Font("NotoSans_Normal", normal, weight = FontWeight.Normal),
                    Font("NotoSans_Medium", medium, weight = FontWeight.Medium),
                    Font("NotoSans_SemiBold", semiBold, weight = FontWeight.SemiBold),
                    Font("NotoSans_Bold", bold, weight = FontWeight.Bold)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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