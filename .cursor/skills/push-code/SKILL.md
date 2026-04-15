---
name: push-code
description: Stage, commit, and push uncommitted code with a well-structured conventional commit message. Use when the user asks to push code, commit and push, save changes to remote, or sync local changes with GitHub.
---

# Push Code

Stage all changes, generate a conventional commit message, and push to the remote branch.

## Prerequisites

- Git repository initialized
- Remote origin configured
- GitHub CLI authenticated (`gh auth status`)

## Workflow

### 1. Branch protection check (MANDATORY FIRST STEP)

```bash
CURRENT_BRANCH=$(git branch --show-current)
```

If `$CURRENT_BRANCH` is `main`, `master`, `dev`, `develop`, `production`, or matches `release/*`:

1. **STOP** — do not commit or push.
2. Analyze changes to determine an appropriate branch name.
3. Create feature branch: `git checkout -b <type>/<short-description>`.
4. Inform the user: "Protected branch detected. Created `<branch>` to keep `<protected>` safe."
5. **REFUSE** even if the user explicitly asks to push to the protected branch.

### 2. Check working tree

```bash
git status
git diff --stat
```

- Verify there are uncommitted changes.
- Check for merge conflicts and resolve before proceeding.
- Ensure no secrets or sensitive files (`.env`, `*.jks`, credentials) are staged.

### 3. Code quality validation (if CI is not configured)

```bash
./gradlew ktlintCheck   # Lint
./gradlew test           # Unit tests
./gradlew build          # Full build
```

If any check fails, report the errors and stop.

### 4. Generate commit message

Use the conventional commit format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`

**Subject**: imperative mood, max 50 chars, no trailing period.

**Body**: explain WHAT changed and WHY. List modified files/modules. Document breaking changes.

**Footer**: `Closes #<issue>` if applicable.

Example:

```
feat(websocket): implement Binance WebSocket connection for crypto prices

- Added WebSocketManager class with retry logic and exponential backoff
- Created CryptoPriceModel data class for price updates
- Configured connection for BTC, ETH, and top 50 coins

Closes #12
```

### 5. Stage and commit

```bash
git add .
git commit -m "<generated-message>"
```

### 6. Verify branch again before push

```bash
CURRENT_BRANCH=$(git branch --show-current)
```

Double-check the branch is NOT protected. Then push:

```bash
git push origin "$CURRENT_BRANCH"
```

### 7. Post-push verification

- Confirm push success.
- Display commit hash, branch name, and GitHub URL.
- Suggest creating a PR if one doesn't exist yet.

## Safety rules

- **Never** push to `main`, `master`, `dev`, `develop`, `production`, or `release/*` — even if the user asks.
- **Never** commit secrets or sensitive files.
- **Never** force push unless the user explicitly requests it and confirms the risk.
