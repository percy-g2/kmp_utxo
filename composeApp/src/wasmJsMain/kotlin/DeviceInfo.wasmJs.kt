@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import buildinfo.APP_VERSION_NAME
import kotlinx.browser.localStorage
import kotlinx.browser.window

actual class DeviceInfo {

    actual fun getDeviceType(): String = "web"

    actual fun getDeviceName(): String = window.navigator.userAgent

    actual fun getDeviceModel(): String = jsNavigatorPlatform()

    actual fun getOsVersion(): String = jsOsName()

    actual fun getDeviceId(): String {
        val existing = localStorage.getItem("device_id")
        if (!existing.isNullOrEmpty()) return existing
        val id = jsRandomUuid()
        localStorage.setItem("device_id", id)
        return id
    }

    actual fun getAppVersion(): String = APP_VERSION_NAME

    actual fun getTimezone(): String = jsTimezone()
}

private fun jsNavigatorPlatform(): String = js("navigator.platform")

// Derive an OS name+version from the user agent so the OS row is distinct from the device row.
// Uses no backslashes (character classes only) to stay a clean single-expression js() literal.
private fun jsOsName(): String =
    js("(function(){var u=navigator.userAgent||'';var m;if(m=u.match(/Windows NT ([0-9.]+)/))return 'Windows '+m[1];if(m=u.match(/Mac OS X ([0-9_]+)/))return 'macOS '+m[1].replace(/_/g,'.');if(m=u.match(/Android ([0-9.]+)/))return 'Android '+m[1];if(m=u.match(/OS ([0-9_]+) like Mac/))return 'iOS '+m[1].replace(/_/g,'.');if(/Linux/.test(u))return 'Linux';return navigator.platform||'Web';})()")

private fun jsTimezone(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")

private fun jsRandomUuid(): String =
    js("(self.crypto && self.crypto.randomUUID) ? self.crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2))")
