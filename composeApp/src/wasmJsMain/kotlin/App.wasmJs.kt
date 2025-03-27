
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ui.Settings

actual fun openLink(link: String) {
    window.open(link)
}

actual class NetworkConnectivityObserver {
    actual fun observe(): Flow<NetworkStatus?> = callbackFlow {
        val updateStatus = {
            val status = if (window.navigator.onLine) NetworkStatus.Available else NetworkStatus.Unavailable
            trySend(status)
        }

        window.addEventListener("online") { updateStatus() }
        window.addEventListener("offline") { updateStatus() }

        updateStatus()

        awaitClose {
            window.removeEventListener("online") { updateStatus() }
            window.removeEventListener("offline") { updateStatus() }
        }
    }
}

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>(
        key = "settings",
        default = Settings()
    )
}

actual fun getWebSocketClient(): HttpClient {
    return HttpClient(Js) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
    }
}