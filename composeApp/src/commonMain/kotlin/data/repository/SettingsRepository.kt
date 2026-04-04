package data.repository

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import theme.ThemeManager
import ui.Settings

interface SettingsRepository {
    val settingsFlow: Flow<Settings?>

    suspend fun update(transform: (Settings?) -> Settings?)
}

/**
 * KStore-backed settings. All writes go through [Dispatchers.IO] and a mutex to reduce races.
 * Future: consider AndroidX DataStore if multi-process or migration is needed.
 */
class SettingsRepositoryImpl(
    private val store: KStore<Settings> = ThemeManager.store,
) : SettingsRepository {
    private val mutex = Mutex()

    override val settingsFlow: Flow<Settings?> get() = store.updates

    override suspend fun update(transform: (Settings?) -> Settings?) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                store.update { current -> transform(current) }
            }
        }
    }
}
