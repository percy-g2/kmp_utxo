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
    var body: some View {
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
}



