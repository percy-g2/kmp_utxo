package data.repository

import domain.model.PriceAlert
import getAlertsKStore
import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AlertRepository {
    fun getAlertsFlow(): Flow<List<PriceAlert>>

    suspend fun getAlerts(): List<PriceAlert>

    suspend fun addAlert(alert: PriceAlert)

    suspend fun updateAlert(alert: PriceAlert)

    suspend fun deleteAlert(alertId: String)

    suspend fun markTriggered(
        alertId: String,
        triggeredAt: Long,
    )

    suspend fun setEnabled(
        alertId: String,
        enabled: Boolean,
    )
}

/**
 * Persists alerts under KStore key file `price_alerts.json` (platform actual).
 * Mutex serializes writes. Future: migrate to DataStore if this becomes a bottleneck.
 */
class AlertRepositoryImpl(
    private val store: KStore<List<PriceAlert>> = getAlertsKStore(),
) : AlertRepository {
    private val mutex = Mutex()

    override fun getAlertsFlow(): Flow<List<PriceAlert>> = store.updates.map { it ?: emptyList() }

    override suspend fun getAlerts(): List<PriceAlert> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.get() ?: emptyList()
            }
        }

    override suspend fun addAlert(alert: PriceAlert) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current ->
                    (current ?: emptyList()) + alert
                }
            }
        }
    }

    override suspend fun updateAlert(alert: PriceAlert) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current ->
                    val list = current ?: emptyList()
                    list.map { if (it.id == alert.id) alert else it }
                }
            }
        }
    }

    override suspend fun deleteAlert(alertId: String) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current ->
                    (current ?: emptyList()).filterNot { it.id == alertId }
                }
            }
        }
    }

    override suspend fun markTriggered(
        alertId: String,
        triggeredAt: Long,
    ) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current ->
                    (current ?: emptyList()).map { a ->
                        if (a.id == alertId) a.copy(lastTriggeredAt = triggeredAt) else a
                    }
                }
            }
        }
    }

    override suspend fun setEnabled(
        alertId: String,
        enabled: Boolean,
    ) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current ->
                    (current ?: emptyList()).map { a ->
                        if (a.id == alertId) a.copy(isEnabled = enabled) else a
                    }
                }
            }
        }
    }
}
