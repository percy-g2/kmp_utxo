import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @StateObject private var appearance = AppAppearance()
    @Environment(\.colorScheme) private var deviceScheme

    /// Resolved appearance: the in-app override, or the device's when the theme is "System".
    private var isDark: Bool {
        (appearance.colorScheme ?? deviceScheme) == .dark
    }

    /// The Compose theme background (#141313 dark) so native chrome matches the content
    /// instead of iOS's default pure black.
    private var appBackgroundArgb: Int32 {
        MainViewControllerKt.themeBackgroundArgb(dark: isDark)
    }

    var body: some View {
        ZStack {
            // Base layer behind the status-bar strip and the translucent tab bar.
            Color(argb: appBackgroundArgb).ignoresSafeArea()

            if #available(iOS 26.0, *) {
                // Native SwiftUI TabView with Liquid Glass; each tab hosts a Compose screen.
                LiquidGlassRootView()
            } else {
                // Fallback: full Compose UI with its own bottom bar.
                ComposeView()
                    .ignoresSafeArea(edges: .all)
                    .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
            }
        }
        // Paint the window itself so any uncovered native area matches the app background.
        .background(WindowBackgroundSetter(color: UIColor(argb: appBackgroundArgb)))
        // Make native chrome (TabView, status bar) follow the in-app theme rather than the
        // device appearance. nil = follow the device (when the app theme is "System").
        .preferredColorScheme(appearance.colorScheme)
    }
}

/// Sets the host UIWindow's background color so the status-bar strip and the area behind the
/// Liquid Glass tab bar match the app background rather than iOS's default black.
private struct WindowBackgroundSetter: UIViewRepresentable {
    let color: UIColor

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async { uiView.window?.backgroundColor = color }
    }
}

private extension Color {
    init(argb: Int32) {
        let v = UInt32(bitPattern: argb)
        self.init(
            .sRGB,
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: Double((v >> 24) & 0xFF) / 255
        )
    }
}

private extension UIColor {
    convenience init(argb: Int32) {
        let v = UInt32(bitPattern: argb)
        self.init(
            red: CGFloat((v >> 16) & 0xFF) / 255,
            green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255,
            alpha: CGFloat((v >> 24) & 0xFF) / 255
        )
    }
}

/// Observes the in-app theme (persisted in Kotlin/KStore) and exposes it as a SwiftUI
/// `ColorScheme?` so the native chrome can match the app's Light/Dark/System setting.
final class AppAppearance: ObservableObject {
    @Published var colorScheme: ColorScheme?

    init() {
        // observeAppTheme emits on the main thread; hop through main async to be safe and to
        // satisfy main-actor isolation for the @Published write.
        _ = MainViewControllerKt.observeAppTheme { name in
            DispatchQueue.main.async {
                switch name {
                case "Dark": self.colorScheme = .dark
                case "Light": self.colorScheme = .light
                default: self.colorScheme = nil // System → follow the device
                }
            }
        }
    }
}



