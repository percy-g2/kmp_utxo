
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.DISPATCH_QUEUE_SERIAL_WITH_AUTORELEASE_POOL
import platform.darwin.dispatch_queue_create
import ui.Settings

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
                    val isWifi = nw_path_uses_interface_type(path, nw_interface_type_wifi)
                    val isExpensive = nw_path_is_expensive(path)
                    val isConstrained = nw_path_is_constrained(path)
                    val isMetered = !isWifi && (isExpensive || isConstrained)

                    trySend(if (isMetered) NetworkStatus.Losing else NetworkStatus.Available)
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
        default = Settings(
            selectedTheme = 0,
            favPairs = listOf("BTCUSDT")
        )
    )
}

actual fun getWebSocketClient(): HttpClient {
    return HttpClient(Darwin) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
}