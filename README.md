![Kotlin Version](https://img.shields.io/badge/kotlin-2.2.21-blue?logo=kotlin) [![Build and Publish](https://github.com/percy-g2/kmp_utxo/actions/workflows/build-and-publish-web.yml/badge.svg)](https://github.com/percy-g2/kmp_utxo/actions/workflows/build-and-publish-web.yml)

[<img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg"
alt="Get it on Google Play Store"
height="40">](https://play.google.com/store/apps/details?id=org.androdevlinux.utxo)

[<img src="https://upload.wikimedia.org/wikipedia/commons/9/91/Download_on_the_App_Store_RGB_blk.svg"
alt="Get it on Google Play Store"
height="40">](https://apps.apple.com/in/app/utxo/id6746167853)

# UTXO - Cryptocurrency Tracker

A modern, cross-platform cryptocurrency tracking application built with Kotlin Multiplatform and Compose Multiplatform.

<table>
  <tr>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s1.png" width="200" alt="1"></td>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s2.png" width="200" alt="2"></td>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s3.png" width="200" alt="3"></td>
  </tr>
  <tr>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s4.png" width="200" alt="4"></td>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s5.png" width="200" alt="5"></td>
    <td><img src="https://github.com/percy-g2/kmp_utxo/blob/main/screenshots/s6.png" width="200" alt="6"></td>
  </tr>
</table>

**UTXO** is a **Kotlin Multiplatform** cryptocurrency tracking application built with **Compose Multiplatform** that targets **Android**, **iOS**, **JVM (Desktop)**, and **Web** platforms. The app provides real-time cryptocurrency price data using the [Binance WebSocket API](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams) and comprehensive market information.

## Tech Stack
- **Kotlin Multiplatform** - Shared business logic across all platforms
- **Compose Multiplatform** - Modern declarative UI framework
- **Coroutines & Flow** - Asynchronous operations and reactive data streams
- **Ktor** - HTTP client and WebSocket support for network operations
- **KStore** - Persistent storage management for user preferences
- **Kotlinx Serialization** - JSON parsing and data serialization
- **Kotlinx DateTime** - Date and time handling

## Features

### 📊 Market Screen
- **Real-time price updates** via Binance WebSocket connection
- **Live cryptocurrency list** with price changes, 24h volume, and market statistics
- **Search functionality** to quickly find cryptocurrencies
- **Sorting options** by volume, price change, and more
- **Smooth animations** and transitions

### ⭐ Favorites
- **Save favorite cryptocurrencies** for quick access
- **Persistent favorites** stored locally across app sessions
- **Quick navigation** to favorite coin details

### 📈 Coin Detail Screen
- **Interactive candlestick charts** with price history visualization
- **24-hour ticker statistics** including:
  - High/Low prices
  - Price change percentage
  - Trading volume (base and quote)
  - Best bid/ask prices
  - Weighted average price
- **Latest news feed** aggregated from multiple RSS sources:
  - CoinDesk
  - CoinTelegraph
  - Decrypt
  - The Block
  - CryptoSlate
  - U.Today
  - Bitcoin Magazine
  - BeInCrypto
- **News filtering** by coin symbol with intelligent matching
- **Real-time price updates** displayed in the header

### ⚙️ Settings
- **Theme customization**:
  - System default (follows device theme)
  - Light mode
  - Dark mode
- **News source selection** - Enable/disable RSS providers
- **About section** with:
  - App version information
  - Privacy Policy link
  - Website link
  - GitHub repository link

### 🌐 Cross-Platform Support
- **Android** - Native Android app with Material Design 3
- **iOS** - Native iOS app with SwiftUI integration
- **JVM (Desktop)** - Native desktop applications for Windows, macOS, and Linux
- **Web** - Web with wasmJS

### 🔔 Additional Features
- **Network connectivity monitoring** - Alerts when offline
- **Optimized performance** - WebSocket pause/resume for memory efficiency
- **Error handling** - Graceful error states and retry mechanisms
- **Responsive design** - Adapts to different screen sizes

## Development Workflow

This project uses Cursor AI agents to automate common development tasks. See the [Cursor Agents Guide](docs/CURSOR_AGENTS_GUIDE.md) for quick reference on using agents with single commands.

**Quick Commands:**
- Commit changes: `Use Git Commit & Push Agent to commit my changes`
- Create PR: `Use GitHub PR Creation Agent to create a pull request`
- Review PR: `Use GitHub PR Review Agent to review PR #42`
- Merge PR: `Use GitHub PR Merge Agent to merge PR #42`

For complete agent specifications, see [`AGENT_RULES.md`](AGENT_RULES.md).

## Building the Project

### Prerequisites
- JDK 17 or higher
- Android Studio or IntelliJ IDEA
- Xcode (for iOS builds)
- Gradle 8.0+

### Build Commands
```bash
# Build for all platforms
./gradlew build

# Build for specific platform
./gradlew :composeApp:assembleDebug          # Android
./gradlew :composeApp:iosSimulatorArm64Binaries # iOS Simulator
./gradlew :composeApp:runDistributable      # JVM Desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun # Web

# Run tests
./gradlew test
```

## Platform-Specific Notes

### Android
- Minimum SDK: 24
- Target SDK: 34
- Uses Material Design 3 components

### iOS
- Minimum iOS version: 15.0
- Requires Xcode 14.0+
- Uses SwiftUI for app lifecycle

### Web
- Built with Kotlin/Wasm
- Uses WebSocket API for real-time updates
- CORS proxy support for RSS feeds

### JVM (Desktop)
- Built on JVM for Windows, macOS, and Linux
- Native window management with Compose Desktop
- Requires JDK 17+ for building and running

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3) - see the [LICENSE](LICENSE) file for details.

The GPLv3 is a strong copyleft license that requires anyone who distributes your code or a derivative work to make the source available under the same terms. This license is particularly suitable for software that you want to keep open and free.

Key points of the GPLv3:
- You can use the software for any purpose
- You can change the software and distribute modified versions
- You can share the software with others
- If you distribute modified versions, you must share your modifications under the GPLv3
- You must include the license and copyright notice with each copy of the software
- You must disclose your source code when you distribute the software

For the full license text, visit: https://www.gnu.org/licenses/gpl-3.0.en.html
