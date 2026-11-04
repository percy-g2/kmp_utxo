
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import logging.AppLogger
import network.newsClientJson
import org.androdevlinux.utxo.ContextProvider
import org.androdevlinux.utxo.storage.SettingsFile
import org.androdevlinux.utxo.widget.FavoritesWidgetProvider
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    val context = ContextProvider.getContext()
    // Durable, backup-excluded, and migrated off the old purgeable cacheDir. See SettingsFile.
    return storeOf<Settings>(
        file = Path(SettingsFile.resolveForStore(context).absolutePath),
        default = Settings()
    )
}

actual fun getWebSocketClient(): HttpClient {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
    return HttpClient(CIO) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
}

actual fun createNewsHttpClient(): HttpClient {
    return HttpClient(Android) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        install(ContentNegotiation) {
            json(newsClientJson)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000 // 15 seconds
            socketTimeoutMillis = 30_000 // 30 seconds (RSS feeds can be large)
            requestTimeoutMillis = 45_000 // 45 seconds (allow more time for slow LineageOS connections)
        }
    }
}

actual fun wrapRssUrlForPlatform(url: String): List<String> {
    // No CORS restrictions on Android, return URL as-is
    return listOf(url)
}

actual fun getPendingCoinDetailFromIntent(): Pair<String, String>? {
    return org.androdevlinux.utxo.CoinDetailIntentHandler.getPendingCoinDetail()
}

actual class NetworkConnectivityObserver {
    private val context = ContextProvider.getContext()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    actual fun observe(): Flow<NetworkStatus?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkStatus.Available)
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.Unavailable)
            }

            override fun onUnavailable() {
                trySend(NetworkStatus.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}

actual fun openLink(link: String) {
    val context = ContextProvider.getContext()
    val intent = Intent(Intent.ACTION_VIEW, link.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
actual fun rememberInAppBrowser(): (link: String) -> Unit {
    // Launch from the hosting Activity, not ContextProvider's application context, so the tab
    // stays in the app's task and Back returns to the news list.
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    return remember(context, surface) {
        { link ->
            if (!isInAppBrowsable(link)) {
                openLink(link)
            } else {
                try {
                    CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(
                            CustomTabColorSchemeParams.Builder()
                                .setToolbarColor(surface.toArgb())
                                .build()
                        )
                        .setColorScheme(
                            if (surface.luminance() < 0.5f) {
                                CustomTabsIntent.COLOR_SCHEME_DARK
                            } else {
                                CustomTabsIntent.COLOR_SCHEME_LIGHT
                            }
                        )
                        .setShowTitle(true)
                        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                        .build()
                        .launchUrl(context, link.toUri())
                } catch (e: Exception) {
                    // No Custom Tabs-capable browser (or the Activity went away): hand off externally.
                    AppLogger.logger.w(throwable = e) { "Custom Tab launch failed, opening externally: $link" }
                    openLink(link)
                }
            }
        }
    }
}

actual fun copyToClipboard(text: String) {
    val context = ContextProvider.getContext()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}

actual fun syncSettingsToWidget(settings: Settings) {
    // Refresh widget immediately when favorites change
    val context = ContextProvider.getContext()
    FavoritesWidgetProvider.updateAllWidgets(context)
}