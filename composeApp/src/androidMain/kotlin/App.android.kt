import android.content.Context

lateinit var appContext: Context

fun initialize(context: Context) {
    appContext = context.applicationContext
}

actual fun getCacheDirectoryPath(): String? {
    return appContext.cacheDir?.absolutePath
}