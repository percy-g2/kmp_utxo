package theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
actual fun platformTypography(): Typography {
    return MaterialTheme.typography
}