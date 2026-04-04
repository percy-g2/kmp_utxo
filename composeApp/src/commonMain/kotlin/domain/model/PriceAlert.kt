package domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PriceAlert(
    val id: String,
    val symbol: String,
    val displayName: String,
    val condition: AlertCondition,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val lastTriggeredAt: Long? = null,
    val repeatAfterMinutes: Int = 60,
)

@Serializable
sealed class AlertCondition {
    @Serializable
    @SerialName("price_above")
    data class PriceAbove(
        val price: Double,
    ) : AlertCondition()

    @Serializable
    @SerialName("price_below")
    data class PriceBelow(
        val price: Double,
    ) : AlertCondition()

    @Serializable
    @SerialName("percent_change_up")
    data class PercentChangeUp(
        val percent: Double,
    ) : AlertCondition()

    @Serializable
    @SerialName("percent_change_down")
    data class PercentChangeDown(
        val percent: Double,
    ) : AlertCondition()
}
