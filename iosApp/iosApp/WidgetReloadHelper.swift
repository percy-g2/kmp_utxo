import Foundation
import WidgetKit

/// Helper to reload widget timeline when favorites change
/// This is called from Kotlin code via Objective-C interop
@objc public class WidgetReloadHelper: NSObject {
    @objc public static func reloadWidgetTimeline() {
        WidgetCenter.shared.reloadTimelines(ofKind: "UTXOWidget")
        print("WidgetReloadHelper: ✅ Reloaded widget timeline for UTXOWidget")
    }
}
