import android.content.Context
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import okio.Path.Companion.toPath
import ui.Settings

lateinit var appContext: Context

fun initialize(context: Context) {
    appContext = context.applicationContext
}

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>(file = "${appContext.cacheDir?.absolutePath}/settings.json".toPath())
}