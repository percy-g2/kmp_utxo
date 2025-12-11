
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.navigation.toRoute
import io.github.xxfast.kstore.KStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import model.CoinDetail
import model.Favorites
import model.Market
import model.NavItem
import model.Settings
import theme.ThemeManager.store
import theme.UTXOTheme
import ui.AppTheme
import ui.CoinDetailScreen
import ui.CryptoList
import ui.CryptoViewModel
import ui.FavoritesListScreen
import ui.SettingsScreen

@Composable
fun App(
    cryptoViewModel: CryptoViewModel = viewModel { CryptoViewModel() }
) {
    val navController: NavHostController = rememberNavController()
    val settingsState by store.updates.collectAsState(initial = ui.Settings(appTheme = AppTheme.System))
    val isDarkTheme = (settingsState?.appTheme == AppTheme.Dark || (settingsState?.appTheme == AppTheme.System && isSystemInDarkTheme()))
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val networkObserver = remember { NetworkConnectivityObserver() }
    val networkStatus by networkObserver.observe().collectAsState(initial = null)

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        when (navBackStackEntry?.destination?.id) {
            Market.serializer().generateHashCode() -> {
                selectedItem = 0
                // Resume WebSocket and data updates
                cryptoViewModel.resume()
            }
            Favorites.serializer().generateHashCode() -> {
                selectedItem = 1
                // Resume WebSocket and data updates
                cryptoViewModel.resume()
            }
            Settings.serializer().generateHashCode() -> {
                selectedItem = 2
                // Pause WebSocket when navigating to Settings to save memory
                cryptoViewModel.pause()
            }
            CoinDetail.serializer().generateHashCode() -> {
                selectedItem = 3
            }
        }
    }

    if (networkStatus != NetworkStatus.Available) {
        NetworkDialog()
    }

    UTXOTheme(isDarkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (selectedItem != 3) {
                    BottomAppBar(
                        actions = {
                            val navItems = listOf(
                                NavItem.HomeScreen,
                                NavItem.FavoritesScreen,
                                NavItem.SettingsScreen
                            )

                            NavigationBar {
                                navItems.forEachIndexed { index, item ->
                                    NavigationBarItem(
                                        alwaysShowLabel = false,
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title
                                            )
                                        },
                                        label = { Text(item.title) },
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Market,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                animatedComposable<Market> { backStackEntry ->
                    // Track previous destination to detect when returning from CoinDetail
                    var previousDestinationId by rememberSaveable { mutableStateOf<Int?>(null) }
                    var refreshTrigger by remember { mutableStateOf(0) }
                    
                    LaunchedEffect(backStackEntry.id) {
                        val currentDestinationId = navBackStackEntry?.destination?.id
                        // If we were on CoinDetail and now we're on Market, trigger refresh
                        if (previousDestinationId != null && 
                            currentDestinationId == Market.serializer().generateHashCode() &&
                            previousDestinationId != currentDestinationId) {
                            refreshTrigger++
                        }
                        previousDestinationId = currentDestinationId
                    }
                    
                    CryptoList(
                        cryptoViewModel = cryptoViewModel,
                        onCoinClick = { symbol, displaySymbol ->
                            navController.navigate(
                                CoinDetail(
                                    symbol = symbol,
                                    displaySymbol = displaySymbol
                                )
                            )
                        },
                        onReturnFromDetail = if (refreshTrigger > 0) refreshTrigger else null
                    )
                }
                animatedComposable<Favorites> {
                    FavoritesListScreen(
                        cryptoViewModel = cryptoViewModel,
                        onCoinClick = { symbol, displaySymbol ->
                            navController.navigate(
                                CoinDetail(
                                    symbol = symbol,
                                    displaySymbol = displaySymbol
                                )
                            )
                        }
                    )
                }
                animatedComposable<Settings> {
                    SettingsScreen {
                        navController.popBackStack()
                    }
                }
                composable<CoinDetail> { backStackEntry ->
                    val coinDetail = backStackEntry.toRoute<CoinDetail>()
                    CoinDetailScreen(
                        symbol = coinDetail.symbol,
                        displaySymbol = coinDetail.displaySymbol,
                        cryptoViewModel = cryptoViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
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

expect fun getKStore(): KStore<ui.Settings>

expect fun openLink(link: String)

expect fun createNewsHttpClient(): HttpClient

expect class NetworkConnectivityObserver() {
    fun observe(): Flow<NetworkStatus?>
}

enum class NetworkStatus {
    Available, Unavailable, Losing, Lost
}