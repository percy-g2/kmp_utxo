package org.androdevlinux.utxo

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Keeps Coil image requests off Ktor's Android engine.
 *
 * A long market-list scroll disposes several in-flight icon requests at once. The Android engine
 * delegates response cleanup to the platform's legacy `com.android.okhttp` implementation, which
 * can throw `IllegalStateException("Unbalanced enter/exit")` while those requests are cancelled.
 * CIO handles that cancellation path safely and is already used by the app on Android.
 */
@OptIn(ExperimentalCoilApi::class)
fun configureAndroidImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(HttpClient(CIO)))
            }
            .build()
    }
}
