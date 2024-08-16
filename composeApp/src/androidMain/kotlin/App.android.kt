
import android.content.Context
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import okio.Path.Companion.toPath
import ui.Settings

lateinit var appContext: Context

fun initialize(context: Context) {
    appContext = context.applicationContext
}

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>(file = "${appContext.cacheDir?.absolutePath}/settings.json".toPath())
}

actual fun getWebSocketClient(): HttpClient {
    return HttpClient(CIO) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
}