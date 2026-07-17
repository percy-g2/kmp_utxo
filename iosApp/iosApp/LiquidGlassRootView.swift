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
    /// Tab that Settings was opened from, so its back arrow can return there (tabs have no back stack).
    @State private var previousSelection = 0
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
                    // Extend Compose under the floating glass tab bar so the list frosts through it
                    // (Contacts-style). Bottom only — the top status-bar inset must be preserved.
                    .ignoresSafeArea(.container, edges: .bottom)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: CoinRoute.self) { route in
                        // Tie the destination's identity to the coin so a widget deep-link that
                        // REPLACES the path (e.g. BTC -> ETH) recreates the Compose host instead of
                        // reusing the previous one. CoinDetailComposeView.updateUIViewController is a
                        // no-op, so without a per-coin id the hosted ComposeUIViewController would
                        // keep showing the old coin.
                        detail(route) { marketPath.removeLast() }
                            .id(route)
                    }
                }
            }

            Tab("Favorites", systemImage: "star.fill", value: 1) {
                NavigationStack(path: $favPath) {
                    FavoritesComposeView { symbol, display in
                        favPath.append(CoinRoute(symbol: symbol, display: display))
                    }
                    .ignoresSafeArea(.keyboard)
                    .ignoresSafeArea(.container, edges: .bottom)
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: CoinRoute.self) { route in
                        // Per-coin identity, mirroring the Market stack (see note above).
                        detail(route) { favPath.removeLast() }
                            .id(route)
                    }
                }
            }

            Tab("Portfolio", systemImage: "chart.pie.fill", value: 2) {
                PortfolioComposeView { selection = 3 } // "Open Settings" → Settings tab
                    .ignoresSafeArea(.keyboard)
                    .ignoresSafeArea(.container, edges: .bottom)
            }

            Tab("Settings", systemImage: "gearshape", value: 3) {
                SettingsComposeView { selection = previousSelection } // back → origin tab
                    .ignoresSafeArea(.keyboard)
                    .ignoresSafeArea(.container, edges: .bottom)
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .task {
            MainViewControllerKt.cryptoResume()
            consumeDeepLink() // handle a link delivered before observation started
        }
        .onChange(of: selection) { oldValue, newValue in
            // Remember where Settings was entered from so its back arrow can return there.
            if newValue == 3 { previousSelection = oldValue }
            // Market(0)/Favorites(1) need the live market stream; Portfolio(2)/Settings(3) don't.
            if newValue == 0 || newValue == 1 {
                MainViewControllerKt.cryptoResume()
            } else {
                MainViewControllerKt.cryptoPause()
            }
            // Only the Portfolio tab needs its (per-wallet) live streams.
            if newValue == 2 {
                MainViewControllerKt.portfolioResume()
            } else {
                MainViewControllerKt.portfolioPause()
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
            // Replace any existing coin detail rather than appending, so repeated widget taps
            // never stack multiple detail screens on the Market tab's NavigationStack. Setting an
            // equal single-element path is a no-op, so re-tapping the same coin doesn't reload it.
            marketPath = NavigationPath([coin])
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
            .onDisappear { if selection == 0 || selection == 1 { MainViewControllerKt.cryptoResume() } }
    }
}
