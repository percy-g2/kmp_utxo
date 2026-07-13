@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import buildinfo.APP_VERSION_NAME
import kotlinx.browser.localStorage
import kotlinx.browser.window

actual class DeviceInfo {

    actual fun getDeviceType(): String = "web"

    actual fun getDeviceName(): String = window.navigator.userAgent

    actual fun getDeviceModel(): String = jsNavigatorPlatform()

    actual fun getOsVersion(): String = jsNavigatorPlatform()

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

private fun jsTimezone(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")

private fun jsRandomUuid(): String =
    js("(self.crypto && self.crypto.randomUUID) ? self.crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2))")
