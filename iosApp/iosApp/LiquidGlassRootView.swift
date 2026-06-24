import SwiftUI
import ComposeApp

/// Route pushed onto a tab's NavigationStack to show a coin's detail.
struct CoinRoute: Hashable {
    let symbol: String
    let display: String
}

/// iOS 26+ root: a native SwiftUI TabView (automatic Liquid Glass) hosting each
/// screen as its own Compose view. Replaces the Compose bottom bar on iOS 26.
@available(iOS 26.0, *)
struct LiquidGlassRootView: View {
    @EnvironmentObject private var urlHandler: URLHandler
    @State private var selection = 0
    @State private var marketPath = NavigationPath()
    @State private var favPath = NavigationPath()

    var body: some View {
        TabView(selection: $selection) {
            Tab("Market", systemImage: "chart.bar.xaxis", value: 0) {
                NavigationStack(path: $marketPath) {
                    MarketComposeView { symbol, display in
                        marketPath.append(CoinRoute(symbol: symbol, display: display))
                    }
                    .ignoresSafeArea(.keyboard)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: CoinRoute.self) { route in
                        detail(route) { marketPath.removeLast() }
                    }
                }
            }

            Tab("Favorites", systemImage: "star.fill", value: 1) {
                NavigationStack(path: $favPath) {
                    FavoritesComposeView { symbol, display in
                        favPath.append(CoinRoute(symbol: symbol, display: display))
                    }
                    .ignoresSafeArea(.keyboard)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: CoinRoute.self) { route in
                        detail(route) { favPath.removeLast() }
                    }
                }
            }

            Tab("Settings", systemImage: "gearshape", value: 2) {
                SettingsComposeView()
                    .ignoresSafeArea(.keyboard)
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .task {
            MainViewControllerKt.cryptoResume()
            consumeDeepLink() // handle a link delivered before observation started
        }
        .onChange(of: selection) { _, newValue in
            if newValue == 2 {
                MainViewControllerKt.cryptoPause()
            } else {
                MainViewControllerKt.cryptoResume()
            }
        }
        .onChange(of: urlHandler.pendingCoin) { _, _ in consumeDeepLink() }
        .onChange(of: urlHandler.selectFavorites) { _, _ in consumeDeepLink() }
    }

    /// Drains pending deep-link intents published by URLHandler. Each is cleared as it is
    /// handled, so the .task check and the onChange observers never double-navigate.
    private func consumeDeepLink() {
        if let coin = urlHandler.pendingCoin {
            urlHandler.pendingCoin = nil
            selection = 0
            marketPath.append(coin)
        }
        if urlHandler.selectFavorites {
            urlHandler.selectFavorites = false
            selection = 1
        }
    }

    private func detail(_ route: CoinRoute, onBack: @escaping () -> Void) -> some View {
        CoinDetailComposeView(symbol: route.symbol, displaySymbol: route.display, onBack: onBack)
            .ignoresSafeArea(.keyboard)
            .toolbar(.hidden, for: .navigationBar)
            .toolbar(.hidden, for: .tabBar)
            .onAppear { MainViewControllerKt.cryptoPause() }
            .onDisappear { if selection != 2 { MainViewControllerKt.cryptoResume() } }
    }
}
