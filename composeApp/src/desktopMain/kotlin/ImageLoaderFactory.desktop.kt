package ui.components

import coil3.ImageLoader
import coil3.PlatformContext

actual fun createImageLoader(context: PlatformContext): ImageLoader {
    return createDefaultImageLoader(context)
}

