package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ktx.formatPrice
import ktx.formatVolume
import model.Ticker24hr
import model.TradingPair
import org.jetbrains.compose.resources.stringResource
import ui.utils.getPriceChangeColor
import ui.utils.shimmerEffect
import ui.utils.dailyRangePosition
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.detail_price_in_quote
import utxo.composeapp.generated.resources.detail_daily_range
import utxo.composeapp.generated.resources.label_24h_change
import utxo.composeapp.generated.resources.label_24h_high
import utxo.composeapp.generated.resources.label_24h_low
import utxo.composeapp.generated.resources.label_24h_volume_quote
import utxo.composeapp.generated.resources.label_last_price
import utxo.composeapp.generated.resources.price_data_not_available
import utxo.composeapp.generated.resources.refresh

/** Price stays ahead of the chart; the day range describes the ticker, not the chart interval. */
@Composable
internal fun CoinPriceHero(
    symbol: String,
    ticker: Ticker24hr?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    tradingPairs: List<TradingPair>,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val quote = tradingPairs.filter { symbol.endsWith(it.quote) }.maxByOrNull { it.quote.length }?.quote
        Text(
            if (quote != null) stringResource(Res.string.detail_price_in_quote, quote)
            else stringResource(Res.string.label_last_price),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ticker == null) {
            if (isLoading) {
                Spacer(Modifier.fillMaxWidth(0.65f).height(44.dp).shimmerEffect())
                Spacer(Modifier.fillMaxWidth().height(64.dp).shimmerEffect())
            } else {
                Text(stringResource(Res.string.price_data_not_available))
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.refresh)) }
            }
            return@Column
        }
        Text(
            text = ticker.lastPrice.formatPrice(symbol, tradingPairs).trim().substringBeforeLast(" "),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        val changeColor = getPriceChangeColor(ticker.priceChangePercent, isDarkTheme, MaterialTheme.colorScheme.onSurface)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${if ((ticker.priceChangePercent.toDoubleOrNull() ?: 0.0) > 0) "+" else ""}${ticker.priceChangePercent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = changeColor,
                modifier = Modifier.background(changeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                stringResource(Res.string.label_24h_change),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroMetric(
                        label = stringResource(Res.string.label_24h_low),
                        value = ticker.lowPrice.formatPrice(symbol, tradingPairs).trim().substringBeforeLast(" "),
                        modifier = Modifier.weight(1f),
                    )
                    HeroMetric(
                        label = stringResource(Res.string.label_24h_high),
                        value = ticker.highPrice.formatPrice(symbol, tradingPairs).trim().substringBeforeLast(" "),
                        modifier = Modifier.weight(1f),
                        alignEnd = true,
                    )
                }
                val rangePosition = dailyRangePosition(ticker.lastPrice, ticker.lowPrice, ticker.highPrice)
                val rangeDescription = stringResource(Res.string.detail_daily_range)
                if (rangePosition != null) {
                    LinearProgressIndicator(
                        progress = { rangePosition },
                        modifier = Modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = rangeDescription },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.label_24h_volume_quote),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(ticker.quoteVolume.formatVolume(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier, alignEnd: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
