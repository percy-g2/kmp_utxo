
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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
import kotlinx.cinterop.ExperimentalForeignApi
import logging.AppLogger
import ui.Settings

actual fun openLink(link: String) {
    runCatching {
        val nsUrl = NSURL.URLWithString(link)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(
                nsUrl,
                mapOf<Any?, Any>(),
                completionHandler = { success ->
                    AppLogger.logger.d { "Open URL success: $success" }
                }
            )
        }
    }.getOrElse {
        AppLogger.logger.e(throwable = it) { "Failed to open URL: $link" }
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
        return requireNotNull(webSocketClientInstance) {
            "WebSocket client instance should not be null after initialization"
        }
    }
}

actual fun createNewsHttpClient(): HttpClient {
    return HttpClient(Darwin) {
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
    // No CORS restrictions on iOS, return URL as-is
    return url
}

actual fun getPendingCoinDetailFromIntent(): Pair<String, String>? {
    // Read from UserDefaults (set by Swift URL handler)
    val userDefaults = platform.Foundation.NSUserDefaults.standardUserDefaults
    val symbol = userDefaults.stringForKey("pendingCoinSymbol") as? String
    val displaySymbol = userDefaults.stringForKey("pendingCoinDisplaySymbol") as? String
    
    if (symbol != null && displaySymbol != null && symbol.isNotEmpty()) {
        // Clear after reading
        userDefaults.removeObjectForKey("pendingCoinSymbol")
        userDefaults.removeObjectForKey("pendingCoinDisplaySymbol")
        userDefaults.synchronize()
        return Pair(symbol, displaySymbol)
    }
    
    return null
}

actual fun syncSettingsToWidget(settings: ui.Settings) {
    // Sync settings to App Group for widget access using UserDefaults
    val appGroupId = "group.org.androdevlinux.utxo"
    val sharedDefaults = platform.Foundation.NSUserDefaults(suiteName = appGroupId)
    
    if (sharedDefaults != null) {
        try {
            // Filter out empty strings from favorites
            val validFavorites = settings.favPairs.filter { it.isNotEmpty() && it.isNotBlank() }
            
            // Sync favorites as JSON string
            val favPairsJson = kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true 
            }.encodeToString(
                ListSerializer(String.serializer()), 
                validFavorites
            )
            
            AppLogger.logger.d { "Syncing to App Group: favPairs=${validFavorites}, JSON=$favPairsJson" }
            
            // Store in UserDefaults
            // Note: In Kotlin/Native, NSUserDefaults uses setObject for Any? type
            sharedDefaults.setObject(favPairsJson, forKey = "favPairs")
            sharedDefaults.setObject(settings.selectedTradingPair, forKey = "selectedTradingPair")
            sharedDefaults.setObject(settings.appTheme.name, forKey = "appTheme")
            sharedDefaults.synchronize()
            
            // Verify it was written
            val verifyJson = sharedDefaults.stringForKey("favPairs") as? String
            AppLogger.logger.d { "Verification - Read back from UserDefaults: $verifyJson" }
            
            AppLogger.logger.d { "Successfully synced settings to App Group UserDefaults: favPairs=${validFavorites}" }
            
            // Reload widget timeline immediately when favorites change
            reloadWidgetTimeline()
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Failed to sync settings to widget: ${e.message}" }
        }
    } else {
        AppLogger.logger.w { "App Group UserDefaults not available: $appGroupId - Make sure App Groups capability is configured" }
    }
}

private fun reloadWidgetTimeline() {
    try {
        // Use UserDefaults flag that Swift can observe instead of NSNotification
        // This is simpler and avoids NSNotificationName API issues
        val userDefaults = platform.Foundation.NSUserDefaults.standardUserDefaults
        val currentTime = platform.Foundation.NSDate()
        userDefaults.setObject(currentTime, forKey = "WidgetReloadRequested")
        userDefaults.synchronize()
        AppLogger.logger.d { "Set widget reload flag in UserDefaults" }
    } catch (e: Exception) {
        AppLogger.logger.w(throwable = e) { "Failed to reload widget timeline: ${e.message}" }
    }
}