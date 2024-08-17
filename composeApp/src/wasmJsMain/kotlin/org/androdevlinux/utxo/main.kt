package org.androdevlinux.utxo

import App
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLLinkElement

fun main() {
    addHeadLinks()
    initializeComposeUI()
}

/**
 * Adds necessary link elements (favicon, apple-touch-icon, manifest) to the document head.
 */
fun addHeadLinks() {
    val head = document.head ?: return

    // Define the link elements to be added
    val links = listOf(
        createLinkElement("icon", "favicon.ico"),
        createLinkElement("apple-touch-icon", "apple-touch-icon.png"),
        createLinkElement("manifest", "manifest.json")
    )

    // Remove existing elements and add new ones
    links.forEach { linkElement ->
        val existing = head.querySelector("link[rel='${linkElement.rel}']")
        existing?.let { head.removeChild(it) }
        head.appendChild(linkElement)
    }
}

/**
 * Creates an HTMLLinkElement with the specified rel and href attributes.
 */
fun createLinkElement(rel: String, href: String): HTMLLinkElement {
    return (document.createElement("link") as HTMLLinkElement).apply {
        this.rel = rel
        this.href = href
    }
}

/**
 * Initializes the Compose UI in the browser's body element.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun initializeComposeUI() {
    document.body?.let {
        ComposeViewport(it) {
            App()
        }
    }
}