import WidgetKit
import SwiftUI
import AppIntents

struct FavoritesWidgetEntryView: View {
    var entry: FavoritesWidgetEntry
    @Environment(\.widgetFamily) var family
    
    var body: some View {
        Group {
            switch family {
            case .systemMedium:
                MediumWidgetView(entry: entry)
            case .systemLarge:
                LargeWidgetView(entry: entry)
            default:
                MediumWidgetView(entry: entry)
            }
        }
        .widgetURL(URL(string: "utxo://favorites"))
    }
}

struct MediumWidgetView: View {
    var entry: FavoritesWidgetEntry
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack(spacing: 0) {
                Text("Your Favorites")
                    .font(.headline)
                    .fontWeight(.bold)
                Spacer()
                Button(intent: RefreshWidgetIntent()) {
                    RefreshIconView(isRefreshing: entry.isRefreshing)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.bottom, 8)
            .padding(.horizontal, 0)
            
            if entry.isLoading {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
                Spacer()
            } else if let errorMessage = entry.errorMessage {
                Spacer()
                Text(errorMessage)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Spacer()
            } else if entry.favorites.isEmpty {
                Spacer()
                Text("No favorites added")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                // Show up to 2 favorites in medium widget
                ForEach(Array(entry.favorites.prefix(2))) { favorite in
                    FavoriteRowView(favorite: favorite)
                    if favorite.id != entry.favorites.prefix(2).last?.id {
                        Divider()
                            .padding(.vertical, 4)
                    }
                }
            }
        }
        .padding(.vertical, 12)
    }
}

struct LargeWidgetView: View {
    var entry: FavoritesWidgetEntry
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack(spacing: 0) {
                Text("Your Favorites")
                    .font(.headline)
                    .fontWeight(.bold)
                Spacer()
                Button(intent: RefreshWidgetIntent()) {
                    RefreshIconView(isRefreshing: entry.isRefreshing)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.bottom, 8)
            .padding(.horizontal, 0)
            
            if entry.isLoading {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
                Spacer()
            } else if let errorMessage = entry.errorMessage {
                Spacer()
                Text(errorMessage)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Spacer()
            } else if entry.favorites.isEmpty {
                Spacer()
                Text("No favorites added")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                // Show up to 4 favorites in large widget
                ForEach(Array(entry.favorites.prefix(4).enumerated()), id: \.element.id) { index, favorite in
                    FavoriteRowView(favorite: favorite)
                    if index < entry.favorites.prefix(4).count - 1 {
                        Divider()
                            .padding(.vertical, 4)
                    }
                }
            }
        }
        .padding(.vertical, 12)
    }
}

struct FavoriteRowView: View {
    let favorite: FavoritesWidgetEntry.FavoriteCoin
    
    var body: some View {
        Link(destination: URL(string: "utxo://coin/\(favorite.symbol)") ?? URL(string: "utxo://favorites")!) {
            HStack(spacing: 8) {
                // No horizontal padding on the row itself
                // Left: Symbol and Volume
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text(favorite.baseSymbol)
                            .font(.system(size: 18, weight: .bold))
                        Text("/")
                            .font(.system(size: 18, weight: .bold))
                        Text(favorite.quoteSymbol)
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                    Text(favorite.volume)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // Center: Chart
                if !favorite.chartData.isEmpty {
                    SparklineChartView(data: favorite.chartData, isPositive: favorite.changePercent >= 0)
                        .frame(width: 80, height: 40)
                } else {
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 80, height: 40)
                }
                
                Spacer()
                
                // Right: Change and Price
                VStack(alignment: .trailing, spacing: 4) {
                    Text(formatChangePercent(favorite.changePercent))
                        .font(.system(size: 13))
                        .foregroundColor(favorite.changePercent >= 0 ? .green : .red)
                    Text(favorite.price)
                        .font(.system(size: 13))
                }
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 0)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func formatChangePercent(_ value: Double) -> String {
        let sign = value >= 0 ? "+" : ""
        return String(format: "%@%.2f%%", sign, value)
    }
}

// Refresh icon with visual feedback
struct RefreshIconView: View {
    let isRefreshing: Bool
    
    var body: some View {
        Image(systemName: isRefreshing ? "arrow.clockwise.circle.fill" : "arrow.clockwise")
            .font(.system(size: 14))
            .foregroundColor(isRefreshing ? .blue.opacity(0.7) : .blue)
            .rotationEffect(.degrees(isRefreshing ? 90 : 0))
            .symbolEffect(.pulse, isActive: isRefreshing)
    }
}

// Simple sparkline chart view for widgets (Charts framework not available in widgets)
struct SparklineChartView: View {
    let data: [Double]
    let isPositive: Bool
    
    var body: some View {
        GeometryReader { geometry in
            Path { path in
                guard !data.isEmpty else { return }
                
                let minValue = data.min() ?? 0
                let maxValue = data.max() ?? 0
                let range = maxValue - minValue
                
                guard range > 0 else { return }
                
                let width = geometry.size.width
                let height = geometry.size.height
                let padding: CGFloat = 2
                let chartWidth = width - padding * 2
                let chartHeight = height - padding * 2
                
                for (index, value) in data.enumerated() {
                    let x = padding + (CGFloat(index) / CGFloat(max(data.count - 1, 1))) * chartWidth
                    let normalizedValue = (value - minValue) / range
                    let y = padding + chartHeight - (normalizedValue * chartHeight)
                    
                    if index == 0 {
                        path.move(to: CGPoint(x: x, y: y))
                    } else {
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                }
            }
            .stroke(isPositive ? Color.green : Color.red, lineWidth: 2)
        }
    }
}

