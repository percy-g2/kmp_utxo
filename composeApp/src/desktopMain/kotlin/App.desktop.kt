import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import net.harawata.appdirs.AppDirsFactory
import okio.Path.Companion.toPath
import ui.Settings
import java.io.File


actual fun getKStore(): KStore<Settings> {
    val directory = AppDirsFactory.getInstance()
        .getUserDataDir("org.androdevlinux.utxo", "1.0.0", "percy-g2")
    if (File(directory).exists().not()) {
        File(directory).mkdirs()
    }
    return storeOf<Settings>(file = "${directory}/settings.json".toPath())
}