import buildinfo.APP_VERSION_NAME
import platform.Foundation.NSBundle
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.UIKit.UIDevice

actual class DeviceInfo {

    actual fun getDeviceType(): String = "ios"

    actual fun getDeviceName(): String = UIDevice.currentDevice.name

    actual fun getDeviceModel(): String = UIDevice.currentDevice.model

    actual fun getDeviceId(): String =
        UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""

    actual fun getOsVersion(): String = "iOS ${UIDevice.currentDevice.systemVersion}"

    actual fun getAppVersion(): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: APP_VERSION_NAME

    actual fun getTimezone(): String = NSTimeZone.localTimeZone.name
}
