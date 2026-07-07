package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ktx.cryptoIconUrl

/**
 * Circular coin logo loaded from Binance's static CDN (via [cryptoIconUrl], which is
 * wasmJs-safe through the weserv proxy). Shows a spinner while loading and renders
 * [fallback] on error or for an empty symbol.
 *
 * Size-agnostic: all sizing comes from [modifier] (the image fills it), so the same
 * component serves the 28dp market rows and the 40dp portfolio rows.
 *
 * [baseAsset] must be already normalized by the caller (e.g. quote suffix or alt-dex
 * namespace stripped) — this composable does no symbol munging beyond the URL build.
 */
@Composable
fun CoinIcon(
    baseAsset: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = { CoinLetterFallback(baseAsset) },
) {
    val iconUrl = remember(baseAsset) { cryptoIconUrl(baseAsset) }
    var loading by remember(baseAsset) { mutableStateOf(true) }
    var errored by remember(baseAsset) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (errored || iconUrl.isEmpty()) {
            fallback()
        } else {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loading = false },
                onError = {
                    loading = false
                    errored = true
                },
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Default fallback: the symbol's first letter, matching the market/favorites lists. */
@Composable
private fun CoinLetterFallback(baseAsset: String) {
    Text(
        text = baseAsset.take(1),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
