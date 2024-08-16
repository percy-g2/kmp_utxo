package org.androdevlinux.utxo

import App
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.body?.let {
        ComposeViewport(it) {
            App()
        }
    }
}