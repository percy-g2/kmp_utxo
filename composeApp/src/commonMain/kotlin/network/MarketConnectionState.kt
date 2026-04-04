package network

/**
 * Connectivity state for the Binance mini-ticker WebSocket used by the market list.
 */
sealed class MarketConnectionState {
    data object Connecting : MarketConnectionState()

    data object Connected : MarketConnectionState()

    data class Disconnected(
        val reason: String?,
    ) : MarketConnectionState()

    data class Failed(
        val error: Throwable,
    ) : MarketConnectionState()
}
