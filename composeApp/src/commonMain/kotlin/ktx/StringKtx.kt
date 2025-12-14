@file:OptIn(ExperimentalTime::class)

package ktx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logging.AppLogger
import model.TradingPair
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

fun String.toCryptoSymbol(): String = when(this.uppercase()) {
    "BTC" -> "₿"
    "USDT" -> "₮"
    "USDC" -> "¢"
    "ETH" -> "Ξ"
    "DOGE" -> "Ð"
    "FDUSD" -> "F"
    "DAI" -> "◈"
    "SOL" -> "◎"
    "USD1" -> "$1"
    else -> this
}

fun String.formatVolume(): String {
    return try {
        val value = this.toDouble()
        when {
            value >= 1_000_000_000 -> {
                val billions = value / 1_000_000_000
                when {
                    billions >= 100 -> "${billions.roundToInt()}B"
                    billions >= 10 -> "${(billions * 10).roundToInt() / 10.0}B"
                    else -> "${(billions * 100).roundToInt() / 100.0}B"
                }
            }

            value >= 1_000_000 -> {
                val millions = value / 1_000_000
                when {
                    millions >= 100 -> "${millions.roundToInt()}M"
                    millions >= 10 -> "${(millions * 10).roundToInt() / 10.0}M"
                    else -> "${(millions * 100).roundToInt() / 100.0}M"
                }
            }

            value >= 1_000 -> {
                value.roundToInt().toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(",")
                    .reversed()
            }

            value >= 1 -> {
                "${(value * 100).roundToInt() / 100.0}"
            }

            else -> {
                "${(value * 10000).roundToInt() / 10000.0}"
            }
        }
    } catch (e: Exception) {
        AppLogger.logger.e(throwable = e) { "Error formatting volume: $this" }
        this
    }
}

fun String.formatPrice(symbol: String, tradingPairs: List<TradingPair>): String = runCatching {
    val selectedPair = tradingPairs.find { pair ->
        symbol.endsWith(pair.quote, ignoreCase = true)
    }?.quote.orEmpty()

    val updatedPrice = if (selectedPair == "USDT" || selectedPair == "USDC" || selectedPair == "FDUSD" || selectedPair == "USD1") {
        this.toDouble().formatAsCurrency()
    } else this

    "$updatedPrice ${selectedPair.toCryptoSymbol()}"
}.getOrElse {
    this
}

fun String.buildStyledSymbol(): AnnotatedString {
    val parts = this.split("/")
    val base = parts.getOrNull(0).orEmpty()
    val quote = parts.getOrNull(1).orEmpty()

    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 18.sp)) {
            append(base)
        }
        withStyle(SpanStyle(fontSize = 14.sp, color = Color.Gray)) {
            append("/$quote")
        }
    }
}

/**
 * Formats an RSS pubDate string (RFC 822 format) to show date and time in device timezone.
 * Example input: "Wed, 01 Jan 2024 12:00:00 +0000"
 * Example output: "Jan 1, 2024 12:00 PM" (in device timezone)
 */
fun String.formatNewsDate(): String {
    return try {
        // Parse RFC 822 date format (common in RSS feeds)
        // Format: "Wed, 01 Jan 2024 12:00:00 +0000" or "Wed, 01 Jan 2024 12:00:00 GMT"
        val dateStr = this.trim()

        if (dateStr.isEmpty()) {
            return ""
        }

        // Parse RFC 822 date string to Instant (UTC)
        val instant = parseRssDate(dateStr)

        // Convert UTC Instant to device's local timezone using kotlinx-datetime
        val systemTimeZone = TimeZone.currentSystemDefault()
        val localDateTime = instant.toLocalDateTime(systemTimeZone)

        // Format: "MMM d, yyyy h:mm a" (e.g., "Jan 1, 2024 12:00 PM")
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        val month = monthNames[localDateTime.monthNumber - 1]
        val day = localDateTime.dayOfMonth
        val year = localDateTime.year
        val hour = localDateTime.hour
        val minute = localDateTime.minute

        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        val minuteStr = if (minute < 10) "0$minute" else "$minute"

        "$month $day, $year $displayHour:$minuteStr $amPm"
    } catch (e: Exception) {
        // If parsing fails, try to extract date and time manually from common formats
        AppLogger.logger.w(throwable = e) { "Error parsing RSS date: $this" }
        try {
            // Try to extract time from common RSS formats as fallback
            extractDateAndTimeFallback(this)
        } catch (e2: Exception) {
            // Last resort: return first 16 chars (date only) or original string
            if (this.length > 16) this.take(16) else this
        }
    }
}

/**
 * Fallback method to extract date and time from common RSS date formats
 */
private fun extractDateAndTimeFallback(dateStr: String): String {
    // Try to find time pattern like "12:00:00" or "12:00" anywhere in the string
    val timePattern = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    val timeMatches = timePattern.findAll(dateStr).toList()

    // Use the first time pattern found (usually the main time)
    if (timeMatches.isNotEmpty()) {
        val timeMatch = timeMatches[0]
        val hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].toInt()

        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        val minuteStr = if (minute < 10) "0$minute" else "$minute"
        val timePart = "$displayHour:$minuteStr $amPm"

        // Try to extract date part - look for date patterns
        // Common formats: "Wed, 01 Jan 2024" or "2024-01-01" or "Jan 1, 2024"
        val datePatterns = listOf(
            Regex("""(\w{3}),\s+(\d{1,2})\s+(\w{3})\s+(\d{4})"""), // "Wed, 01 Jan 2024"
            Regex("""(\d{4})-(\d{2})-(\d{2})"""), // "2024-01-01"
            Regex("""(\w{3})\s+(\d{1,2}),?\s+(\d{4})""") // "Jan 1, 2024" or "Jan 1 2024"
        )

        for (pattern in datePatterns) {
            val dateMatch = pattern.find(dateStr)
            if (dateMatch != null) {
                // Found a date pattern, format it nicely
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val monthMap = mapOf(
                    "Jan" to "Jan", "Feb" to "Feb", "Mar" to "Mar", "Apr" to "Apr",
                    "May" to "May", "Jun" to "Jun", "Jul" to "Jul", "Aug" to "Aug",
                    "Sep" to "Sep", "Oct" to "Oct", "Nov" to "Nov", "Dec" to "Dec"
                )

                when {
                    // Format: "Wed, 01 Jan 2024"
                    dateMatch.groupValues.size == 5 -> {
                        val day = dateMatch.groupValues[2].toInt()
                        val month = dateMatch.groupValues[3]
                        val year = dateMatch.groupValues[4].toInt()
                        return "$month $day, $year $timePart"
                    }
                    // Format: "2024-01-01"
                    dateMatch.groupValues.size == 4 && dateMatch.groupValues[1].length == 4 -> {
                        val year = dateMatch.groupValues[1].toInt()
                        val monthNum = dateMatch.groupValues[2].toInt()
                        val day = dateMatch.groupValues[3].toInt()
                        val month = monthNames.getOrNull(monthNum - 1) ?: "Unknown"
                        return "$month $day, $year $timePart"
                    }
                    // Format: "Jan 1, 2024" or "Jan 1 2024"
                    dateMatch.groupValues.size == 4 -> {
                        val month = dateMatch.groupValues[1]
                        val day = dateMatch.groupValues[2].toInt()
                        val year = dateMatch.groupValues[3].toInt()
                        return "$month $day, $year $timePart"
                    }
                }
            }
        }

        // If no date pattern found, use first part of string + time
        val datePart = dateStr.take(16).trim()
        return "$datePart $timePart"
    }

    throw IllegalArgumentException("No time pattern found")
}

/**
 * Parses an RSS date string (RFC 822 format) to an Instant.
 * Handles various formats commonly found in RSS feeds.
 */
internal fun parseRssDate(dateStr: String): Instant {
    // Common RSS date formats:
    // "Wed, 01 Jan 2024 12:00:00 +0000"
    // "Wed, 01 Jan 2024 12:00:00 GMT"
    // "2024-01-01T12:00:00Z"

    try {
        // Try ISO 8601 format first (if present)
        // ISO 8601 format: "2024-01-01T12:00:00Z" or "2024-01-01T12:00:00+00:00"
        // Check if it starts with a 4-digit year followed by "-" and contains "T" after the date part
        val isIso8601 = dateStr.length >= 10 && 
            dateStr[0].isDigit() && 
            dateStr[1].isDigit() && 
            dateStr[2].isDigit() && 
            dateStr[3].isDigit() &&
            dateStr[4] == '-' &&
            dateStr.contains("T")
        
        if (isIso8601) {
            return Instant.parse(dateStr)
        }

        // Parse RFC 822 format: "Thu, 11 Dec 2025 17:15:00 +0000" or "Wed, 01 Jan 2024 12:00:00 GMT"
        val rfc822Pattern = Regex("""(\w{3}),\s+(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+([+-]\d{4}|GMT|UTC)""", RegexOption.IGNORE_CASE)
        val match = rfc822Pattern.find(dateStr)

        if (match != null) {
            val dayOfMonth = match.groupValues[2].toInt()
            val monthStr = match.groupValues[3]
            val year = match.groupValues[4].toInt()
            val hour = match.groupValues[5].toInt()
            val minute = match.groupValues[6].toInt()
            val second = match.groupValues.getOrNull(7)?.toIntOrNull() ?: 0
            val timezoneStr = match.groupValues[8]

            val monthMap = mapOf(
                "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
                "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
                "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
            )

            val month = monthMap[monthStr] ?: throw IllegalArgumentException("Invalid month: $monthStr")

            // Parse timezone offset
            val (offsetSign, offsetHours, offsetMins) = when {
                timezoneStr == "GMT" || timezoneStr == "UTC" -> Triple("+", 0, 0)
                timezoneStr.startsWith("+") || timezoneStr.startsWith("-") -> {
                    val sign = if (timezoneStr.startsWith("+")) "+" else "-"
                    val hours = timezoneStr.substring(1, 3).toInt()
                    val mins = timezoneStr.substring(3, 5).toInt()
                    Triple(sign, hours, mins)
                }
                else -> Triple("+", 0, 0)
            }

            // Create ISO 8601 string with proper timezone offset format
            val yearStr = year.toString().padStart(4, '0')
            val monthNumStr = month.toString().padStart(2, '0')
            val dayStr = dayOfMonth.toString().padStart(2, '0')
            val hourStr = hour.toString().padStart(2, '0')
            val minuteStr = minute.toString().padStart(2, '0')
            val secondStr = second.toString().padStart(2, '0')
            val offsetHoursStr = offsetHours.toString().padStart(2, '0')
            val offsetMinsStr = offsetMins.toString().padStart(2, '0')

            val isoString = "${yearStr}-${monthNumStr}-${dayStr}T${hourStr}:${minuteStr}:${secondStr}${offsetSign}${offsetHoursStr}:${offsetMinsStr}"

            return Instant.parse(isoString)
        }

        // Try to parse as ISO 8601 directly
        return Instant.parse(dateStr)
    } catch (e: Exception) {
        // If all parsing fails, throw the exception
        throw IllegalArgumentException("Unable to parse date: $dateStr", e)
    }
}

