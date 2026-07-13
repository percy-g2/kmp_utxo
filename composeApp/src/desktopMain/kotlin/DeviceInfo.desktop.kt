import buildinfo.APP_VERSION_NAME
import net.harawata.appdirs.AppDirsFactory
import java.io.File
import java.net.InetAddress
import java.util.TimeZone
import java.util.UUID

actual class DeviceInfo {

    actual fun getDeviceType(): String = "desktop"

    actual fun getDeviceName(): String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?: System.getProperty("user.name")
            ?: "Desktop"

    actual fun getDeviceModel(): String =
        "${System.getProperty("os.name")} (${System.getProperty("os.arch")})"

    actual fun getOsVersion(): String =
        "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

    // Stable per-install id persisted alongside settings.json in the app data dir.
    // The "1.0.0" segment is a fixed path anchor (matches getKStore()), NOT the app version.
    actual fun getDeviceId(): String {
        val dir = AppDirsFactory.getInstance()
            .getUserDataDir("org.androdevlinux.utxo", "1.0.0", "percy-g2")
        val idFile = File(dir, "device-id")
        runCatching { idFile.readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val id = UUID.randomUUID().toString()
        runCatching {
            idFile.parentFile?.mkdirs()
            idFile.writeText(id)
        }
        return id
    }

    actual fun getAppVersion(): String = APP_VERSION_NAME

    actual fun getTimezone(): String = TimeZone.getDefault().id
}
