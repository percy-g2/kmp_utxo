package ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
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
 *
 * ### Behaviour when settings.json is unreadable
 *
 * kstore's `FileCodec.decode()` catches only `FileNotFoundException`, so a corrupt or truncated file
 * throws out of `updates`' `onStart`. That is handled in two halves:
 *
 * - **Reads** retry a bounded number of times (a transient IO error is worth another go; a corrupt
 *   file throws identically every time, so retrying forever would just spin), then fall back to
 *   in-memory defaults. The file on disk is left untouched so it can still be recovered by hand.
 * - **Writes** detect the same failure and repair the file via [repairAndWrite], because
 *   `KStore.update` reads before it writes and so could never succeed against it.
 *
 * One caveat worth knowing: `catch` terminates the flow, so after that fallback [settings] stops
 * receiving updates for the rest of the process — writes still reach disk (and repair the file), but
 * the UI won't reflect them until the next launch, which then reads cleanly.
 */
object SettingsStore {
    private val store get() = ThemeManager.store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bounded so an unreadable file — which fails identically every time — can't spin. */
    private const val READ_RETRIES = 2L
    private const val READ_RETRY_DELAY_MS = 500L

    /**
     * The persisted settings. `null` until the first disk read completes — see the class doc; treat
     * it as "unknown", not as [Settings] defaults.
     */
    val settings: StateFlow<Settings?> = store.updates
        .retryWhen { cause, attempt ->
            val willRetry = attempt < READ_RETRIES
            AppLogger.logger.e(throwable = cause) {
                "SettingsStore: settings read failed (attempt ${attempt + 1}), retrying=$willRetry"
            }
            if (willRetry) delay(READ_RETRY_DELAY_MS)
            willRetry
        }
        // Without this the flow would die and [settings] would stay null forever, and screens that
        // gate on "settings loaded" (Coin Detail) would never load anything at all.
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
        } catch (e: SerializationException) {
            repairAndWrite(transform, e)
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "SettingsStore: failed to persist settings" }
        }
    }

    /**
     * Rewrites an unparseable settings.json instead of failing every write for the rest of the
     * install. `KStore.update` reads before it writes, so it can never succeed here; `set` skips the
     * read. Only content that was already unrecoverable is lost, and this runs solely off an explicit
     * user settings change — never on its own.
     */
    private suspend fun repairAndWrite(transform: (Settings) -> Settings, cause: Throwable) {
        AppLogger.logger.e(throwable = cause) {
            "SettingsStore: settings.json is unreadable; rewriting it from the in-memory state"
        }
        try {
            store.set(transform(settings.value ?: Settings()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "SettingsStore: repair write failed" }
        }
    }
}
