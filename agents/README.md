# PR Review Agent

AI-powered code review agent for Pull Requests and Merge Requests. Analyzes code changes with KMP/CMP-specific checks and provides intelligent review feedback.

**All reviews follow the rules defined in [`rules/pr-review.md`](rules/pr-review.md)** - the AI agent rulebook that ensures consistent, high-quality code reviews.

## Features

- 🔍 **Multi-Provider Support**: Works with GitHub, GitLab, and Bitbucket
- 🎯 **KMP-Specific Analysis**: Detects expect/actual mismatches, platform leaks, and common dependency violations
- 🛡️ **Security Scanning**: Identifies potential security issues like hardcoded secrets
- 📊 **Code Quality Checks**: Analyzes complexity, duplication, and style issues
- 🤖 **AI-Powered Reviews**: Generates intelligent, contextual review comments
- 📖 **Rulebook-Driven**: Follows comprehensive rules defined in `rules/pr-review.md`

## Rulebook

The agent follows a comprehensive rulebook located at `agents/rules/pr-review.md`. This rulebook defines:

- **KMP/CMP Architecture Rules**: Expect/actual patterns, platform leaks, dependency management
- **Code Quality Standards**: Function complexity, duplication, naming conventions
- **Security Guidelines**: Hardcoded secrets, insecure APIs
- **Testing Requirements**: Test coverage, test organization
- **Documentation Standards**: Public API docs, README updates
- **Review Severity Levels**: CRITICAL, WARNING, SUGGESTION, INFO

**Customize reviews by editing the rulebook** - all AI agents will follow your team's standards.

## Installation

```bash
cd agents
./gradlew installDist
```

The agent will be available at `build/install/agents/bin/agents`

## Usage

### Basic Usage

Review a PR by URL:
```bash
pr-review https://github.com/owner/repo/pull/123
```

Review a PR by number (uses repository from git config):
```bash
pr-review 123
```

### With Authentication

For private repositories, set the appropriate token:
```bash
GITHUB_TOKEN=your_token_here pr-review 123
```

### Examples

```bash
# Review GitHub PR
pr-review https://github.com/percy-g2/kmp_utxo/pull/42

# Review PR number (auto-detects repository)
pr-review 42

# Review with GitHub token
GITHUB_TOKEN=ghp_xxxxx pr-review 42
```

## Output

The agent provides:

1. **Console Output**: Summary with critical issues, warnings, and suggestions
2. **Detailed Report**: Markdown report saved to `.cmp-agents/pr-review-{number}.md`

### Review Categories

- **KMP Architecture**: Expect/actual patterns, platform leaks, dependency violations
- **Code Quality**: Complexity, duplication, naming, style
- **Security**: Hardcoded secrets, insecure APIs, permission issues
- **Testing**: Test coverage, missing tests
- **Documentation**: Missing or incomplete documentation

## Configuration

The agent automatically detects repository information from `.git/config`. For custom configuration, you can set environment variables:

- `GITHUB_TOKEN`: GitHub personal access token
- `GITLAB_TOKEN`: GitLab access token
- `BITBUCKET_TOKEN`: Bitbucket app password

## Customizing Review Rules

Edit `agents/rules/pr-review.md` to customize:

- Severity levels for different issue types
- Function complexity thresholds
- Test coverage requirements
- Platform-specific rules
- Custom patterns to check

The agent will automatically load and follow your custom rules.

## KMP-Specific Checks

The agent performs specialized checks for Kotlin Multiplatform projects:

1. **Expect/Actual Validation**: Ensures all expect declarations have corresponding actual implementations
2. **Platform Leak Detection**: Identifies platform-specific code in commonMain
3. **Dependency Analysis**: Verifies platform-specific dependencies are in correct source sets
4. **Architecture Validation**: Checks for proper separation of common and platform code

All checks follow the rules defined in the rulebook.

## Supported Providers

- ✅ **GitHub**: Full support
- 🚧 **GitLab**: Coming soon
- 🚧 **Bitbucket**: Coming soon

## Rulebook Structure

The rulebook (`rules/pr-review.md`) is organized into sections:

1. **KMP/CMP Architecture Rules** (Rules 1-4)
2. **Code Quality Standards** (Rules 5-8)
3. **Security Guidelines** (Rules 9-10)
4. **Testing Requirements** (Rules 11-12)
5. **Documentation Standards** (Rules 13-14)
6. **Review Severity Levels**
7. **Platform-Specific Rules**

Each rule includes:
- Severity level
- Description and requirements
- Example violations
- Correct patterns

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

When contributing:
1. Follow the existing code style
2. Update the rulebook if adding new checks
3. Add tests for new features
4. Update documentation

## License

MIT License - see LICENSE file for details
