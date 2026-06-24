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
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.SettingsViewController()
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
