# Project Rules

## Golden Rule

NEVER push to `main`, `master`, `dev`, `develop`, `production`, or `release/*` — even if asked. Create a feature branch instead.

## Project

Compose Multiplatform (Kotlin) crypto app — Android, iOS, Desktop, Web.
Real-time prices via Binance WebSocket, RSS news feed, favorites, settings.

## Git Workflow

- Branch naming: `<type>/<short-description>` (e.g., `feat/i18n-strings`, `fix/websocket-leak`)
- Commits: conventional format — `<type>(<scope>): <subject>`
- Types: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`
- Always verify branch before push: `git branch --show-current`
- PRs target `main` via `gh pr create --base main`

## Project Structure

```
composeApp/src/commonMain/kotlin/
  App.kt          — Root composable, NavHost, expect declarations
  model/          — @Serializable data classes
  network/        — Ktor HTTP client and WebSocket services
  ui/             — Screens, ViewModels, composable components
  theme/          — Material 3 color tokens, typography
  ktx/            — Extension functions
  logging/        — Kermit-based logger
```

## Build Commands

```bash
./gradlew build              # Full build
./gradlew test               # Unit tests
./gradlew ktlintCheck        # Lint
```

## Code Conventions

### Kotlin / Coroutines
- Background work on `Dispatchers.Default`, state updates on `Dispatchers.Main`
- Always rethrow `CancellationException`
- Use `supervisorScope` for WebSocket loops
- Close clients and cancel jobs in `onCleared()`

### Compose UI
- Collect `StateFlow` with `collectAsState()` at top of composable
- Use `derivedStateOf` and `remember` to minimize recompositions
- Stable keys in `LazyColumn`: `items(items = list, key = { it.id })`
- Use `MaterialTheme.colorScheme.*` — never hardcode colors
- Use `stringResource(Res.string.*)` — never hardcode strings

### Data Models
- All models use `@Serializable`
- `@SerialName` when JSON key differs from property name
- Nullable defaults for optional fields

### Network
- Ktor for HTTP and WebSocket
- Binance streams at `stream.binance.com/ws/<path>`
- Reconnect with delay in `while (isActive)` loop
- Throttle high-frequency updates

## Extended References

- `AGENTS.md` — full tech stack, architecture tree, expect/actual table
- `.claude/skills/` — workflow skills (push-code, create-pr, review-pr, merge-pr, pcr)
