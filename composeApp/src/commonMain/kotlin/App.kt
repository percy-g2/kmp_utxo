
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import logging.AppLogger
import model.CoinDetail
import model.Favorites
import model.Market
import model.NavItem
import model.Settings
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import theme.UTXOTheme
import ui.AppTheme
import ui.CoinDetailScreen
import ui.CryptoList
import ui.CryptoViewModel
import ui.FavoritesListScreen
import ui.SettingsScreen
import ui.utils.isDarkTheme
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.network_unavailable
import utxo.composeapp.generated.resources.network_unavailable_message

@Composable
fun App(
    cryptoViewModel: CryptoViewModel = viewModel { CryptoViewModel() }
) {
    // Initialize logger early to ensure it's ready
    LaunchedEffect(Unit) {
        AppLogger.logger.d { "App initialized" }
    }
    
    val navController: NavHostController = rememberNavController()
    val settingsState by store.updates.collectAsState(initial = ui.Settings(appTheme = AppTheme.System))
    val isDarkTheme = isDarkTheme(settingsState)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val networkObserver = remember { NetworkConnectivityObserver() }
    val networkStatus by networkObserver.observe().collectAsState(initial = null)
    
    // Handle coin detail intent from widget
    LaunchedEffect(Unit) {
        // Check for pending coin detail navigation (from widget click)
        val pendingCoinDetail = getPendingCoinDetailFromIntent()
        if (pendingCoinDetail != null) {
            navController.navigate(
                CoinDetail(
                    symbol = pendingCoinDetail.first,
                    displaySymbol = pendingCoinDetail.second
                )
            )
        }
    }

    // Simplified navigation state management
    LaunchedEffect(navBackStackEntry?.destination?.id) {
        val destinationId = navBackStackEntry?.destination?.id ?: return@LaunchedEffect
        
        when (destinationId) {
            Market.serializer().generateHashCode() -> {
                selectedItem = 0
                cryptoViewModel.resume()
            }
            Favorites.serializer().generateHashCode() -> {
                selectedItem = 1
                cryptoViewModel.resume()
            }
            Settings.serializer().generateHashCode() -> {
                selectedItem = 2
                cryptoViewModel.pause()
            }
            CoinDetail.serializer().generateHashCode() -> {
                selectedItem = 3
                cryptoViewModel.pause()
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
                animatedComposable<Market> {
                    CryptoList(
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
            Text(text = stringResource(Res.string.network_unavailable))
        },
        text = {
            Text(text = stringResource(Res.string.network_unavailable_message))
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

expect fun wrapRssUrlForPlatform(url: String): String

expect fun getPendingCoinDetailFromIntent(): Pair<String, String>?

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class NetworkConnectivityObserver() {
    fun observe(): Flow<NetworkStatus?>
}

enum class NetworkStatus {
    Available, Unavailable
}