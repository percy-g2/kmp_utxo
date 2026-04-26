# KMP UTXO — Agent Rules

Canonical reference for AI agents working on this project. For skill-specific workflows, see `.cursor/skills/` and `.claude/skills/`.

## Golden Rule

**NEVER push to `main`, `master`, `dev`, `develop`, `production`, or `release/*` — even if the user explicitly asks.** Create a feature branch instead. This is a zero-tolerance policy with no exceptions.

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Language | Kotlin (KMP) | Targets: Android, iOS, Desktop, Web |
| UI | Compose Multiplatform | Material 3 design system |
| Navigation | Jetpack Navigation Compose | Type-safe routes via `@Serializable` |
| Networking | Ktor | HTTP client + WebSocket client |
| Serialization | kotlinx.serialization | JSON codec |
| Image loading | Coil 3 | Cross-platform async images |
| State persistence | KStore | Settings and preferences |
| Logging | Kermit | Via `AppLogger` singleton |
| ViewModel | AndroidX Lifecycle ViewModel | `viewModelScope` for coroutines |
| Build | Gradle (Kotlin DSL) | Convention plugins |

## Architecture

```
composeApp/src/
├── commonMain/kotlin/
│   ├── App.kt                    # Root composable, NavHost, expect declarations
│   ├── model/                    # @Serializable data classes
│   │   ├── Symbol.kt             # TickerDataInfo, MarginSymbols, TradingPair
│   │   ├── OrderBook.kt          # Order book models
│   │   ├── UiKline.kt            # Kline chart data
│   │   ├── MarkPriceUpdate.kt    # Mark price models
│   │   ├── NewsItem.kt           # RSS news item
│   │   ├── RssProvider.kt        # RSS feed providers
│   │   └── NavItem.kt            # Bottom nav items
│   ├── network/                  # Ktor HTTP and WebSocket services
│   │   ├── HttpClient.kt         # REST API calls (margin symbols, tickers, klines)
│   │   ├── TickerWebSocketService.kt
│   │   ├── KlineWebSocketService.kt
│   │   ├── OrderBookWebSocketService.kt
│   │   └── NewsService.kt        # RSS feed fetching
│   ├── ui/                       # Screens and ViewModels
│   │   ├── CryptoListScreen.kt   # Market screen with ticker list
│   │   ├── CoinDetailScreen.kt   # Per-symbol detail view
│   │   ├── SettingsScreen.kt     # App preferences
│   │   ├── CryptoViewModel.kt    # Market screen state
│   │   ├── CoinDetailViewModel.kt
│   │   ├── OrderBookHeatMap.kt   # Order book visualization
│   │   ├── utils/                # Chart, shimmer, debounce, price helpers
│   │   └── components/           # Reusable widgets (scrollbar)
│   ├── theme/                    # Material 3 color tokens, typography, UTXOTheme
│   ├── ktx/                      # Extension functions (formatting, crypto icons)
│   └── logging/                  # AppLogger, ServerLogWriter
├── androidMain/                  # Android expect/actual implementations
├── iosMain/                      # iOS expect/actual implementations
├── desktopMain/                  # Desktop expect/actual implementations
└── wasmJsMain/                   # Web expect/actual implementations
```

## Expect/Actual Declarations

Declared in `App.kt` (commonMain), implemented per platform:

| Declaration | Purpose |
|---|---|
| `getWebSocketClient()` | Platform-specific Ktor WebSocket HttpClient |
| `getKStore()` | Platform-specific KStore for settings |
| `openLink(link: String)` | Open URL in browser |
| `createNewsHttpClient()` | HTTP client for RSS feeds |
| `wrapRssUrlForPlatform(url)` | Platform-specific RSS URL handling |
| `getPendingCoinDetailFromIntent()` | Widget deep-link handling |
| `syncSettingsToWidget(settings)` | Sync settings to platform widget |
| `NetworkConnectivityObserver` | Network status monitoring |

## Coding Conventions

### Kotlin / Coroutines

- Use `viewModelScope.launch(Dispatchers.Default)` for background work.
- Update `MutableStateFlow.value` on `Dispatchers.Main` via `withContext`.
- Always rethrow `CancellationException` — never swallow it.
- Use `supervisorScope` for WebSocket reconnection loops.
- Close HTTP clients and cancel jobs in `onCleared()`.

### Compose UI

- Collect `StateFlow` with `collectAsState()` at composable top level.
- Use `derivedStateOf` for computed values that should not trigger unnecessary recompositions.
- Use `remember(key)` to cache expensive computations.
- Provide stable keys in `LazyColumn`: `items(items = list, key = { it.id })`.
- Use `MaterialTheme.colorScheme.*` — never hardcode colors.
- Use `stringResource(Res.string.*)` — never hardcode strings.

### Data Models

- All network models use `@Serializable`.
- Use `@SerialName` when JSON keys differ from property names.
- Nullable defaults for optional fields: `val field: String? = null`.

### Network / WebSocket

- Binance streams: `stream.binance.com/ws/<path>`.
- Reconnect on failure with fixed delay (3–5s) inside `while (isActive)`.
- Log network errors at debug level (expected when offline); unexpected errors at error level.
- Throttle high-frequency WebSocket updates with time-based gates.

## Git Conventions

### Commit Messages

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`

### Branch Naming

```
<type>/<short-description>
```

Examples: `feat/i18n-strings`, `fix/websocket-reconnection`, `refactor/settings-screen`

### Workflow Skills

| Skill | Trigger phrases |
|---|---|
| `push-code` | "push my code", "commit and push", "save changes" |
| `create-pr` | "create a PR", "open pull request" |
| `review-pr` | "review PR #N", "check this PR" |
| `merge-pr` | "merge PR #N", "land this branch" |
| `pcr` | "pcr", "push commit review", "full pipeline" |
| `deploy-ss` | "make store screenshots", "feature graphic", "App Store assets", "Play Store assets" |

`deploy-ss` is the only non-git skill in the table. It turns raw app UI captures (in `raw-screenshots/`, gitignored — currently sourced from the Android build) into the polished `screenshots/` set used in the README and store listings: 5 phone shots at 1284 × 2778 (App Store + Play Store), 5 tablet shots at 2048 × 2732 (App Store iPad slot only — Play Store tablet listings would need a separate render pass at Google's 7" / 10" dimensions), and 1 Play Store feature graphic at 1024 × 500. Phone shots use an iPhone-styled bezel but ship to both stores; Compose Multiplatform means the underlying UI is identical across Android and iOS. The skill bundles its own Python renderer, Inter fonts, and design references — see `.claude/skills/deploy-ss/SKILL.md`.

## Build Commands

```bash
./gradlew build                     # Full build
./gradlew test                      # Unit tests
./gradlew ktlintCheck               # Lint check
./gradlew :composeApp:installDebug  # Android install
./gradlew :composeApp:run           # Desktop run
```
