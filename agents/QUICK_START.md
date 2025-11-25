# PR Review Agent - Quick Start

## What It Does

The PR Review Agent analyzes Pull Requests and Merge Requests to provide intelligent code review feedback, with special focus on Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) projects.

## Installation

```bash
cd agents
./gradlew installDist
```

## Usage

### Review by PR URL
```bash
./pr-review.sh https://github.com/percy-g2/kmp_utxo/pull/123
```

### Review by PR Number
```bash
./pr-review.sh 123
```
*(Automatically detects repository from git config)*

### With Authentication
```bash
GITHUB_TOKEN=your_token ./pr-review.sh 123
```

## Features

✅ **Multi-Provider Support**: GitHub (GitLab & Bitbucket coming soon)  
✅ **KMP-Specific Checks**: Expect/actual validation, platform leaks, dependency analysis  
✅ **Security Scanning**: Hardcoded secrets, insecure APIs  
✅ **Code Quality**: Complexity, duplication, style checks  
✅ **Detailed Reports**: Markdown reports saved to `.cmp-agents/`

## Example Output

```
================================================================================
PR Review Results
================================================================================

PR: #123 - Add new feature
Author: developer
URL: https://github.com/owner/repo/pull/123

Summary:
  Status: APPROVED_WITH_SUGGESTIONS
  Files Reviewed: 5
  Lines Added: 120
  Lines Removed: 45
  Total Comments: 8
  Critical Issues: 0
  Warnings: 2
  Suggestions: 6
  KMP Issues: 1

Affected Platforms: Android, iOS, Desktop

⚠️  WARNINGS:
--------------------------------------------------------------------------------
File: src/commonMain/kotlin/api/ApiClient.kt
Category: KMP_ARCHITECTURE
Message: Platform-specific import 'java.io' detected in commonMain
Suggestion: Move platform-specific code to expect/actual pattern

💡 SUGGESTIONS:
--------------------------------------------------------------------------------
File: src/commonMain/kotlin/ui/Screen.kt
Category: CODE_QUALITY
Message: Long function detected (~60 lines). Consider breaking it down.
Suggestion: Extract smaller functions for better readability and testability

📄 Detailed report saved to: .cmp-agents/pr-review-123.md
```

## What Gets Checked

### KMP Architecture
- Expect/actual pattern completeness
- Platform-specific code in commonMain
- Dependency placement in correct source sets
- Missing platform implementations

### Code Quality
- Function complexity
- Code duplication
- Naming conventions
- Style consistency

### Security
- Hardcoded secrets (API keys, passwords, tokens)
- Insecure API usage
- Permission issues

### Testing & Documentation
- Test file presence
- Documentation completeness

## Next Steps

See [README.md](README.md) for detailed documentation and [USAGE.md](USAGE.md) for advanced usage examples.

