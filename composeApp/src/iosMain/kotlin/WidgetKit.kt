import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
abstract class WidgetConfiguration

abstract class Timeline<T : TimelineEntry>(val entries: List<T>, val policy: NSNumber)

interface TimelineEntry {
    val date: NSDate
}

@OptIn(ExperimentalForeignApi::class)
object ReloadPolicy {
    val atEnd: NSNumber = NSNumber(0)
    val never: NSNumber = NSNumber(1)
    val after: (NSDate) -> NSNumber = { date -> NSNumber(2) }
}

@OptIn(ExperimentalForeignApi::class)
fun createTimeline(entries: List<TimelineEntry>, policy: NSNumber): Timeline<TimelineEntry> =
    object : Timeline<TimelineEntry>(entries, policy) {}