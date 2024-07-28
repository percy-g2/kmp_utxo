package network.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

open class Item(val path: String, val title: String, val icon: ImageVector)

sealed class NavItem {
    object Home :
        Item(path = NavPath.HOME.toString(), title = NavTitle.HOME, icon = Icons.Default.Home)

    object Settings :
        Item(path = NavPath.SETTINGS.toString(), title = NavTitle.SETTINGS, icon = Icons.Default.Settings)
}

enum class NavPath {
    HOME, SETTINGS
}

object NavTitle {
    const val HOME = "Home"
    const val SETTINGS = "Settings"
}