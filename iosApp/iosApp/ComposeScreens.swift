import SwiftUI
import UIKit
import ComposeApp

// Thin UIViewControllerRepresentable wrappers around the per-screen Compose hosts
// exported from Kotlin (MainViewControllerKt). Used by the iOS 26 native TabView path.

struct MarketComposeView: UIViewControllerRepresentable {
    let onCoin: (String, String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MarketViewController(onCoin: onCoin)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct FavoritesComposeView: UIViewControllerRepresentable {
    let onCoin: (String, String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.FavoritesViewController(onCoin: onCoin)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct SettingsComposeView: UIViewControllerRepresentable {
    /// Invoked by the screen's back arrow; should return to the tab Settings was opened from.
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.SettingsViewController(onBack: onBack)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct PortfolioComposeView: UIViewControllerRepresentable {
    /// Invoked by the screen's "Open Settings" actions; should switch to the Settings tab.
    let onConfigure: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.PortfolioViewController(onConfigure: onConfigure)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct CoinDetailComposeView: UIViewControllerRepresentable {
    let symbol: String
    let displaySymbol: String
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.CoinDetailViewController(
            symbol: symbol,
            displaySymbol: displaySymbol,
            onBack: onBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
