package org.androdevlinux.utxo.widget.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import model.UiKline
import model.UiKlineSerializer
import network.JsonConfig
import java.net.HttpURLConnection
import java.net.URL

object ChartHelper {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun fetchChartData(symbol: String): List<UiKline> {
        return runBlocking {
            try {
                val url = URL("https://api.binance.com/api/v3/uiKlines?symbol=$symbol&interval=1s&limit=1000")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    JsonConfig.json.decodeFromString(UiKlineSerializer, response)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun createSparklineBitmap(
        klines: List<UiKline>,
        width: Int,
        height: Int,
        lineColor: Int,
        fillColor: Int,
        isPositive: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        if (klines.isEmpty()) {
            return bitmap
        }

        // Extract close prices
        val prices = klines.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (prices.isEmpty()) {
            return bitmap
        }

        val minPrice = prices.minOrNull() ?: 0.0
        val maxPrice = prices.maxOrNull() ?: 0.0
        val priceRange = maxPrice - minPrice
        if (priceRange == 0.0) {
            return bitmap
        }

        val padding = 2f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        // Create path for line
        val path = Path()
        val fillPath = Path()

        prices.forEachIndexed { index, price ->
            val x = padding + (index.toFloat() / (prices.size - 1).coerceAtLeast(1)) * chartWidth
            val normalizedPrice = ((price - minPrice) / priceRange).toFloat()
            val y = padding + chartHeight - (normalizedPrice * chartHeight)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height.toFloat())
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Close fill path
        fillPath.lineTo(padding + chartWidth, height.toFloat())
        fillPath.close()

        // Draw fill
        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            alpha = 50 // Semi-transparent
        }
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        val linePaint = Paint().apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawPath(path, linePaint)

        return bitmap
    }
}

