package logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * Application-wide logger singleton.
 * Provides a single instance of Kermit Logger that can be used throughout the app.
 * Includes a ServerLogWriter for intercepting logs and sending to server for debugging.
 */
object AppLogger {
    private val serverLogWriter = ServerLogWriter(
        maxBufferSize = 1000,
        enabled = true // Can be configured based on build type or settings
    )

    /**
     * The main logger instance configured with platform logging and server interception.
     */
    val logger: Logger = Logger

    init {
        // Configure log writers
        val logWriters = mutableListOf<LogWriter>()
        logWriters.add(platformLogWriter()) // Platform-specific logging (Logcat on Android, NSLog on iOS, etc.)
        logWriters.add(serverLogWriter)     // Server interception for debugging
        
        Logger.setLogWriters(logWriters)
        Logger.setMinSeverity(Severity.Verbose) // Log everything by default
    }

    /**
     * Get the server log writer for manual log retrieval/sending.
     */
    fun getServerLogWriter(): ServerLogWriter = serverLogWriter

    /**
     * Convenience extension function for logging exceptions with a tag.
     */
    fun Throwable.logError(tag: String, message: String) {
        logger.e(tag = tag, throwable = this) { message }
    }

    /**
     * Convenience extension function for logging exceptions without a tag.
     */
    fun Throwable.logError(message: String) {
        logger.e(throwable = this) { message }
    }
}
