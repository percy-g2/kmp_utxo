package ui.error

import org.jetbrains.compose.resources.StringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.error
import utxo.composeapp.generated.resources.network_unavailable
import utxo.composeapp.generated.resources.unknown_error

object UiErrorMapper {
    fun toTitleRes(error: NetworkError): StringResource =
        when (error) {
            is NetworkError.NoConnection -> Res.string.network_unavailable
            else -> Res.string.error
        }

    fun toMessageRes(error: NetworkError): StringResource =
        when (error) {
            is NetworkError.NoConnection -> Res.string.network_unavailable
            is NetworkError.Timeout -> Res.string.unknown_error
            is NetworkError.ServerError -> Res.string.unknown_error
            is NetworkError.ParseError -> Res.string.unknown_error
        }
}
