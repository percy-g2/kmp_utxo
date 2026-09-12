package theme

import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF2856C5)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFDFE7FF)
val onPrimaryContainerLight = Color(0xFF102C70)
val secondaryLight = Color(0xFF526078)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFE5EAF5)
val onSecondaryContainerLight = Color(0xFF25324A)
val tertiaryLight = Color(0xFF272B2A)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFF484C4B)
val onTertiaryContainerLight = Color(0xFFE5E8E6)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFF6F8FC)
val onBackgroundLight = Color(0xFF172033)
val surfaceLight = Color(0xFFF6F8FC)
val onSurfaceLight = Color(0xFF172033)
val surfaceVariantLight = Color(0xFFE2E7F0)
val onSurfaceVariantLight = Color(0xFF536078)
val outlineLight = Color(0xFF758198)
val outlineVariantLight = Color(0xFFD6DEEB)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF31302F)
val inverseOnSurfaceLight = Color(0xFFF4F0EE)
val inversePrimaryLight = Color(0xFFAEC6FF)
val surfaceDimLight = Color(0xFFD6DEEB)
val surfaceBrightLight = Color(0xFFF6F8FC)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFFFFFFF)
val surfaceContainerLight = Color(0xFFEDF1F8)
val surfaceContainerHighLight = Color(0xFFE5EAF3)
val surfaceContainerHighestLight = Color(0xFFDDE4EF)

val primaryDark = Color(0xFFAEC6FF)
val onPrimaryDark = Color(0xFF102C70)
val primaryContainerDark = Color(0xFF223D70)
val onPrimaryContainerDark = Color(0xFFDFE7FF)
val secondaryDark = Color(0xFFBDCAE2)
val onSecondaryDark = Color(0xFF31302E)
val secondaryContainerDark = Color(0xFF263449)
val onSecondaryContainerDark = Color(0xFFDCE6FA)
val tertiaryDark = Color(0xFFC4C7C5)
val onTertiaryDark = Color(0xFF2D3130)
val tertiaryContainerDark = Color(0xFF303433)
val onTertiaryContainerDark = Color(0xFFC0C3C1)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF0B101A)
val onBackgroundDark = Color(0xFFE5EBF7)
val surfaceDark = Color(0xFF0B101A)
val onSurfaceDark = Color(0xFFE5EBF7)
val surfaceVariantDark = Color(0xFF303D52)
val onSurfaceVariantDark = Color(0xFFB5C1D7)
val outlineDark = Color(0xFF8492AA)
val outlineVariantDark = Color(0xFF2D3A50)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE5E2E0)
val inverseOnSurfaceDark = Color(0xFF31302F)
val inversePrimaryDark = Color(0xFF2856C5)
val surfaceDimDark = Color(0xFF0B101A)
val surfaceBrightDark = Color(0xFF303D52)
val surfaceContainerLowestDark = Color(0xFF080D15)
val surfaceContainerLowDark = Color(0xFF121B2A)
val surfaceContainerDark = Color(0xFF192335)
val surfaceContainerHighDark = Color(0xFF222E42)
val surfaceContainerHighestDark = Color(0xFF2C3950)

val yellowDark = Color(0xFFFFEB3B)
val yellowLight = Color(0xFFF57F17) // Darker yellow for light mode

/** Semantic gain/loss colors for price changes, resolved per theme. */
object PriceChangeColors {
    /** Positive change. Brighter green on dark surfaces; standard green on light. */
    fun gain(isDarkTheme: Boolean): Color = if (isDarkTheme) gainOnDark else gainOnLight

    /** Negative change. Bright red on dark surfaces; darker AA-compliant red on light. */
    fun loss(isDarkTheme: Boolean): Color = if (isDarkTheme) lossOnDark else lossOnLight

    private val gainOnDark = Color(0xFF66BB6A)  // brighter green, kept legible on dark surfaces
    private val gainOnLight = Color(0xFF237A45)
    private val lossOnDark = Color(0xFFF44336)   // bright red, legible on dark surfaces
    private val lossOnLight = Color(0xFFD32F2F)  // Material Red 700 — meets WCAG AA on light surfaces
}