
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.darwin.DISPATCH_QUEUE_SERIAL_WITH_AUTORELEASE_POOL
import platform.darwin.dispatch_queue_create
import ui.Settings

actual fun openLink(link: String) {
    runCatching {
        val nsUrl = NSURL.URLWithString(link)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(
                nsUrl,
                mapOf<Any?, Any>(),
                completionHandler = { success ->
                    println("Open URL success: $success")
                }
            )
        }
    }.getOrElse {
        it.printStackTrace()
    }
}


actual class NetworkConnectivityObserver {
    actual fun observe(): Flow<NetworkStatus?> = callbackFlow {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_queue_create(
            "org.androdevlinux.utxo.connectivity.monitor",
            DISPATCH_QUEUE_SERIAL_WITH_AUTORELEASE_POOL
        )

        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            when {
                status == nw_path_status_satisfied -> {
                    trySend(NetworkStatus.Available)
                }
                else -> trySend(NetworkStatus.Unavailable)
            }
        }

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)

        awaitClose {
            nw_path_monitor_cancel(monitor)
        }
    }
}

actual fun getKStore(): KStore<Settings> {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return storeOf<Settings>(
        file = Path("${paths.firstOrNull() as? String}/settings.json"),
        default = Settings()
    )
}

// Singleton WebSocket client to prevent memory leaks from multiple instances
private var webSocketClientInstance: HttpClient? = null
private val webSocketClientLock = SynchronizedObject()

actual fun getWebSocketClient(): HttpClient {
    synchronized(webSocketClientLock) {
        if (webSocketClientInstance == null) {
            webSocketClientInstance = HttpClient(Darwin) {
                install(WebSockets)
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.NONE
                }
            }
        }
        return webSocketClientInstance!!
    }
}