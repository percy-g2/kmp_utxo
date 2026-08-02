package ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logging.AppLogger
import theme.ThemeManager

/**
 * App-wide, hot view of the persisted [Settings] plus the canonical write path for the values the
 * Settings screen edits.
 *
 * Two problems this exists to solve:
 *
 * 1. **Reads.** `KStore.updates` is `cache.onStart { read(fromCache = false) }` — a *cold* Flow, so
 *    every collector pays its own disk read and every screen renders at least one frame against the
 *    `collectAsState(initial = …)` placeholder. Screens could not tell "not read yet" from "read,
 *    and these are the values", so Coin Detail loaded its ticker and news once against placeholder
 *    settings and again when the real ones landed. [settings] is hot and starts at `null`, which
 *    means **"not read from disk yet"** and never "loaded, with defaults" — callers must not act
 *    on `null`.
 *
 * 2. **Writes.** Settings previously wrote through `rememberCoroutineScope()`, which is cancelled
 *    when the screen leaves composition — toggle dark mode and immediately navigate back, and the
 *    in-flight `store.update` could be killed before it reached disk. Writes here run on an
 *    app-lifetime scope instead.
 */
object SettingsStore {
    private val store get() = ThemeManager.store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The persisted settings. `null` until the first disk read completes — see the class doc; treat
     * it as "unknown", not as [Settings] defaults.
     */
    val settings: StateFlow<Settings?> = store.updates
        // KStore's FileCodec.decode() only catches FileNotFoundException, so a corrupt or truncated
        // settings.json throws out of `updates`' onStart. Without this the flow would die and stay
        // null forever, and screens that gate on "settings loaded" (Coin Detail) would never load
        // anything at all. Fall back to defaults in memory — the file on disk is left untouched so
        // it can still be recovered — and log loudly.
        .catch { e ->
            AppLogger.logger.e(throwable = e) { "SettingsStore: could not read settings; using defaults" }
            emit(Settings())
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Persist the optional llm7.io token. Written once, when the token dialog is confirmed. */
    fun setAiApiToken(token: String) = write { it.copy(aiApiToken = token.trim()) }

    fun setTheme(theme: AppTheme) = write { it.copy(appTheme = theme) }

    fun setEnabledRssProviders(providers: Set<String>) = write { it.copy(enabledRssProviders = providers) }

    private fun write(transform: (Settings) -> Settings) {
        scope.launch { persist(transform) }
    }

    private suspend fun persist(transform: (Settings) -> Settings) {
        try {
            // Read-modify-write under KStore's mutex, so this can't race the other store writers.
            store.update { current -> transform(current ?: Settings()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "SettingsStore: failed to persist settings" }
        }
    }
}
