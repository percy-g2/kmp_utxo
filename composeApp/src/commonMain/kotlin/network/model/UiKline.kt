package network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray

@Serializable
data class UiKline(
    val openTime: Long? = null,
    val openPrice: String? = null,
    val highPrice: String? = null,
    val lowPrice: String? = null,
    val closePrice: String,
    val volume: String? = null,
    val closeTime: Long? = null,
    val quoteAssetVolume: String? = null,
    val numberOfTrades: Int? = null,
    val takerBuyBaseAssetVolume: String? = null,
    val takerBuyQuoteAssetVolume: String? = null
)

object UiKlineSerializer : JsonTransformingSerializer<List<UiKline>>(ListSerializer(UiKline.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return buildJsonArray {
            for (jsonElement in element.jsonArray) {
                val array = jsonElement.jsonArray
                add(
                    JsonObject(
                        mapOf(
                            "openTime" to array[0],
                            "openPrice" to array[1],
                            "highPrice" to array[2],
                            "lowPrice" to array[3],
                            "closePrice" to array[4],
                            "volume" to array[5],
                            "closeTime" to array[6],
                            "quoteAssetVolume" to array[7],
                            "numberOfTrades" to array[8],
                            "takerBuyBaseAssetVolume" to array[9],
                            "takerBuyQuoteAssetVolume" to array[10]
                        )
                    )
                )
            }
        }
    }
}
