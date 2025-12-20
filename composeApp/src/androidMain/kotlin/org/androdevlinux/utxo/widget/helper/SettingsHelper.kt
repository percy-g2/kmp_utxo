package org.androdevlinux.utxo.widget.helper

import android.content.Context
import kotlinx.serialization.json.Json
import ui.Settings
import java.io.File

object SettingsHelper {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun readSettings(context: Context): Settings? {
        return try {
            val settingsFile = File(context.cacheDir, "settings.json")
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                json.decodeFromString<Settings>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

