import Foundation

struct WidgetDataHelper {
    // MARK: - Settings
    
    struct Settings: Codable {
        let appTheme: String?
        let favPairs: [String]
        let selectedTradingPair: String
        let enabledRssProviders: [String]?
        
        enum CodingKeys: String, CodingKey {
            case appTheme
            case favPairs
            case selectedTradingPair
            case enabledRssProviders
        }
        
        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            appTheme = try container.decodeIfPresent(String.self, forKey: .appTheme)
            favPairs = try container.decodeIfPresent([String].self, forKey: .favPairs) ?? []
            selectedTradingPair = try container.decodeIfPresent(String.self, forKey: .selectedTradingPair) ?? "BTC"
            enabledRssProviders = try container.decodeIfPresent([String].self, forKey: .enabledRssProviders)
        }
        
        init(appTheme: String?, favPairs: [String], selectedTradingPair: String, enabledRssProviders: [String]?) {
            self.appTheme = appTheme
            self.favPairs = favPairs
            self.selectedTradingPair = selectedTradingPair
            self.enabledRssProviders = enabledRssProviders
        }
    }
    
    static func readSettings() throws -> Settings {
        // Widget extensions run in a separate process and can't directly access the main app's cache directory.
        // We need to use App Groups for shared storage.
        
        // Method 1: Try UserDefaults with App Group FIRST (primary method for widget sharing)
        // The main app syncs favorites here when settings change
        let appGroupId = "group.org.androdevlinux.utxo"
        print("🔵 WidgetDataHelper: ========== START readSettings() ==========")
        print("🔵 WidgetDataHelper: Attempting to read from App Group UserDefaults...")
        print("🔵 WidgetDataHelper: App Group ID: \(appGroupId)")
        print("🔵 WidgetDataHelper: Widget bundle ID: \(Bundle.main.bundleIdentifier ?? "unknown")")
        
        // Try to get shared UserDefaults
        let sharedDefaults = UserDefaults(suiteName: appGroupId)
        print("🔵 WidgetDataHelper: UserDefaults(suiteName:) result: \(sharedDefaults != nil ? "✅ SUCCESS" : "❌ FAILED")")
        
        if let sharedDefaults = sharedDefaults {
            // Force synchronize to ensure we have latest data
            sharedDefaults.synchronize()
            print("🔵 WidgetDataHelper: Synchronized UserDefaults")
            print("🔵 WidgetDataHelper: ✅ App Group UserDefaults available")
            
            // Print all keys for debugging
            let allKeys = sharedDefaults.dictionaryRepresentation().keys
            print("🔵 WidgetDataHelper: All UserDefaults keys: \(Array(allKeys))")
            print("🔵 WidgetDataHelper: Total keys count: \(allKeys.count)")
            
            // Check if favPairs key exists (as any type)
            let favPairsValue = sharedDefaults.object(forKey: "favPairs")
            print("🔵 WidgetDataHelper: favPairs value (any type): \(favPairsValue != nil ? "EXISTS" : "NIL")")
            if let favPairsValue = favPairsValue {
                print("🔵 WidgetDataHelper: favPairs value type: \(type(of: favPairsValue))")
                print("🔵 WidgetDataHelper: favPairs value description: \(String(describing: favPairsValue))")
                
                // Try to convert to string if it's not already
                if let stringValue = favPairsValue as? String {
                    print("🔵 WidgetDataHelper: favPairs is String: \(stringValue)")
                } else if let dataValue = favPairsValue as? Data {
                    print("🔵 WidgetDataHelper: favPairs is Data, size: \(dataValue.count) bytes")
                    if let stringFromData = String(data: dataValue, encoding: .utf8) {
                        print("🔵 WidgetDataHelper: Converted Data to String: \(stringFromData)")
                    }
                }
            }
            
            // Try multiple ways to read the value
            var favPairsJson: String? = nil
            
            // Method 1: Try as string directly
            if let stringValue = sharedDefaults.string(forKey: "favPairs") {
                favPairsJson = stringValue
                print("🔵 WidgetDataHelper: ✅ Read favPairs as string: \(stringValue)")
            }
            
            // Method 2: Try reading as object and converting
            if favPairsJson == nil, let objectValue = sharedDefaults.object(forKey: "favPairs") {
                if let stringValue = objectValue as? String {
                    favPairsJson = stringValue
                    print("🔵 WidgetDataHelper: ✅ Read favPairs from object as string: \(stringValue)")
                } else if let dataValue = objectValue as? Data,
                          let stringFromData = String(data: dataValue, encoding: .utf8) {
                    favPairsJson = stringFromData
                    print("🔵 WidgetDataHelper: ✅ Read favPairs from object as Data, converted: \(stringFromData)")
                }
            }
            
            if let favPairsJson = favPairsJson {
                print("🔵 WidgetDataHelper: ✅ Found favPairs JSON string: \(favPairsJson)")
                print("🔵 WidgetDataHelper: JSON string length: \(favPairsJson.count)")
                
                if let favPairsData = favPairsJson.data(using: .utf8) {
                    print("🔵 WidgetDataHelper: ✅ Converted JSON string to Data, size: \(favPairsData.count) bytes")
                    
                    do {
                        let favPairs = try JSONDecoder().decode([String].self, from: favPairsData)
                        print("🔵 WidgetDataHelper: ✅ Successfully decoded favorites: \(favPairs)")
                        print("🔵 WidgetDataHelper: Favorites count: \(favPairs.count)")
                        
                        // Found favorites in UserDefaults
                        let selectedTradingPair = sharedDefaults.string(forKey: "selectedTradingPair") ?? "BTC"
                        let appTheme = sharedDefaults.string(forKey: "appTheme")
                        print("🔵 WidgetDataHelper: ✅ Returning Settings with \(favPairs.count) favorites")
                        
                        return Settings(
                            appTheme: appTheme,
                            favPairs: favPairs,
                            selectedTradingPair: selectedTradingPair,
                            enabledRssProviders: nil
                        )
                    } catch {
                        print("🔵 WidgetDataHelper: ❌ Failed to decode favPairs JSON: \(error)")
                        print("🔵 WidgetDataHelper: Error details: \(error.localizedDescription)")
                        if let jsonString = String(data: favPairsData, encoding: .utf8) {
                            print("🔵 WidgetDataHelper: Attempted to decode: \(jsonString)")
                        }
                    }
                } else {
                    print("🔵 WidgetDataHelper: ❌ Failed to convert JSON string to Data")
                }
            } else {
                print("🔵 WidgetDataHelper: ❌ Could not read favPairs as string from UserDefaults")
                print("🔵 WidgetDataHelper: Available keys: \(Array(allKeys))")
                print("🔵 WidgetDataHelper: Checking each key individually...")
                
                // Debug: Try reading each key
                for key in allKeys {
                    if let value = sharedDefaults.object(forKey: key) {
                        print("🔵 WidgetDataHelper: Key '\(key)': type=\(type(of: value)), value=\(value)")
                    }
                }
            }
        } else {
            print("🔵 WidgetDataHelper: ❌ App Group UserDefaults not available - App Groups may not be configured")
            print("🔵 WidgetDataHelper: Make sure App Groups capability is added to both app and widget targets")
            print("🔵 WidgetDataHelper: App Group ID: group.org.androdevlinux.utxo")
        }
        
        // Method 2: Fall back to App Group file container (if configured)
        var settingsFile: URL?
        var triedPaths: [String] = []
        
        if let appGroupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.org.androdevlinux.utxo") {
            let appGroupSettingsFile = appGroupURL.appendingPathComponent("settings.json")
            triedPaths.append(appGroupSettingsFile.path)
            if FileManager.default.fileExists(atPath: appGroupSettingsFile.path) {
                settingsFile = appGroupSettingsFile
            }
        }
        
        // Method 3: Fall back to widget's own cache directory (won't have main app's data)
        if settingsFile == nil {
            if let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first {
                let cacheSettingsFile = cacheDir.appendingPathComponent("settings.json")
                triedPaths.append(cacheSettingsFile.path)
                if FileManager.default.fileExists(atPath: cacheSettingsFile.path) {
                    settingsFile = cacheSettingsFile
                }
            }
        }
        
        guard let settingsFile = settingsFile else {
            // Debug: Log tried paths
            print("🔵 WidgetDataHelper: Settings file not found. Tried paths: \(triedPaths)")
            print("🔵 WidgetDataHelper: Returning empty Settings (no UserDefaults, no file)")
            print("🔵 WidgetDataHelper: ========== END readSettings() - RETURNING EMPTY ==========")
            return Settings(appTheme: "System", favPairs: [], selectedTradingPair: "BTC", enabledRssProviders: nil)
        }
        
        do {
            let data = try Data(contentsOf: settingsFile)
            
            // Debug: Print raw JSON
            if let jsonString = String(data: data, encoding: .utf8) {
                print("WidgetDataHelper: Found settings file at \(settingsFile.path)")
                print("WidgetDataHelper: Settings JSON (first 500 chars): \(String(jsonString.prefix(500)))")
            }
            
            let decoder = JSONDecoder()
            let settings = try decoder.decode(Settings.self, from: data)
            
            // Debug: Print decoded settings
            print("WidgetDataHelper: Decoded favPairs count: \(settings.favPairs.count)")
            print("WidgetDataHelper: Decoded favPairs: \(settings.favPairs)")
            let filtered = settings.favPairs.filter { !$0.isEmpty && !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            print("WidgetDataHelper: Filtered favPairs count: \(filtered.count)")
            print("WidgetDataHelper: Filtered favPairs: \(filtered)")
            
            return settings
        } catch {
            print("WidgetDataHelper: Failed to decode settings: \(error)")
            // Try to read as string to see what we got
            if let data = try? Data(contentsOf: settingsFile),
               let jsonString = String(data: data, encoding: .utf8) {
                print("WidgetDataHelper: Raw JSON content (first 500 chars): \(String(jsonString.prefix(500)))")
            }
            throw error
        }
    }
    
    // MARK: - Ticker Data
    
    struct TickerData: Codable {
        let symbol: String
        let lastPrice: String
        let priceChangePercent: String
        let quoteVolume: String
    }
    
    static func fetchTicker(symbol: String) async -> TickerData? {
        guard let url = URL(string: "https://api.binance.com/api/v3/ticker/24hr?symbol=\(symbol)") else {
            return nil
        }
        
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                return nil
            }
            
            // Check if response is an error
            if let responseString = String(data: data, encoding: .utf8),
               responseString.contains("\"code\"") && responseString.contains("\"msg\"") {
                return nil
            }
            
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            let ticker24hr = try decoder.decode(Ticker24hrResponse.self, from: data)
            
            return TickerData(
                symbol: ticker24hr.symbol,
                lastPrice: ticker24hr.lastPrice,
                priceChangePercent: ticker24hr.priceChangePercent,
                quoteVolume: ticker24hr.quoteVolume
            )
        } catch {
            return nil
        }
    }
    
    private struct Ticker24hrResponse: Codable {
        let symbol: String
        let lastPrice: String
        let priceChangePercent: String
        let quoteVolume: String
    }
    
    // MARK: - Chart Data
    
    static func fetchChartData(symbol: String) async -> [Double] {
        guard let url = URL(string: "https://api.binance.com/api/v3/uiKlines?symbol=\(symbol)&interval=1s&limit=1000") else {
            return []
        }
        
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            
            guard let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[Any]] else {
                return []
            }
            
            // Extract close prices (index 4 in the kline array)
            return jsonArray.compactMap { kline in
                guard kline.count > 4,
                      let closePriceString = kline[4] as? String,
                      let closePrice = Double(closePriceString) else {
                    return nil
                }
                return closePrice
            }
        } catch {
            return []
        }
    }
    
    // MARK: - Formatting Helpers
    
    static func extractSymbolParts(symbol: String, tradingPair: String) -> (String, String) {
        let quoteCurrencies = ["USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", "DAI", "TUSD", "EUR", "GBP", "JPY"]
        
        // First try with selected trading pair
        if symbol.uppercased().hasSuffix(tradingPair.uppercased()) && symbol.count > tradingPair.count {
            let base = String(symbol.prefix(symbol.count - tradingPair.count))
            if !base.isEmpty {
                return (base, tradingPair)
            }
        }
        
        // Fallback: try to extract from symbol using common quote currencies
        for quote in quoteCurrencies.sorted(by: { $0.count > $1.count }) {
            if symbol.uppercased().hasSuffix(quote.uppercased()) && symbol.count > quote.count {
                let base = String(symbol.prefix(symbol.count - quote.count))
                if !base.isEmpty {
                    return (base, quote)
                }
            }
        }
        
        // Last resort: return symbol as-is
        return (symbol, "")
    }
    
    static func formatPrice(_ price: String, quoteSymbol: String) -> String {
        guard let priceValue = Double(price) else {
            return price
        }
        
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 8
        
        if priceValue >= 1000 {
            formatter.maximumFractionDigits = 0
        } else if priceValue >= 1 {
            formatter.maximumFractionDigits = 2
        } else if priceValue >= 0.01 {
            formatter.maximumFractionDigits = 4
        } else {
            formatter.maximumFractionDigits = 8
        }
        
        let formattedPrice = formatter.string(from: NSNumber(value: priceValue)) ?? price
        
        if !quoteSymbol.isEmpty {
            return "\(formattedPrice) \(quoteSymbol)"
        } else {
            return formattedPrice
        }
    }
    
    static func formatVolume(_ volume: String) -> String {
        guard let value = Double(volume) else {
            return volume
        }
        
        if value >= 1_000_000_000 {
            let billions = value / 1_000_000_000
            if billions >= 100 {
                return "\(Int(billions))B"
            } else if billions >= 10 {
                return String(format: "%.1fB", billions)
            } else {
                return String(format: "%.2fB", billions)
            }
        } else if value >= 1_000_000 {
            let millions = value / 1_000_000
            if millions >= 100 {
                return "\(Int(millions))M"
            } else if millions >= 10 {
                return String(format: "%.1fM", millions)
            } else {
                return String(format: "%.2fM", millions)
            }
        } else if value >= 1_000 {
            let formatter = NumberFormatter()
            formatter.numberStyle = .decimal
            formatter.maximumFractionDigits = 0
            return formatter.string(from: NSNumber(value: value)) ?? volume
        } else if value >= 1 {
            return String(format: "%.2f", value)
        } else {
            return String(format: "%.4f", value)
        }
    }
}

