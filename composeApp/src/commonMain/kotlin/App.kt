
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.serialization.generateHashCode
import io.github.xxfast.kstore.KStore
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow
import model.Home
import model.NavItem
import theme.DarkColorScheme
import theme.LightColorScheme
import theme.ThemeManager
import theme.UTXOTheme
import ui.CryptoList
import ui.CryptoViewModel
import ui.Settings
import ui.SettingsScreen
import ui.Theme

@Composable
fun App(
    cryptoViewModel: CryptoViewModel = viewModel { CryptoViewModel() }
) {
    val navController: NavHostController = rememberNavController()
    val themeState by ThemeManager.themeState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val networkObserver = remember { NetworkConnectivityObserver() }
    val networkStatus by networkObserver.observe().collectAsState(initial = null)

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        when (navBackStackEntry?.destination?.id) {
            Home.serializer().generateHashCode() -> {
                selectedItem = 0
            }
            Settings.serializer().generateHashCode() -> {
                selectedItem = 1
            }
        }
    }

    if (networkStatus == NetworkStatus.Unavailable) {
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
                        val navItems = listOf(NavItem.HomeScreen, NavItem.SettingsScreen)

                        NavigationBar(
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    alwaysShowLabel = false,
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
                                        navController.navigate(item.path) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
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
                startDestination = Home,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                animatedComposable<Home> {
                    CryptoList(cryptoViewModel = cryptoViewModel)
                }
                animatedComposable<model.Settings> {
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
        onDismissRequest = { /*no op*/ },
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
        confirmButton = { /*no op*/ }
    )
}

@OptIn(ExperimentalAnimationApi::class)
inline fun <reified T : Any> NavGraphBuilder.animatedComposable(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable<T>(
        enterTransition = { expandFromCenter() },
        exitTransition = { shrinkToCenter() },
        content = content
    )
}

@ExperimentalAnimationApi
fun expandFromCenter(): EnterTransition {
    return scaleIn(
        animationSpec = tween(300),
        initialScale = 0.8f,
        transformOrigin = TransformOrigin.Center
    ) + fadeIn(animationSpec = tween(300))
}

@ExperimentalAnimationApi
fun shrinkToCenter(): ExitTransition {
    return scaleOut(
        animationSpec = tween(300),
        targetScale = 0.8f,
        transformOrigin = TransformOrigin.Center
    ) + fadeOut(animationSpec = tween(300))
}

expect fun getWebSocketClient(): HttpClient

expect fun getKStore(): KStore<Settings>

expect fun openLink(link: String)

expect class NetworkConnectivityObserver() {
    fun observe(): Flow<NetworkStatus?>
}

enum class NetworkStatus {
    Available, Unavailable, Losing, Lost
}