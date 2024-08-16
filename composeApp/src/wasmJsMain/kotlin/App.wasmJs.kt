import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import model.CryptoPair
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>(
        key = "settings",
        default = Settings(
            selectedTheme = 0,
            favPairs = listOf(CryptoPair.BTCUSDT.symbol, CryptoPair.ETHUSDT.symbol, CryptoPair.SOLUSDT.symbol)
        )
    )
}

actual fun getWebSocketClient(): HttpClient {
    return HttpClient(Js) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
}