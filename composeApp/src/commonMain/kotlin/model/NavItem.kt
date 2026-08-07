package model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
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

    object PortfolioScreen : Item<Portfolio>(
        path = Portfolio,
        title = "Portfolio",
        icon = Icons.Default.AccountBalanceWallet
    )
}

@Serializable
object Market

@Serializable
object Settings

@Serializable
object Favorites

@Serializable
object Portfolio

@Serializable
data class CoinDetail(val symbol: String, val displaySymbol: String)

/**
 * The per-coin AI chat.
 *
 * [initialQuestion] carries the suggestion chip a user tapped on the coin screen, so the chat opens
 * already answering rather than asking them to type what they just picked.
 */
@Serializable
data class CoinChat(
    val symbol: String,
    val displaySymbol: String,
    val initialQuestion: String? = null
)
