import io.github.xxfast.kstore.KStore
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import ui.Settings
import io.github.xxfast.kstore.file.storeOf
import okio.Path.Companion.toPath

actual fun getKStore(): KStore<Settings> {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return storeOf<Settings>(file = "${paths.firstOrNull() as? String}/settings.json".toPath())
}