import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import model.SymbolPriceTicker
import network.HttpClient
import platform.Foundation.NSDate
import theme.ThemeManager
import kotlin.experimental.ExperimentalObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SymbolPriceWidget")
class SymbolPriceWidget {
    @OptIn(DelicateCoroutinesApi::class)
    @ObjCName("provideTimeline")
    fun provideTimeline(completion: (Timeline<TimelineEntry>) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            val httpClient = HttpClient()
            val symbols = ThemeManager.store.get()?.favPairs ?: emptyList()
            val tickers = httpClient.fetchSymbolPriceTicker(symbols)

            val entry = SymbolPriceEntry(NSDate(), tickers ?: emptyList())
            val timeline = createTimeline(listOf(entry), ReloadPolicy.atEnd)

            completion(timeline)
        }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SymbolPriceEntry")
class SymbolPriceEntry(override val date: NSDate, val tickers: List<SymbolPriceTicker>) : TimelineEntry