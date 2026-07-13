import android.os.Build
import android.provider.Settings
import buildinfo.APP_VERSION_NAME
import org.androdevlinux.utxo.ContextProvider
import java.util.Locale
import java.util.TimeZone

actual class DeviceInfo {
    // Resolved lazily so construction never races the AppInitializer that sets the Context.
    private val context get() = ContextProvider.getContext()

    actual fun getDeviceType(): String = "android"

    actual fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }} $model"
        }
    }

    actual fun getDeviceModel(): String = Build.MODEL

    actual fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    actual fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE}"

    actual fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: APP_VERSION_NAME
        } catch (_: Exception) {
            APP_VERSION_NAME
        }
    }

    actual fun getTimezone(): String = TimeZone.getDefault().id
}
