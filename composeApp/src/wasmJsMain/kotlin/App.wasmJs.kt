import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>("settings")
}