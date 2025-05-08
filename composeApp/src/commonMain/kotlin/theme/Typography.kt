package theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import utxo.composeapp.generated.resources.NotoSans_Bold
import utxo.composeapp.generated.resources.NotoSans_Light
import utxo.composeapp.generated.resources.NotoSans_Medium
import utxo.composeapp.generated.resources.NotoSans_Regular
import utxo.composeapp.generated.resources.NotoSans_SemiBold
import utxo.composeapp.generated.resources.Res

@Composable
fun NotoSansFontFamily() = FontFamily(
    Font(Res.font.NotoSans_Light, weight = FontWeight.Light),
    Font(Res.font.NotoSans_Regular, weight = FontWeight.Normal),
    Font(Res.font.NotoSans_Medium, weight = FontWeight.Medium),
    Font(Res.font.NotoSans_SemiBold, weight = FontWeight.SemiBold),
    Font(Res.font.NotoSans_Bold, weight = FontWeight.Bold)
)

@Composable
fun AppTypography() = Typography().run {
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
        bodyLarge = bodyLarge.copy(fontFamily =  fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}