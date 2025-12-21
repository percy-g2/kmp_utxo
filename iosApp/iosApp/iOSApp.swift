import SwiftUI
import WidgetKit

@main
struct iOSApp: App {
    @StateObject private var urlHandler = URLHandler()
    private var timer: Timer?
    
    init() {
        // Check UserDefaults flag periodically to reload widget when Kotlin sets it
        // This is simpler than NSNotification which has API issues in Kotlin/Native
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            let userDefaults = UserDefaults.standard
            if userDefaults.object(forKey: "WidgetReloadRequested") != nil {
                // Clear the flag and reload widget
                userDefaults.removeObject(forKey: "WidgetReloadRequested")
                userDefaults.synchronize()
                WidgetCenter.shared.reloadTimelines(ofKind: "UTXOWidget")
            }
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(urlHandler)
                .onOpenURL { url in
                    urlHandler.handleURL(url)
                }
        }
    }
}

class URLHandler: ObservableObject {
    func handleURL(_ url: URL) {
        guard url.scheme == "utxo" else { return }
        
        if url.host == "coin" {
            // Extract symbol from path: utxo://coin/BTCUSDT
            let symbol = url.pathComponents.last ?? ""
            let (base, quote) = extractSymbolParts(symbol: symbol, tradingPair: "BTC")
            let displaySymbol = quote.isEmpty ? symbol : "\(base)/\(quote)"
            
            // Store in UserDefaults for Kotlin code to read
            let userDefaults = UserDefaults.standard
            userDefaults.set(symbol, forKey: "pendingCoinSymbol")
            userDefaults.set(displaySymbol, forKey: "pendingCoinDisplaySymbol")
            userDefaults.synchronize()
        } else if url.host == "favorites" {
            // Navigate to favorites - store empty to trigger favorites navigation
            let userDefaults = UserDefaults.standard
            userDefaults.set("", forKey: "pendingCoinSymbol")
            userDefaults.set("", forKey: "pendingCoinDisplaySymbol")
            userDefaults.synchronize()
        }
    }
    
    private func extractSymbolParts(symbol: String, tradingPair: String) -> (String, String) {
        let quoteCurrencies = ["USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", "DAI", "TUSD", "EUR", "GBP", "JPY"]
        
        if symbol.uppercased().hasSuffix(tradingPair.uppercased()) && symbol.count > tradingPair.count {
            let base = String(symbol.prefix(symbol.count - tradingPair.count))
            if !base.isEmpty {
                return (base, tradingPair)
            }
        }
        
        for quote in quoteCurrencies.sorted(by: { $0.count > $1.count }) {
            if symbol.uppercased().hasSuffix(quote.uppercased()) && symbol.count > quote.count {
                let base = String(symbol.prefix(symbol.count - quote.count))
                if !base.isEmpty {
                    return (base, quote)
                }
            }
        }
        
        return (symbol, "")
    }
}