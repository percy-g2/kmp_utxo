
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xxfast.kstore.KStore
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow
import model.NavItem
import theme.DarkColorScheme
import theme.LightColorScheme
import theme.ThemeManager
import theme.UTXOTheme
import ui.CryptoList
import ui.Settings
import ui.SettingsScreen
import ui.Theme

@Composable
fun App() {
    val navController: NavHostController = rememberNavController()
    val themeState by ThemeManager.themeState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val networkObserver = remember { NetworkConnectivityObserver() }
    val networkStatus by networkObserver.observe().collectAsState(initial = NetworkStatus.Unavailable)

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        when (navBackStackEntry?.destination?.route) {
            NavItem.Home.path -> {
                selectedItem = 0
            }
            NavItem.Settings.path -> {
                selectedItem = 1
            }
        }
    }

    if (networkStatus != NetworkStatus.Available) {
        NetworkDialog()
    }

    LaunchedEffect(Unit) {
        ThemeManager.store.get()?.let { settings ->
            ThemeManager.themeState.value = settings.selectedTheme
        }
    }

    val colorScheme = when (themeState) {
        Theme.SYSTEM.id -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
        Theme.LIGHT.id -> LightColorScheme
        Theme.DARK.id -> DarkColorScheme
        else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    UTXOTheme(colorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomAppBar(
                    actions = {
                        val navItems = listOf(NavItem.Home, NavItem.Settings)

                        NavigationBar(
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    alwaysShowLabel = true,
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title
                                        )
                                    },
                                    label = { if (selectedItem == index) Text(item.title) },
                                    selected = selectedItem == index,
                                    onClick = {
                                        selectedItem = index
                                        navController.navigate(item.path)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = NavItem.Home.path,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(route = NavItem.Home.path) {
                    CryptoList()
                }
                composable(route = NavItem.Settings.path) {
                    SettingsScreen {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkDialog() {
    AlertDialog(
        onDismissRequest = {
            // no op
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        ),
        title = {
            Text(text = "Network Unavailable")
        },
        text = {
            Text(text = "It seems you are not connected to the internet. Please check your connection and try again.")
        },
        confirmButton = {
            // no op
        }
    )
}

expect fun getWebSocketClient(): HttpClient

expect fun getKStore(): KStore<Settings>

class CommonFlow<T>(private val flow: Flow<T>) : Flow<T> by flow

fun <T> Flow<T>.asCommonFlow(): CommonFlow<T> = CommonFlow(this)

expect class NetworkConnectivityObserver() {
    fun observe(): CommonFlow<NetworkStatus>
}

enum class NetworkStatus {
    Available, Unavailable, Losing, Lost
}