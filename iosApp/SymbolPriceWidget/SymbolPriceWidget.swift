//
//  SymbolPriceWidget.swift
//  SymbolPriceWidget
//
//  Created by Prashant Gahlot on 17/08/24.
//  Copyright © 2024 orgName. All rights reserved.
//

import WidgetKit
import SwiftUI
import ComposeApp

struct SymbolPriceWidgetEntry: WidgetKit.TimelineEntry {
    let date: Date
    let tickers: [SymbolPriceTicker] // Assuming this is defined in the Kotlin module
}

struct SymbolPriceWidgetProvider: WidgetKit.TimelineProvider {
    func placeholder(in context: Context) -> SymbolPriceWidgetEntry {
        SymbolPriceWidgetEntry(date: Date(), tickers: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (SymbolPriceWidgetEntry) -> Void) {
        SymbolPriceWidget().provideTimeline { timeline in
            if let firstEntry = timeline.entries.first as? SymbolPriceEntry {
                completion(SymbolPriceWidgetEntry(date: firstEntry.date as Date, tickers: firstEntry.tickers))
            }
        }
    }

    func getTimeline(in context: Context, completion: @escaping (WidgetKit.Timeline<SymbolPriceWidgetEntry>) -> Void) {
            SymbolPriceWidget().provideTimeline { timeline in
                let currentDate = Date()
                var entries: [SymbolPriceWidgetEntry] = []
                
                // Create entries for the next minute, updating every 10 seconds
                for offset in stride(from: 0, to: 60, by: 10) {
                    let entryDate = Calendar.current.date(byAdding: .second, value: offset, to: currentDate)!
                    if let entry = timeline.entries.first as? SymbolPriceEntry {
                        entries.append(SymbolPriceWidgetEntry(date: entryDate, tickers: entry.tickers))
                    }
                }
                
                completion(WidgetKit.Timeline(entries: entries, policy: .atEnd))
            }
        }
}

struct SymbolPriceWidgetEntryView: View {
    var entry: SymbolPriceWidgetEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        VStack {
            Text("Symbol Prices")
                .font(.headline)

            ForEach(entry.tickers, id: \.symbol) { ticker in
                Text("\(ticker.symbol): \(ticker.price)")
                    .font(.body)
            }
        }
        .padding()
        .containerBackground(for: .widget) {
            Color.clear
        }
    }
}


struct SymbolPriceWidgets: SwiftUI.Widget {
    private let kind: String = "SymbolPriceWidget"

    public var body: some SwiftUI.WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SymbolPriceWidgetProvider()) { entry in
            SymbolPriceWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Symbol Price Widget")
        .description("Displays the latest symbol prices.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

#if DEBUG
struct SymbolPriceWidgets_Previews: PreviewProvider {
    static var previews: some View {
        SymbolPriceWidgetEntryView(entry: SymbolPriceWidgetEntry(date: Date(), tickers: [
            SymbolPriceTicker(symbol: "BTC", price: "50000"),
            SymbolPriceTicker(symbol: "ETH", price: "3000")
        ]))
        .previewContext(WidgetPreviewContext(family: .systemSmall))
    }
}
#endif
