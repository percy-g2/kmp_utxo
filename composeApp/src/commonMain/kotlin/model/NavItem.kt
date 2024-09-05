package model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
sealed class NavItem {
    open class Item<T>(val path: T, val title: String, val icon: ImageVector) : NavItem()

    object HomeScreen : Item<Home>(
        path = Home,
        title = "Home",
        icon = Icons.Default.Home
    )

    object SettingsScreen : Item<Settings>(
        path = Settings,
        title = "Settings",
        icon = Icons.Default.Settings
    )
}

@Serializable
object Home

@Serializable
object Settings