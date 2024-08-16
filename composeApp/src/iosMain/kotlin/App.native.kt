
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return storeOf<Settings>(file = "${paths.firstOrNull() as? String}/settings.json".toPath())
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