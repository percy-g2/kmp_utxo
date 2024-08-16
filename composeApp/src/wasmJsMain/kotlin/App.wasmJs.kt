import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import model.CryptoPair
import ui.Settings

actual fun getKStore(): KStore<Settings> {
    return storeOf<Settings>(
        key = "settings",
        default = Settings(
            selectedTheme = 0,
            favPairs = listOf(CryptoPair.BTCUSDT.symbol, CryptoPair.ETHUSDT.symbol, CryptoPair.SOLUSDT.symbol)
        )
    )
}