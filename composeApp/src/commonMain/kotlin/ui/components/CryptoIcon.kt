package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logging.AppLogger
import network.CryptoIconService
import org.jetbrains.compose.resources.vectorResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.app_icon_fallback

/**
 * Composable that displays a cryptocurrency icon.
 * 
 * @param symbol The full trading pair symbol (e.g., "BTCUSDT")
 * @param tradingPair The trading pair quote (e.g., "USDT")
 * @param modifier Modifier for styling
 * @param size Size of the icon in dp (default: 40.dp)
 */
@Composable
fun CryptoIcon(
    symbol: String,
    tradingPair: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalPlatformContext.current
    // Create ImageLoader - Coil3 will handle caching
    val imageLoader = remember(context) { 
        AppLogger.logger.d { "Creating ImageLoader for CryptoIcon" }
        createImageLoader(context) 
    }
    
    val baseSymbol = remember(symbol, tradingPair) {
        CryptoIconService.extractBaseSymbol(symbol, tradingPair)
    }
    
    // Map size to CoinGecko size parameter
    val coinGeckoSize = remember(size) {
        when {
            size.value <= 32 -> "thumb"
            size.value <= 64 -> "small"
            else -> "large"
        }
    }
    
    // State for icon URL (fetched asynchronously)
    var iconUrl by remember(symbol, tradingPair) { mutableStateOf<String?>(null) }
    var isLoadingUrl by remember(symbol, tradingPair) { mutableStateOf(true) }
    
    // Fetch icon URL from CoinGecko API
    LaunchedEffect(symbol, tradingPair, coinGeckoSize) {
        isLoadingUrl = true
        try {
            val url = withContext(Dispatchers.Default) {
                CryptoIconService.getIconUrl(symbol, tradingPair, size = coinGeckoSize)
            }
            iconUrl = url
            AppLogger.logger.d { "CryptoIcon: symbol=$symbol, tradingPair=$tradingPair, baseSymbol=$baseSymbol, url=$url" }
        } catch (e: Exception) {
            AppLogger.logger.e(throwable = e) { "Failed to fetch icon URL for $baseSymbol" }
            iconUrl = null
        } finally {
            isLoadingUrl = false
        }
    }
    
    // Check if we have a valid URL
    val hasValidUrl = iconUrl != null && iconUrl!!.isNotEmpty()
    
    // Fallback: Show first letter of base symbol
    val fallbackText = remember(baseSymbol) {
        baseSymbol.take(1).uppercase().takeIf { it.isNotEmpty() } ?: "?"
    }
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingUrl) {
            // Show loading indicator while fetching URL
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.5f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (!hasValidUrl) {
            // No valid URL, show app icon fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.app_icon_fallback),
                    contentDescription = "App icon fallback",
                    modifier = Modifier.size(size * 0.6f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(iconUrl!!)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Icon for $baseSymbol",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            ) {
            val state = painter.state
            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    // Show loading indicator
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(size * 0.5f),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    // Log error for debugging
                    val errorMessage = state.result.throwable?.message ?: "Unknown error"
                    AppLogger.logger.w(throwable = state.result.throwable) { 
                        "Failed to load icon for $baseSymbol from $iconUrl: $errorMessage" 
                    }
                    // Show app icon as fallback
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.app_icon_fallback),
                            contentDescription = "App icon fallback",
                            modifier = Modifier.size(size * 0.6f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
        }
    }
}

