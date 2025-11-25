# PR Review Agent Rulebook

This document defines the rules and guidelines that the PR Review Agent follows when analyzing code changes. All AI agents must adhere to these rules when performing code reviews.

## Table of Contents
1. [KMP/CMP Architecture Rules](#kmpcmp-architecture-rules)
2. [Code Quality Standards](#code-quality-standards)
3. [Security Guidelines](#security-guidelines)
4. [Testing Requirements](#testing-requirements)
5. [Documentation Standards](#documentation-standards)
6. [Review Severity Levels](#review-severity-levels)
7. [Platform-Specific Rules](#platform-specific-rules)

---

## KMP/CMP Architecture Rules

### Rule 1: Expect/Actual Pattern Validation
**Severity: CRITICAL**

- **MUST**: Every `expect` declaration in `commonMain` MUST have corresponding `actual` implementations in ALL platform source sets (androidMain, iosMain, desktopMain, webMain, wasmJsMain)
- **MUST NOT**: Have `expect` declarations without `actual` implementations
- **MUST**: Ensure `actual` implementations match the `expect` signature exactly
- **SHOULD**: Verify that platform-specific implementations are functionally equivalent

**Example Violation:**
```kotlin
// commonMain/Platform.kt
expect class Platform() {
    fun getPlatformName(): String
}

// Missing actual in iosMain - CRITICAL ISSUE
```

**Correct Pattern:**
```kotlin
// commonMain/Platform.kt
expect class Platform() {
    fun getPlatformName(): String
}

// androidMain/Platform.android.kt
actual class Platform {
    actual fun getPlatformName() = "Android"
}

// iosMain/Platform.ios.kt
actual class Platform {
    actual fun getPlatformName() = "iOS"
}
```

### Rule 2: Platform Leak Prevention
**Severity: CRITICAL**

- **MUST NOT**: Use platform-specific APIs directly in `commonMain`
- **MUST NOT**: Import platform-specific packages in `commonMain`:
  - `java.io.*`, `java.net.*` (JVM-specific)
  - `androidx.compose.ui.platform.*` (Android-specific)
  - `kotlinx.cinterop.*` (Native-specific)
  - `platform.Foundation.*`, `platform.UIKit.*` (iOS-specific)
- **MUST**: Use `expect/actual` pattern for platform-specific functionality
- **SHOULD**: Use multiplatform libraries (kotlinx.coroutines, kotlinx.serialization) instead of platform APIs

**Example Violation:**
```kotlin
// commonMain/FileManager.kt - CRITICAL ISSUE
import java.io.File  // Platform leak!

fun readFile(path: String): String {
    return File(path).readText()  // JVM-specific API
}
```

**Correct Pattern:**
```kotlin
// commonMain/FileManager.kt
expect class FileManager {
    fun readFile(path: String): String
}

// androidMain/FileManager.android.kt
actual class FileManager {
    actual fun readFile(path: String): String {
        return java.io.File(path).readText()
    }
}
```

### Rule 3: Dependency Management
**Severity: CRITICAL**

- **MUST NOT**: Place platform-specific dependencies in `commonMain` dependencies
- **MUST**: Place platform-specific dependencies in their respective source sets:
  - `io.ktor:ktor-client-android` → `androidMain`
  - `io.ktor:ktor-client-darwin` → `iosMain`
  - `io.ktor:ktor-client-cio` → `desktopMain`
  - `io.ktor:ktor-client-js` → `webMain`/`wasmJsMain`
- **MUST**: Use common dependencies (ktor-client-core, kotlinx libraries) in `commonMain`
- **SHOULD**: Verify dependency versions are consistent across platforms

**Example Violation:**
```kotlin
// build.gradle.kts - commonMain dependencies
dependencies {
    implementation("io.ktor:ktor-client-android:3.3.1")  // CRITICAL ISSUE
}
```

**Correct Pattern:**
```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.3.1")  // Common dependency
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-android:3.3.1")  // Platform-specific
        }
    }
}
```

### Rule 4: Source Set Organization
**Severity: WARNING**

- **MUST**: Keep common business logic in `commonMain`
- **MUST**: Keep platform-specific implementations in platform source sets
- **SHOULD**: Use `expect/actual` for platform abstractions
- **SHOULD NOT**: Duplicate code across platform source sets unnecessarily

---

## Code Quality Standards

### Rule 5: Function Complexity
**Severity: SUGGESTION**

- **SHOULD NOT**: Functions exceed 50 lines of code
- **SHOULD**: Break down complex functions into smaller, focused functions
- **SHOULD**: Use descriptive function names that indicate purpose
- **MAY**: Allow longer functions for data classes, sealed classes, or simple getters/setters

**Guidelines:**
- Functions with 30-50 lines: SUGGESTION to refactor
- Functions with 50+ lines: WARNING to refactor
- Functions with 100+ lines: CRITICAL - must refactor

### Rule 6: Code Duplication
**Severity: WARNING**

- **MUST NOT**: Duplicate code across platform source sets without using `expect/actual`
- **SHOULD**: Extract common logic to `commonMain`
- **SHOULD**: Use shared utilities and helper functions
- **MAY**: Allow platform-specific optimizations that require duplication

### Rule 7: Naming Conventions
**Severity: SUGGESTION**

- **MUST**: Follow Kotlin naming conventions:
  - Classes: PascalCase (`UserRepository`)
  - Functions/variables: camelCase (`getUserData`)
  - Constants: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
  - Packages: lowercase (`com.example.feature`)
- **SHOULD**: Use descriptive names that indicate purpose
- **SHOULD NOT**: Use abbreviations unless widely understood (`http`, `api`, `id`)

### Rule 8: Error Handling
**Severity: WARNING**

- **MUST**: Handle exceptions appropriately
- **SHOULD**: Use Result types or sealed classes for error handling
- **SHOULD NOT**: Swallow exceptions silently
- **SHOULD**: Provide meaningful error messages

---

## Security Guidelines

### Rule 9: Hardcoded Secrets
**Severity: CRITICAL**

- **MUST NOT**: Hardcode API keys, passwords, tokens, or secrets in source code
- **MUST**: Use environment variables or secure configuration management
- **MUST**: Use build configuration files (local.properties, .env) excluded from version control
- **SHOULD**: Use platform-specific secure storage (KeyStore, Keychain)

**Patterns to Flag:**
- `api[_-]?key\s*[=:]\s*["'][^"']+`
- `password\s*[=:]\s*["'][^"']+`
- `secret\s*[=:]\s*["'][^"']+`
- `token\s*[=:]\s*["'][^"']+`

**Example Violation:**
```kotlin
// CRITICAL SECURITY ISSUE
val apiKey = "sk_live_1234567890abcdef"
```

**Correct Pattern:**
```kotlin
val apiKey = System.getenv("API_KEY") ?: throw IllegalStateException("API_KEY not set")
```

### Rule 10: Insecure APIs
**Severity: WARNING**

- **SHOULD NOT**: Use deprecated or insecure APIs
- **SHOULD**: Use HTTPS for network requests
- **SHOULD**: Validate and sanitize user input
- **SHOULD**: Use secure storage for sensitive data

---

## Testing Requirements

### Rule 11: Test Coverage
**Severity: SUGGESTION**

- **SHOULD**: Include tests for new features and bug fixes
- **SHOULD**: Test both common and platform-specific code
- **SHOULD**: Use `commonTest` for shared test logic
- **MAY**: Require tests for critical business logic (configurable per project)

**Guidelines:**
- New features with 3+ files changed: SUGGESTION to add tests
- Bug fixes: SUGGESTION to add regression tests
- Public APIs: WARNING if no tests present

### Rule 12: Test Organization
**Severity: INFO**

- **SHOULD**: Mirror source structure in test directories
- **SHOULD**: Use descriptive test names (`testUserLoginWithValidCredentials`)
- **SHOULD**: Group related tests in test classes

---

## Documentation Standards

### Rule 13: Public API Documentation
**Severity: SUGGESTION**

- **SHOULD**: Document public APIs, classes, and functions
- **SHOULD**: Use KDoc format for documentation
- **SHOULD**: Include parameter descriptions and return value documentation
- **MAY**: Require documentation for complex algorithms or business logic

**Example:**
```kotlin
/**
 * Fetches user data from the remote API.
 *
 * @param userId The unique identifier of the user
 * @return [Result] containing [User] on success or [ApiError] on failure
 * @throws [NetworkException] if network request fails
 */
suspend fun fetchUser(userId: String): Result<User, ApiError>
```

### Rule 14: README and Changelog
**Severity: INFO**

- **SHOULD**: Update README if adding new features or changing setup
- **SHOULD**: Update CHANGELOG for user-facing changes
- **MAY**: Require documentation updates for significant changes

---

## Review Severity Levels

### CRITICAL
Issues that **MUST** be fixed before merge:
- Security vulnerabilities
- KMP architecture violations (expect/actual mismatches, platform leaks)
- Build-breaking changes
- Critical bugs

### WARNING
Issues that **SHOULD** be addressed:
- Code quality problems
- Potential bugs
- Performance concerns
- Architecture improvements

### SUGGESTION
Issues that **MAY** be addressed:
- Code style improvements
- Refactoring opportunities
- Documentation additions
- Test coverage improvements

### INFO
Informational comments:
- Best practices
- Platform-specific notes
- Testing reminders

---

## Platform-Specific Rules

### Android
- **MUST**: Follow Android best practices (lifecycle awareness, memory management)
- **SHOULD**: Use Compose for UI (if using Compose Multiplatform)
- **SHOULD**: Handle configuration changes appropriately

### iOS
- **MUST**: Follow iOS design guidelines (if applicable)
- **SHOULD**: Use SwiftUI integration properly (if using Compose Multiplatform)
- **SHOULD**: Handle memory management correctly

### Desktop
- **SHOULD**: Consider window management and user experience
- **SHOULD**: Handle file system permissions appropriately

### Web/WASM
- **SHOULD**: Consider bundle size and performance
- **SHOULD**: Handle browser compatibility
- **SHOULD**: Optimize for web-specific constraints

---

## Review Comment Format

All review comments MUST follow this structure:

1. **File Path**: Exact file path relative to repository root
2. **Line Number**: Specific line (if applicable)
3. **Severity**: CRITICAL, WARNING, SUGGESTION, or INFO
4. **Category**: KMP_ARCHITECTURE, CODE_QUALITY, SECURITY, TESTING, DOCUMENTATION, STYLE, PERFORMANCE
5. **Message**: Clear description of the issue
6. **Suggestion**: Actionable recommendation (if applicable)
7. **Code Snippet**: Relevant code excerpt (if helpful)

---

## Customization

Teams can customize these rules by:
1. Modifying this rulebook file
2. Adding project-specific rules
3. Adjusting severity levels
4. Adding custom patterns to check

---

## Enforcement

- **CRITICAL** issues: Block merge (configurable)
- **WARNING** issues: Require acknowledgment (configurable)
- **SUGGESTION** issues: Informational only
- **INFO** issues: Informational only

---

**Last Updated**: 2024
**Version**: 1.0.0

