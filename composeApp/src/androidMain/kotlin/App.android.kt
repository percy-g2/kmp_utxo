
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.toUri
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import org.androdevlinux.utxo.ContextProvider
import org.androdevlinux.utxo.widget.FavoritesWidgetProvider
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    val context = ContextProvider.getContext()
    return storeOf<Settings>(
        file = Path("${context.cacheDir?.absolutePath}/settings.json"),
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
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
    return HttpClient(CIO) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
}

actual fun wrapRssUrlForPlatform(url: String): String {
    // No CORS restrictions on Android, return URL as-is
    return url
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

actual fun syncSettingsToWidget(settings: Settings) {
    // Refresh widget immediately when favorites change
    val context = ContextProvider.getContext()
    FavoritesWidgetProvider.updateAllWidgets(context)
}