package org.androdevlinux.utxo.storage

import android.content.Context
import java.io.File

/**
 * Resolves where `settings.json` lives on Android, and migrates it off the cache directory.
 *
 * It used to live in `context.cacheDir`, which the OS evicts under storage pressure and which
 * *Settings → Storage → Clear cache* wipes outright — taking favourites, tracked wallets and the
 * theme with it. iOS already moved off its equivalent (Caches → Application Support);
 * this is the Android counterpart, targeting [Context.getNoBackupFilesDir] so the data is durable
 * without the AI token and wallet addresses entering Google auto-backup / device transfer.
 */
object SettingsFile {
    private const val NAME = "settings.json"

    /** Durable location: survives "Clear cache", excluded from cloud backup. */
    fun current(context: Context): File = File(context.noBackupFilesDir, NAME)

    /** Legacy (purgeable) location, kept readable for one-way migration and the widget fallback. */
    fun legacy(context: Context): File = File(context.cacheDir, NAME)

    /**
     * Returns the file the store should use, migrating the legacy copy across on first run.
     * Idempotent and best-effort: if anything fails the user simply starts from defaults, exactly
     * as they would have if the cache had been evicted.
     *
     * The copy goes to a temp file and is then renamed, so [current] only ever becomes visible
     * complete. That matters because kstore's `FileCodec.decode()` catches only
     * `FileNotFoundException` — a half-written file would throw `SerializationException` out of
     * `KStore.updates` and permanently break the settings flow with no recovery path.
     */
    fun resolveForStore(context: Context): File {
        val target = current(context)
        val legacyFile = legacy(context)
        if (!target.exists() && legacyFile.exists()) {
            runCatching {
                val temp = File(context.noBackupFilesDir, "$NAME.migrating")
                legacyFile.copyTo(temp, overwrite = true)
                // Same-directory rename is atomic; drop the temp file if it didn't take.
                if (!temp.renameTo(target)) temp.delete()
            }
        }
        return target
    }
}
