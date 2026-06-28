import UIKit

/// Restores the standard iOS left-edge swipe-to-go-back gesture.
///
/// Our coin-detail route lives in a SwiftUI `NavigationStack` whose nav bar is hidden
/// (`.toolbar(.hidden, for: .navigationBar)` in `LiquidGlassRootView`) because the Compose
/// screen draws its own top bar. Hiding the nav bar also disables UIKit's
/// `interactivePopGestureRecognizer` by default — the edge-pop is tied to the visible back
/// button — so only the in-screen back button worked. Re-pointing the gesture's delegate at the
/// navigation controller and allowing it whenever there is something to pop brings the native
/// swipe-back behavior back.
///
/// The `viewControllers.count > 1` guard means the gesture only fires on a pushed screen
/// (coin detail), never on a tab's list root.
extension UINavigationController: UIGestureRecognizerDelegate {
    open override func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
