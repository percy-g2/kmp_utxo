package model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
sealed class NavItem {
    open class Item<T>(val path: T, val title: String, val icon: ImageVector) : NavItem()

    object HomeScreen : Item<Market>(
        path = Market,
        title = "Market",
        icon = Icons.Default.CandlestickChart
    )

    object SettingsScreen : Item<Settings>(
        path = Settings,
        title = "Settings",
        icon = Icons.Default.Settings
    )

    object FavoritesScreen : Item<Favorites>(
        path = Favorites,
        title = "Favorites",
        icon = Icons.Default.Star
    )
}

@Serializable
object Market

@Serializable
object Settings

@Serializable
object Favorites

@Serializable
data class CoinDetail(val symbol: String, val displaySymbol: String)
