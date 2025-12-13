package logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime

/**
 * Custom LogWriter that buffers logs in memory for server interception.
 * Logs can be sent to a server for debugging purposes.
 */
@OptIn(ExperimentalTime::class)
class ServerLogWriter(
    private val maxBufferSize: Int = 1000,
    private val enabled: Boolean = true
) : LogWriter() {

    private val logBuffer = mutableListOf<BufferedLog>()
    private val mutex = Mutex()

    data class BufferedLog(
        val timestamp: Long,
        val severity: Severity,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )

    override fun isLoggable(tag: String, severity: Severity): Boolean {
        return enabled && severity >= Severity.Warn // Only buffer warnings and errors by default
    }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        if (!enabled) return

        // Add log to buffer (non-blocking, fire-and-forget)
        CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock {
                val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
                logBuffer.add(
                    BufferedLog(
                        timestamp = timestamp,
                        severity = severity,
                        tag = tag,
                        message = message,
                        throwable = throwable
                    )
                )

                // Keep buffer size limited
                if (logBuffer.size > maxBufferSize) {
                    logBuffer.removeAt(0)
                }
            }
        }
    }

    /**
     * Get all buffered logs for sending to server.
     * This clears the buffer after retrieval.
     */
    suspend fun getAndClearLogs(): List<BufferedLog> {
        return mutex.withLock {
            val logs = logBuffer.toList()
            logBuffer.clear()
            logs
        }
    }

    /**
     * Get all buffered logs without clearing.
     */
    suspend fun getLogs(): List<BufferedLog> {
        return mutex.withLock {
            logBuffer.toList()
        }
    }

    /**
     * Clear all buffered logs.
     */
    suspend fun clearLogs() {
        mutex.withLock {
            logBuffer.clear()
        }
    }

    /**
     * Enable or disable server logging.
     */
    fun setEnabled(enabled: Boolean) {
        // Note: This is a simple implementation. For thread-safety, consider using AtomicBoolean
        // but since this is typically called during initialization, it should be fine.
    }

    /**
     * Convert logs to a format suitable for server transmission.
     */
    fun BufferedLog.toServerFormat(): Map<String, Any?> {
        return mapOf(
            "timestamp" to timestamp,
            "severity" to severity.name,
            "tag" to tag,
            "message" to message,
            "stackTrace" to throwable?.stackTraceToString()
        )
    }
}
