package org.androdevlinux.utxo

object CoinDetailIntentHandler {
    private var pendingSymbol: String? = null
    private var pendingDisplaySymbol: String? = null
    
    fun setPendingCoinDetail(symbol: String, displaySymbol: String) {
        pendingSymbol = symbol
        pendingDisplaySymbol = displaySymbol
    }
    
    fun getPendingCoinDetail(): Pair<String, String>? {
        val symbol = pendingSymbol
        val displaySymbol = pendingDisplaySymbol
        if (symbol != null && displaySymbol != null) {
            pendingSymbol = null
            pendingDisplaySymbol = null
            return Pair(symbol, displaySymbol)
        }
        return null
    }
}
