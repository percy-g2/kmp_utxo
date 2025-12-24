import AppIntents
import WidgetKit

struct RefreshWidgetIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Widget"
    static var description = IntentDescription("Refresh the favorites widget data")
    
    func perform() async throws -> some IntentResult {
        // Set refreshing state to show visual feedback immediately
        FavoritesTimelineProvider.isRefreshing = true
        
        // Reload widget timeline immediately - this triggers the visual feedback
        // The isRefreshing flag will be reset in getTimeline after showing the loading state
        WidgetCenter.shared.reloadTimelines(ofKind: "UTXOWidget")
        
        // Small delay to ensure the reload request is processed
        try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
        
        return .result()
    }
}

