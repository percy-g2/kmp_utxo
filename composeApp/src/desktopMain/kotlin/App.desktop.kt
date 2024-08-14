import net.harawata.appdirs.AppDirsFactory
import java.io.File


actual fun getCacheDirectoryPath(): String? {
    val directory = AppDirsFactory.getInstance()
        .getUserDataDir("org.androdevlinux.utxo", "1.0.0", "percy-g2")
    if (File(directory).exists().not()) {
        File(directory).mkdirs()
    }
    return directory
}