import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import platform.UIKit.UIViewController
import theme.ThemeManager.store
import theme.UTXOTheme
import ui.AppTheme
import ui.CoinDetailScreen
import ui.CryptoList
import ui.CryptoViewModel
import ui.FavoritesListScreen
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
