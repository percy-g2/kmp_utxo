import AppIntents
import WidgetKit

struct RefreshWidgetIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Widget"
    static var description = IntentDescription("Refresh the favorites widget data")
    
    func perform() async throws -> some IntentResult {
        // Set refreshing state to show visual feedback
        FavoritesTimelineProvider.isRefreshing = true
        
        // Reload widget timeline immediately to show refreshing state
        WidgetCenter.shared.reloadTimelines(ofKind: "UTXOWidget")
        
        // Small delay to show visual feedback, then reload with actual data
        try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
        
        // Reload again to fetch and display new data (will reset isRefreshing to false)
        WidgetCenter.shared.reloadTimelines(ofKind: "UTXOWidget")
        
        return .result()
    }
}

