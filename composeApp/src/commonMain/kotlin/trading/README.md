# Binance Microstructure Trading Engine

A production-ready, low-latency scalping engine for Binance spot markets using order book imbalance and trade flow analysis.

## Architecture

```
trading/
├── data/              # Data models and utilities
│   ├── AggTrade.kt           # Aggregated trade data
│   ├── MarketSnapshot.kt     # Complete market state snapshot
│   ├── MarketSnapshotBuilder.kt  # Builder for snapshots
│   └── OrderBook.kt          # Order book calculations
│
├── strategy/          # Trading strategy components
│   ├── ImbalanceCalculator.kt    # Order book imbalance detection
│   ├── TradeFlowAnalyzer.kt       # Aggressive trade flow analysis
│   ├── SpreadFilter.kt            # Spread and liquidity filtering
│   ├── PositionSizer.kt          # Depth-aware position sizing
│   └── TradeSignal.kt            # Trading signal types
│
├── execution/         # Order execution
│   ├── ExecutionPolicy.kt        # Maker/taker order selection
│   └── OrderExecutor.kt          # Order execution interface & implementations
│
├── risk/              # Risk management
│   ├── RiskManager.kt            # Central risk management
│   └── DailyLossGuard.kt        # Daily loss limits
│
├── engine/            # Main engine
│   ├── TradingEngine.kt          # Core trading engine
│   └── TradingEngineFactory.kt   # Factory for engine setup
│
├── api/               # Binance API adapters
│   ├── BinanceApiAdapter.kt      # REST API client
│   └── AggTradeWebSocketService.kt  # WebSocket trade stream
│
├── config/            # Configuration
│   └── StrategyConfig.kt         # All strategy parameters
│
└── example/           # Usage examples
    └── TradingEngineExample.kt   # Example implementation
```

## Core Principles

### 1. Order Book Imbalance Detection
- Analyzes top 20 levels of order book
- Calculates volume-weighted imbalance ratio
- **LONG signal**: Imbalance > 1.5 (bid volume 1.5x ask volume)
- **SHORT signal**: Imbalance < 0.67 (ask volume 1.5x bid volume)

### 2. Trade Flow Confirmation
- Uses aggregated trades to detect aggressive buying/selling
- Only trades when imbalance AND trade flow agree on direction
- Prevents false signals from order book spoofing

### 3. Spread & Liquidity Filtering
- Rejects trades if spread > 0.1% (configurable)
- Ensures depth can absorb 2% of order size
- Prevents slippage and partial fills

### 4. Depth-Aware Position Sizing
- Maximum position = 2% of visible depth
- Risk per trade ≤ 0.5% of equity
- Accounts for slippage and fees
- No fixed lot sizes - all sizing is dynamic

### 5. Intelligent Execution
- **Maker orders**: When spread tight and price stable (lower fees)
- **Taker orders**: When momentum high and trade flow accelerating
- Supports LIMIT, MARKET, and post-only orders

### 6. Strict Risk Management
- Max daily loss: 2% of equity
- Max consecutive losses: 3 (then cooldown)
- No trading during extreme volatility
- All trades MUST pass risk checks

## Trading Flow

```
onMarketUpdate():
    ↓
1. Risk Manager Check → REJECT if blocked
    ↓
2. Spread Filter Check → REJECT if fails
    ↓
3. Strategy Evaluation:
   - Calculate imbalance
   - Analyze trade flow
   - Generate signal (LONG/SHORT/NONE)
    ↓
4. Position Size Calculation → REJECT if too small
    ↓
5. Final Spread Filter Check → REJECT if fails
    ↓
6. Determine Order Type (Maker/Taker/Market)
    ↓
7. Execute Order
    ↓
8. Record Result in Risk Manager
```

**A trade is placed ONLY if ALL conditions are met. Otherwise → NO TRADE**

## Usage Example

```kotlin
// Initialize
val factory = TradingEngineFactory(
    httpClient = httpClient,
    config = StrategyConfig.DEFAULT,
    equity = 10000.0
)

val engine = factory.create()
val apiAdapter = factory.createApiAdapter()
val aggTradeService = factory.createAggTradeWebSocket()

// Connect to WebSocket streams
orderBookService.connect("BTCUSDT", levels = 20)
aggTradeService.connect("BTCUSDT")

// Process market updates
orderBookService.orderBookData.collectLatest { orderBook ->
    val trades = aggTradeService.trades.value.take(500)
    val snapshot = snapshotBuilder.build("BTCUSDT", orderBook, trades)
    val result = engine.onMarketUpdate(snapshot)
    // Handle result...
}
```

## Configuration

All parameters are configurable via `StrategyConfig`:

```kotlin
val config = StrategyConfig(
    imbalanceLong = 1.5,           // LONG threshold
    imbalanceShort = 0.67,          // SHORT threshold
    maxSpreadPct = 0.001,           // 0.1% max spread
    maxRiskPerTradePct = 0.005,     // 0.5% max risk per trade
    maxDailyLossPct = 0.02,         // 2% max daily loss
    // ... more parameters
)
```

Presets available:
- `StrategyConfig.DEFAULT` - Balanced settings
- `StrategyConfig.CONSERVATIVE` - Tighter risk limits
- `StrategyConfig.AGGRESSIVE` - Looser risk limits

## Binance API Endpoints

### REST API (via BinanceApiAdapter)
- `GET /api/v3/depth` - Order book depth
- `GET /api/v3/aggTrades` - Aggregated trades
- `GET /api/v3/ticker/bookTicker` - Best bid/ask
- `GET /api/v3/klines` - Kline/candlestick data

### WebSocket Streams
- `<symbol>@depth20` - Order book depth updates (via OrderBookWebSocketService)
- `<symbol>@aggTrade` - Aggregated trades stream (via AggTradeWebSocketService)

## Testing

The engine includes a `MockOrderExecutor` for testing without real API calls:

```kotlin
val factory = TradingEngineFactory(
    httpClient = httpClient,
    config = config,
    equity = equity,
    orderExecutor = MockOrderExecutor(simulateSlippage = true)
)
```

## Key Features

✅ **No indicator-based strategies** - Pure microstructure analysis  
✅ **Order book + trade flow confirmation** - Prevents false signals  
✅ **Depth-aware position sizing** - Respects liquidity constraints  
✅ **Strict risk management** - Hard stops on losses  
✅ **Configurable thresholds** - No magic numbers  
✅ **Production-ready** - Proper error handling and logging  
✅ **Testable** - Mock executor for backtesting  

## Important Notes

⚠️ **Order Execution**: The `BinanceOrderExecutor` requires API keys and proper authentication. It's currently a skeleton - implement actual API calls for production use.

⚠️ **User Data Stream**: For execution reports and order status, implement Binance User Data Stream WebSocket separately.

⚠️ **Testing**: Always test with `MockOrderExecutor` first before using real API keys.

## Success Criteria

A trade is executed ONLY when:
1. ✅ Order book imbalance exists (LONG or SHORT)
2. ✅ Trade flow confirms the direction
3. ✅ Spread is acceptable (< 0.1%)
4. ✅ Risk manager approves
5. ✅ Position size fits available depth
6. ✅ All filters pass

**Otherwise → NO TRADE**

This system prioritizes:
- Capital preservation
- Fee efficiency  
- Slippage control

Not:
- High trade count
- Overfitting
- Indicator-based gambling

