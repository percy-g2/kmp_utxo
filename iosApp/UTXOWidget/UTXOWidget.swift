//
//  UTXOWidget.swift
//  UTXOWidget
//
//  Created by Prashant Gahlot on 21/12/25.
//  Copyright © 2025 orgName. All rights reserved.
//

import WidgetKit
import SwiftUI

struct UTXOWidget: Widget {
    let kind: String = "UTXOWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: FavoritesTimelineProvider()) { entry in
            FavoritesWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
                .padding(.horizontal, 0)
        }
        .configurationDisplayName("Favorites")
        .description("Display your favorite cryptocurrencies with live prices and charts.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct FavoritesWidgetEntry: TimelineEntry {
    let date: Date
    let favorites: [FavoriteCoin]
    let isLoading: Bool
    let isRefreshing: Bool
    let errorMessage: String?
    let rotationAngle: Double
    
    struct FavoriteCoin: Identifiable {
        let id: String
        let symbol: String
        let baseSymbol: String
        let quoteSymbol: String
        let price: String
        let changePercent: Double
        let volume: String
        let chartData: [Double]
    }
    
    init(date: Date, favorites: [FavoriteCoin], isLoading: Bool, isRefreshing: Bool, errorMessage: String?, rotationAngle: Double = 0) {
        self.date = date
        self.favorites = favorites
        self.isLoading = isLoading
        self.isRefreshing = isRefreshing
        self.errorMessage = errorMessage
        self.rotationAngle = rotationAngle
    }
}

struct FavoritesTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> FavoritesWidgetEntry {
        FavoritesWidgetEntry(
            date: Date(),
            favorites: [
                .init(id: "1", symbol: "BTCUSDT", baseSymbol: "BTC", quoteSymbol: "USDT", price: "50,000", changePercent: 2.5, volume: "1.2B", chartData: []),
                .init(id: "2", symbol: "ETHUSDT", baseSymbol: "ETH", quoteSymbol: "USDT", price: "3,000", changePercent: -1.2, volume: "800M", chartData: [])
            ],
            isLoading: false,
            isRefreshing: false,
            errorMessage: nil,
            rotationAngle: 0
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (FavoritesWidgetEntry) -> ()) {
        let entry = placeholder(in: context)
        completion(entry)
    }
    
    // Track refresh state and cache last favorites to show while refreshing
    static var isRefreshing = false
    static var lastFavorites: [FavoritesWidgetEntry.FavoriteCoin] = []

    func getTimeline(in context: Context, completion: @escaping (Timeline<FavoritesWidgetEntry>) -> ()) {
        Task {
            let now = Date()
            var allEntries: [FavoritesWidgetEntry] = []
            
            // Check if this is a refresh request
            let wasRefreshing = FavoritesTimelineProvider.isRefreshing
            
            // If refreshing, create refreshing entry with visual feedback
            if wasRefreshing {
                FavoritesTimelineProvider.isRefreshing = false
                
                // Create multiple refreshing entries with progressive rotation for smoother visual feedback
                // This makes the refresh feel more responsive and animated
                for i in 0..<3 {
                    let rotationAngle = Double(i) * 120.0 // 0°, 120°, 240°
                    let entryDate = now.addingTimeInterval(Double(i) * 0.1) // 0.1 seconds apart
                    allEntries.append(FavoritesWidgetEntry(
                        date: entryDate,
                        favorites: FavoritesTimelineProvider.lastFavorites,
                        isLoading: false,
                        isRefreshing: true,
                        errorMessage: nil,
                        rotationAngle: rotationAngle
                    ))
                }
                
                // Small delay to ensure refreshing state is visible before loading new data
                try? await Task.sleep(nanoseconds: 400_000_000) // 0.4 seconds
            }
            
            // Load actual data
            let entry = await loadFavoritesData()
            
            // Cache the favorites for next refresh
            FavoritesTimelineProvider.lastFavorites = entry.favorites
            
            // Add the final data entry after the refreshing entries (if any)
            let finalEntryDate = wasRefreshing ? now.addingTimeInterval(0.8) : now
            let finalEntry = FavoritesWidgetEntry(
                date: finalEntryDate,
                favorites: entry.favorites,
                isLoading: false,
                isRefreshing: false,
                errorMessage: entry.errorMessage,
                rotationAngle: 0
            )
            allEntries.append(finalEntry)
            
            // Schedule next update in 5 minutes
            let nextUpdate = Calendar.current.date(byAdding: .minute, value: 5, to: Date()) ?? Date()
            // Use .after policy to ensure refresh happens at the scheduled time
            // iOS will refresh the widget around this time (may vary based on system heuristics)
            let timeline = Timeline(entries: allEntries, policy: .after(nextUpdate))
            completion(timeline)
        }
    }
    
    private func loadFavoritesData() async -> FavoritesWidgetEntry {
        print("🔵 UTXOWidget: loadFavoritesData() called")
        
        // TEST: Direct UserDefaults access check
        if let testDefaults = UserDefaults(suiteName: "group.org.androdevlinux.utxo") {
            print("🔵 TEST: App Group UserDefaults accessible")
            testDefaults.synchronize() // Force sync
            let allKeys = testDefaults.dictionaryRepresentation().keys
            print("🔵 TEST: All keys count: \(allKeys.count)")
            print("🔵 TEST: All keys: \(Array(allKeys))")
            
            if let testFavPairs = testDefaults.string(forKey: "favPairs") {
                print("🔵 TEST: ✅ Found favPairs as string: \(testFavPairs)")
            } else {
                print("🔵 TEST: ❌ favPairs not found as string")
                if let testObject = testDefaults.object(forKey: "favPairs") {
                    print("🔵 TEST: Found as object: \(testObject)")
                    print("🔵 TEST: Object type: \(type(of: testObject))")
                } else {
                    print("🔵 TEST: ❌ favPairs key doesn't exist at all")
                }
            }
        } else {
            print("🔵 TEST: ❌ Cannot access App Group UserDefaults - App Groups not configured!")
        }
        
        do {
            // Read settings
            print("🔵 UTXOWidget: Calling WidgetDataHelper.readSettings()...")
            let settings = try WidgetDataHelper.readSettings()
            print("🔵 UTXOWidget: Settings read successfully")
            print("🔵 UTXOWidget: Settings.favPairs count: \(settings.favPairs.count)")
            print("🔵 UTXOWidget: Settings.favPairs: \(settings.favPairs)")
            
            // Filter out empty strings and take up to 4 favorites
            let favorites = settings.favPairs.filter { !$0.isEmpty && !$0.trimmingCharacters(in: .whitespaces).isEmpty }.prefix(4)
            
            print("🔵 UTXOWidget: After filtering - Loaded \(favorites.count) favorites from settings")
            print("🔵 UTXOWidget: Favorites list: \(Array(favorites))")
            
            if favorites.isEmpty {
                print("🔵 UTXOWidget: ❌ No favorites found after filtering")
                print("🔵 UTXOWidget: Total favPairs in settings: \(settings.favPairs.count)")
                print("🔵 UTXOWidget: Raw favPairs: \(settings.favPairs)")
                return FavoritesWidgetEntry(
                    date: Date(),
                    favorites: [],
                    isLoading: false,
                    isRefreshing: false,
                    errorMessage: "No favorites added",
                    rotationAngle: 0
                )
            }
            
            print("🔵 UTXOWidget: ✅ Found \(favorites.count) favorites, proceeding to fetch data...")
            
            // Fetch ticker data for favorites
            var favoriteCoins: [FavoritesWidgetEntry.FavoriteCoin] = []
            
            for symbol in favorites {
                if let tickerData = await WidgetDataHelper.fetchTicker(symbol: symbol) {
                    let (base, quote) = WidgetDataHelper.extractSymbolParts(symbol: symbol, tradingPair: settings.selectedTradingPair)
                    let chartData = await WidgetDataHelper.fetchChartData(symbol: symbol)
                    
                    favoriteCoins.append(.init(
                        id: symbol,
                        symbol: symbol,
                        baseSymbol: base,
                        quoteSymbol: quote,
                        price: WidgetDataHelper.formatPrice(tickerData.lastPrice, quoteSymbol: quote),
                        changePercent: Double(tickerData.priceChangePercent) ?? 0.0,
                        volume: WidgetDataHelper.formatVolume(tickerData.quoteVolume),
                        chartData: chartData
                    ))
                }
            }
            
            // isRefreshing is already reset in getTimeline, so use false here
            return FavoritesWidgetEntry(
                date: Date(),
                favorites: Array(favoriteCoins),
                isLoading: false,
                isRefreshing: false,
                errorMessage: nil,
                rotationAngle: 0
            )
        } catch {
            // isRefreshing is already reset in getTimeline, so use false here
            return FavoritesWidgetEntry(
                date: Date(),
                favorites: [],
                isLoading: false,
                isRefreshing: false,
                errorMessage: "Failed to load data",
                rotationAngle: 0
            )
        }
    }
}

#Preview(as: .systemMedium) {
    UTXOWidget()
} timeline: {
    FavoritesWidgetEntry(
        date: Date(),
        favorites: [
            .init(id: "1", symbol: "BTCUSDT", baseSymbol: "BTC", quoteSymbol: "USDT", price: "50,000 USDT", changePercent: 2.5, volume: "1.2B", chartData: []),
            .init(id: "2", symbol: "ETHUSDT", baseSymbol: "ETH", quoteSymbol: "USDT", price: "3,000 USDT", changePercent: -1.2, volume: "800M", chartData: [])
        ],
        isLoading: false,
        isRefreshing: false,
        errorMessage: nil,
        rotationAngle: 0
    )
}
