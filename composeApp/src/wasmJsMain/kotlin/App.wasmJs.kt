
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
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

actual fun createNewsHttpClient(): HttpClient {
    return HttpClient(Js) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.NONE
        }
        install(ContentNegotiation) {
            json()
        }
    }
}

actual fun wrapRssUrlForPlatform(url: String): String {
    // Use CORS proxy for WASM/web platform to bypass CORS restrictions
    // Using allorigins.win as a reliable CORS proxy service
    val encodedUrl = encodeURIComponent(url)
    return "https://api.allorigins.win/raw?url=$encodedUrl"
}

@JsName("encodeURIComponent")
private external fun encodeURIComponent(str: String): String