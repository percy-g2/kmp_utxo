
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import logging.AppLogger
import model.CoinDetail
import model.Favorites
import model.Market
import model.NavItem
import model.Portfolio
import model.Settings
import org.jetbrains.compose.resources.stringResource
import theme.ThemeManager.store
import theme.UTXOTheme
import ui.AppTheme
import ui.CoinDetailScreen
import ui.CryptoList
import ui.CryptoViewModel
import ui.FavoritesListScreen
import ui.PortfolioScreen
import ui.SettingsScreen
import ui.hasPortfolioWallets
import theme.ThemeManager
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

    // One-time migration of the legacy single Hyperliquid address into the wallet list.
    // Runs here (not in the Portfolio VM, whose tab is gated on wallet presence).
    LaunchedEffect(Unit) {
        ThemeManager.migrateSettingsIfNeeded()
    }
    
    val navController: NavHostController = rememberNavController()
    val settingsState by store.updates.collectAsState(initial = ui.Settings(appTheme = AppTheme.System))
    val isDarkTheme = isDarkTheme(settingsState)
    
    // Sync settings to widget (iOS App Group)
    LaunchedEffect(settingsState) {
        settingsState?.let { syncSettingsToWidget(it) }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestinationId = navBackStackEntry?.destination?.id
    val networkObserver = remember { NetworkConnectivityObserver() }
    val networkStatus by networkObserver.observe().collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Helper function to get current CoinDetail route if we're on CoinDetail screen
    fun getCurrentCoinDetailRoute(): CoinDetail? {
        val currentEntry = navBackStackEntry ?: return null
        val currentDestination = currentEntry.destination.id
        if (currentDestination == CoinDetail.serializer().generateHashCode()) {
            return try {
                currentEntry.toRoute<CoinDetail>()
            } catch (e: Exception) {
                // Defensive handling: toRoute may throw various exceptions during deserialization
                // Return null to gracefully handle any route parsing errors
                null
            }
        }
        return null
    }
    
    // Helper function to navigate to CoinDetail, replacing if already on CoinDetail
    fun navigateToCoinDetail(symbol: String, displaySymbol: String) {
        val currentCoinDetail = getCurrentCoinDetailRoute()
        val isAlreadyOnCoinDetail = currentCoinDetail != null
        val isDifferentSymbol = currentCoinDetail?.symbol != symbol
        
        if (isAlreadyOnCoinDetail && isDifferentSymbol) {
            // Replace current CoinDetail destination using atomic navigation
            // We already know we're on CoinDetail from getCurrentCoinDetailRoute(), so destination ID exists
            val currentDestinationId = navBackStackEntry?.destination?.id
            navController.navigate(
                CoinDetail(
                    symbol = symbol,
                    displaySymbol = displaySymbol
                )
            ) {
                // Pop the current CoinDetail destination and replace it atomically
                currentDestinationId?.let {
                    popUpTo(it) { inclusive = true }
                } ?: run {
                    // Fallback: pop backstack if destination ID unavailable (shouldn't happen)
                    popUpTo(navController.graph.startDestinationId) { saveState = false }
                }
            }
        } else if (!isAlreadyOnCoinDetail) {
            // Normal navigation when not on CoinDetail screen
            navController.navigate(
                CoinDetail(
                    symbol = symbol,
                    displaySymbol = displaySymbol
                )
            )
        }
        // If already on CoinDetail with same symbol, do nothing
    }
    
    // Handle coin detail intent from widget
    // This handles app launch case and updates when already on CoinDetail
    LaunchedEffect(navBackStackEntry?.destination?.id) {
        // Only proceed if NavHost is ready
        val currentDestination = navBackStackEntry?.destination?.id ?: return@LaunchedEffect
        
        // Check for pending coin detail
        val pendingCoinDetail = getPendingCoinDetailFromIntent()
        if (pendingCoinDetail != null) {
            navigateToCoinDetail(pendingCoinDetail.first, pendingCoinDetail.second)
        } else {
            // If not found immediately, check periodically for up to 2 seconds
            // This covers the case where URL handler sets UserDefaults slightly after URL is opened
            var elapsed = 0L
            val checkInterval = 200L // Check every 200ms
            val maxWaitTime = 2000L // Stop after 2 seconds
            
            while (elapsed < maxWaitTime) {
                delay(checkInterval)
                elapsed += checkInterval
                
                val foundPendingCoinDetail = getPendingCoinDetailFromIntent()
                if (foundPendingCoinDetail != null) {
                    navigateToCoinDetail(foundPendingCoinDetail.first, foundPendingCoinDetail.second)
                    break // Stop checking once we've navigated
                }
            }
        }
    }
    
    // Check for pending coin detail when app resumes (for when widget is clicked while app is already running)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Check if NavHost is ready
                val currentDestination = navBackStackEntry?.destination?.id
                if (currentDestination != null) {
                    val pendingCoinDetail = getPendingCoinDetailFromIntent()
                    if (pendingCoinDetail != null) {
                        navigateToCoinDetail(pendingCoinDetail.first, pendingCoinDetail.second)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pause the market stream on screens that don't need it; resume on the list screens.
    LaunchedEffect(currentDestinationId) {
        when (currentDestinationId) {
            Market.serializer().generateHashCode(),
            Favorites.serializer().generateHashCode() -> cryptoViewModel.resume()
            Portfolio.serializer().generateHashCode(),
            Settings.serializer().generateHashCode(),
            CoinDetail.serializer().generateHashCode() -> cryptoViewModel.pause()
            else -> {}
        }
    }

    // If all tracked wallets are cleared while the user is on the Portfolio tab, the tab
    // disappears — redirect to Market so they aren't stranded.
    LaunchedEffect(currentDestinationId, settingsState?.hyperliquidWallets) {
        val onPortfolio = currentDestinationId == Portfolio.serializer().generateHashCode()
        val enabled = settingsState?.hasPortfolioWallets() == true
        if (onPortfolio && !enabled) {
            navController.navigate(Market) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
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
                val isCoinDetail = currentDestinationId == CoinDetail.serializer().generateHashCode()
                if (!isCoinDetail) {
                    val portfolioEnabled = settingsState?.hasPortfolioWallets() == true
                    val navItems = buildList {
                        add(NavItem.HomeScreen)
                        add(NavItem.FavoritesScreen)
                        if (portfolioEnabled) add(NavItem.PortfolioScreen)
                        add(NavItem.SettingsScreen)
                    }

                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                alwaysShowLabel = false,
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                selected = currentDestinationId == item.destinationId(),
                                onClick = {
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Market,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // The root Scaffold reserves the bottom nav-bar / system-bar inset here. Mark it
                    // consumed so nested per-screen Scaffolds (Portfolio, Settings) don't re-apply the
                    // same bottom inset and open a surface-colored strip above the bottom bar.
                    .consumeWindowInsets(innerPadding)
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
                animatedComposable<Portfolio> {
                    PortfolioScreen(
                        onConfigureClick = {
                            navController.navigate(Settings) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                animatedComposable<Settings> {
                    SettingsScreen(onBackPress = {
                        navController.popBackStack()
                    })
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

/** Maps a bottom-bar [NavItem.Item] to its navigation destination id for route-based selection. */
private fun NavItem.Item<*>.destinationId(): Int = when (this) {
    NavItem.HomeScreen -> Market.serializer().generateHashCode()
    NavItem.FavoritesScreen -> Favorites.serializer().generateHashCode()
    NavItem.PortfolioScreen -> Portfolio.serializer().generateHashCode()
    NavItem.SettingsScreen -> Settings.serializer().generateHashCode()
    else -> -1
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

expect fun wrapRssUrlForPlatform(url: String): List<String>

internal expect val useExchangeInfoForMarginSymbols: Boolean

expect fun getPendingCoinDetailFromIntent(): Pair<String, String>?

expect fun syncSettingsToWidget(settings: ui.Settings)

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class NetworkConnectivityObserver() {
    fun observe(): Flow<NetworkStatus?>
}

enum class NetworkStatus {
    Available, Unavailable
}