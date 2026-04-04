package ui.error

sealed class NetworkError {
    data object NoConnection : NetworkError()

    data object Timeout : NetworkError()

    data class ServerError(
        val code: Int,
    ) : NetworkError()

    data object ParseError : NetworkError()
}
