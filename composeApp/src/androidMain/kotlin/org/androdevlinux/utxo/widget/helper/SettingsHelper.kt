package org.androdevlinux.utxo.widget.helper

import android.content.Context
import kotlinx.serialization.json.Json
import org.androdevlinux.utxo.storage.SettingsFile
import ui.Settings

object SettingsHelper {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun readSettings(context: Context): Settings? {
        return try {
            // Prefer the durable location, but keep reading the legacy cacheDir copy until the app
            // process has had a chance to migrate it — a widget update can fire after an app update
            // but before the next launch. Once the durable file exists this fallback never hits.
            val settingsFile = SettingsFile.current(context).takeIf { it.exists() }
                ?: SettingsFile.legacy(context).takeIf { it.exists() }
                ?: return null
            json.decodeFromString<Settings>(settingsFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

