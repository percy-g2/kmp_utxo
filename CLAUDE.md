# Project Rules

## Git Branch Policy

**NEVER push directly to `main` or `master`.** All changes must go to a feature branch.

When committing or pushing code:
1. Check the current branch with `git branch --show-current`
2. If on `main` or `master`, create a new branch first based on the git diff:
   - Use format: `<type>/<short-description>` (e.g., `refactor/price-alert-ux`, `fix/notification-formatting`)
   - Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `perf`, `test`, `style`
3. Never stage secrets: `.env`, `*.jks`, `key.txt`, `credentials.*`, `*.pem`, `*.key`, `prod.jks`
4. Push with upstream tracking: `git push -u origin <branch-name>`
5. Use `/push-code` skill for the full workflow

## Project Structure

This is a **Kotlin Multiplatform** (KMP) app using **Compose Multiplatform** targeting Android, iOS, Desktop, and WASM.

```
composeApp/          # Shared KMP module (all platforms)
  src/commonMain/    # Shared code
  src/androidMain/   # Android-specific (expect/actual)
  src/iosMain/       # iOS-specific
  src/desktopMain/   # Desktop-specific
  src/wasmJsMain/    # WASM/browser-specific
androidApp/          # Android app wrapper
iosApp/              # Native iOS app (Swift/Xcode)
```

### Package Layout (commonMain)

```
App.kt                    # Entry point, navigation, expect declarations
data/repository/          # Repositories (KStore persistence)
domain/                   # Business logic (trigger logic, evaluators)
domain/model/             # Domain entities (@Serializable)
model/                    # Data models (API responses, nav items)
network/                  # Ktor HTTP client, WebSocket services
ui/                       # Compose screens and ViewModels
ui/components/            # Reusable composables
ui/utils/                 # UI utilities (chart, shimmer, debounce)
ui/error/                 # Error mapping
theme/                    # Material3 colors, typography (expect/actual)
ktx/                      # Kotlin extensions (Double, String formatting)
logging/                  # Kermit logger setup
notification/             # Notification service (expect/actual)
```

## Build Commands

- **Quick compile check**: `./gradlew composeApp:compileKotlinDesktop`
- **Android debug**: `./gradlew androidApp:assembleDebug`
- **WASM browser**: `./gradlew composeApp:wasmJsBrowserDistribution`
- **Run tests**: `./gradlew composeApp:desktopTest`

## Code Conventions

### Compose

- Composable functions use **PascalCase** (enforced via .editorconfig ktlint override)
- Use **Material3** components exclusively (`androidx.compose.material3.*`)
- Theme colors defined in `theme/Color.kt` — use semantic colors from `MaterialTheme.colorScheme`, custom colors (`greenLight`, `redDark`) for trading-specific UI
- Typography is `expect/actual` per platform in `theme/Typography.kt`
- Use `stringResource(Res.string.*)` for all user-facing text — strings live in `composeResources/values/strings.xml`
- Bottom sheets: `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
- Navigation: type-safe routes via `@Serializable` objects/data classes in `model/NavItem.kt`

### Kotlin / KMP

- **Serialization**: `kotlinx.serialization` for all models — annotate with `@Serializable`, use `@SerialName` for sealed class variants
- **Coroutines**: `viewModelScope.launch` in ViewModels, `Flow`/`StateFlow` for reactive state
- **Persistence**: KStore (file-based JSON) — `expect fun getKStore()` / `expect fun getAlertsKStore()` with platform `actual` implementations
- **Networking**: Ktor client with platform engines (CIO/Android/Darwin/JS), WebSocket plugin for real-time data
- **Platform-specific code**: Use `expect`/`actual` declarations — `expect` in commonMain, `actual` in each platform source set
- **UUIDs**: `com.benasher44.uuid.uuid4()` for generating IDs
- **Time**: `kotlin.time.Clock.System.now()` and `kotlinx.datetime` — no `java.time`
- **Logging**: `AppLogger.logger` (Kermit) — use `.d {}`, `.w {}`, `.e {}` with lazy messages

### Architecture

- **ViewModel**: AndroidX `ViewModel` with `viewModelScope`, expose `StateFlow` for UI state
- **Repository pattern**: Interface in `data/repository/`, implementation uses KStore with `Mutex` for write serialization
- **Sealed classes** for UI state (`Loading`, `Success`, `Empty`, `Error`)
- **Sealed classes** for domain variants (e.g., `AlertCondition.PriceAbove`, `PriceBelow`)

### Formatting

- **ktlint** enforced via Gradle plugin — run `./gradlew ktlintCheck` to verify
- Trailing commas on multiline parameter lists
- 4-space indentation
- Price formatting: use `Double.formatAsCurrency()` from `ktx/DoubleKtx.kt`
- Volume formatting: use `String.formatVolume()` from `ktx/StringKtx.kt`

## Platform Notes

- **Android**: minSdk 24, targetSdk 36, namespace `org.androdevlinux.utxo`
- **iOS**: Static framework, x64 + arm64 + simulatorArm64
- **Desktop**: JVM-based, distributed as DMG/MSI/DEB
- **WASM**: Browser target, deployed to gh-pages
- **Kotlin**: 2.3.20, Compose: 1.10.3

## Testing

- Test framework: `kotlin.test` with `kotlinx-coroutines-test`
- Tests in `composeApp/src/commonTest/`
- Run: `./gradlew composeApp:desktopTest`
