
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.toUri
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import org.androdevlinux.utxo.ContextProvider
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    val context = ContextProvider.getContext()
    return storeOf<Settings>(
        file = Path("${context.cacheDir?.absolutePath}/settings.json"),
        default = Settings()
    )
}

actual fun getWebSocketClient(): HttpClient {
    return HttpClient(CIO) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
    }
}

actual class NetworkConnectivityObserver {
    private val context = ContextProvider.getContext()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    actual fun observe(): Flow<NetworkStatus?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkStatus.Available)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkStatus.Losing)
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.Lost)
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