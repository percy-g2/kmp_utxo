
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.io.files.Path
import net.harawata.appdirs.AppDirsFactory
import ui.Settings
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.awt.Desktop
import java.net.URI

actual class NetworkConnectivityObserver {
    actual fun observe(): Flow<NetworkStatus?> = callbackFlow {
        var previousStatus = checkNetworkStatus()
        trySend(previousStatus)

        while (isActive) {
            delay(1) // Check every 5 seconds
            val currentStatus = checkNetworkStatus()
            if (currentStatus != previousStatus) {
                trySend(currentStatus)
                previousStatus = currentStatus
            }
        }

        awaitClose()
    }

    private fun checkNetworkStatus(): NetworkStatus {
        return if (isNetworkAvailable()) NetworkStatus.Available else NetworkStatus.Unavailable
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val socket = Socket()
            val socketAddress = InetSocketAddress("8.8.8.8", 53)
            socket.connect(socketAddress, 3000) // 3 seconds timeout
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}


actual fun getKStore(): KStore<Settings> {
    val directory = AppDirsFactory.getInstance()
        .getUserDataDir("org.androdevlinux.utxo", "1.0.0", "percy-g2")
    if (File(directory).exists().not()) {
        File(directory).mkdirs()
    }
    return storeOf<Settings>(
        file = Path("${directory}/settings.json"),
        default = Settings(
            selectedTheme = 0,
            favPairs = listOf("BTCUSDT")
        )
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

actual fun openLink(link: String) {
    Desktop.getDesktop().browse(URI(link));
}