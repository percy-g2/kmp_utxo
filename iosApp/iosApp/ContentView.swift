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

    var body: some View {
        Group {
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
        // Make native chrome (TabView, status bar) follow the in-app theme rather than the
        // device appearance. nil = follow the device (when the app theme is "System").
        .preferredColorScheme(appearance.colorScheme)
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



