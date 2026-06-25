import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController
import theme.ThemeManager.store
import theme.UTXOTheme
import theme.backgroundDark
import theme.backgroundLight
import ui.AppTheme
import ui.CoinDetailScreen
import ui.CryptoList
import ui.CryptoViewModel
import ui.FavoritesListScreen
import ui.PortfolioScreen
import ui.PortfolioViewModel
import ui.SettingsScreen
import ui.utils.isDarkTheme

/**
 * Shared, process-wide singletons for the iOS 26 native-TabView path, where each tab
 * is hosted by its own ComposeUIViewController. Sharing one CryptoViewModel keeps a
 * single WebSocket connection across all hosts, and one network StateFlow keeps a
 * single connectivity monitor.
 */
object IosShared {
    val cryptoViewModel: CryptoViewModel by lazy { CryptoViewModel() }

    /** Shared so the native TabView can drive pause/resume on tab switches (battery/data). */
    val portfolioViewModel: PortfolioViewModel by lazy { PortfolioViewModel() }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val networkStatus: StateFlow<NetworkStatus?> by lazy {
        NetworkConnectivityObserver().observe().stateIn(scope, SharingStarted.Eagerly, null)
    }
}

/** Per-screen wrapper providing the theme + network dialog that App() normally provides once. */
@Composable
private fun IosScreen(content: @Composable () -> Unit) {
    val settings by store.updates.collectAsState(initial = ui.Settings(appTheme = AppTheme.System))
    val status by IosShared.networkStatus.collectAsState()
    UTXOTheme(isDarkTheme(settings)) {
        content()
        if (status != NetworkStatus.Available) NetworkDialog()
    }
}

/** Full-Compose UI used as the fallback on iOS < 26 (keeps its own Compose bottom bar). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

fun MarketViewController(onCoin: (String, String) -> Unit): UIViewController =
    ComposeUIViewController { IosScreen { CryptoList(IosShared.cryptoViewModel, onCoin) } }

fun FavoritesViewController(onCoin: (String, String) -> Unit): UIViewController =
    ComposeUIViewController { IosScreen { FavoritesListScreen(IosShared.cryptoViewModel, onCoin) } }

fun SettingsViewController(): UIViewController =
    ComposeUIViewController { IosScreen { SettingsScreen(onBackPress = {}) } }

/** Portfolio screen for the iOS 26 native-TabView path. [onConfigure] should select Settings. */
fun PortfolioViewController(onConfigure: () -> Unit): UIViewController =
    ComposeUIViewController {
        IosScreen { PortfolioScreen(onConfigureClick = onConfigure, viewModel = IosShared.portfolioViewModel) }
    }

fun CoinDetailViewController(
    symbol: String,
    displaySymbol: String,
    onBack: () -> Unit
): UIViewController =
    ComposeUIViewController {
        IosScreen { CoinDetailScreen(symbol, displaySymbol, onBack, IosShared.cryptoViewModel) }
    }

fun cryptoResume() = IosShared.cryptoViewModel.resume()

fun cryptoPause() = IosShared.cryptoViewModel.pause()

fun portfolioResume() = IosShared.portfolioViewModel.resume()

fun portfolioPause() = IosShared.portfolioViewModel.pause()

/**
 * Observe the in-app theme so SwiftUI native chrome (the iOS 26 TabView / status bar) can match
 * it via `.preferredColorScheme` — without this it follows the device appearance, not the app's
 * theme toggle. Emits "System" | "Light" | "Dark" (System means "follow the device"). Returns a
 * cancel function for the caller to invoke on teardown.
 */
/**
 * The Compose theme background color (ARGB) so SwiftUI can paint the window / status-bar strip /
 * tab-bar surround the same shade as the app content (the app uses #141313, not the iOS default
 * pure black). [dark] selects the dark vs light theme background.
 */
fun themeBackgroundArgb(dark: Boolean): Int = (if (dark) backgroundDark else backgroundLight).toArgb()

fun observeAppTheme(onChange: (String) -> Unit): () -> Unit {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch {
        store.updates
            .map { (it?.appTheme ?: AppTheme.System).name }
            .collect { onChange(it) }
    }
    return { scope.cancel() }
}
