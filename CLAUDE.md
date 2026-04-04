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

## Build

- Desktop compile check: `./gradlew composeApp:compileKotlinDesktop`
- Android: `./gradlew androidApp:assembleDebug`
