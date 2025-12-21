package ui.components

import coil3.ImageLoader
import coil3.PlatformContext

/**
 * Creates and configures Coil3 ImageLoader for multiplatform use.
 * Coil3 will automatically use the available network component from coil-network-ktor3.
 */
expect fun createImageLoader(context: PlatformContext): ImageLoader

/**
 * Creates a default ImageLoader configuration.
 * The network component is automatically provided by coil-network-ktor3 dependency.
 */
fun createDefaultImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .build()
}

