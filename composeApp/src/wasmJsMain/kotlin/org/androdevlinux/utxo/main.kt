package org.androdevlinux.utxo

import App
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLLinkElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val head = document.head ?: return
    val link = document.createElement("link") as HTMLLinkElement
    link.rel = "icon"
    link.href = "https://raw.githubusercontent.com/percy-g2/kmp_utxo/main/composeApp/src/linuxMain/resources/AppIcon.png"

    // Remove existing icon if present
    val existingIcon = head.querySelector("link[rel='icon']")
    existingIcon?.let {
        head.removeChild(it)
    }

    head.appendChild(link)
    document.body?.let {
        ComposeViewport(it) {
            App()
        }
    }
}