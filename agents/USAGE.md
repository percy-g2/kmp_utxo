# PR Review Agent - Usage Guide

## Quick Start

### 1. Build the Agent

```bash
cd agents
./gradlew installDist
```

### 2. Run a Review

**Option A: Using the wrapper script**
```bash
./pr-review.sh https://github.com/percy-g2/kmp_utxo/pull/123
```

**Option B: Direct execution**
```bash
build/install/agents/bin/agents https://github.com/percy-g2/kmp_utxo/pull/123
```

**Option C: Using PR number (auto-detects repository)**
```bash
./pr-review.sh 123
```

## Examples

### Review a GitHub PR by URL
```bash
./pr-review.sh https://github.com/owner/repo/pull/42
```

### Review a PR by number
The agent will automatically detect the repository from your `.git/config`:
```bash
./pr-review.sh 42
```

### Review with authentication (for private repos)
```bash
GITHUB_TOKEN=ghp_your_token_here ./pr-review.sh 42
```

## Input Formats Supported

### GitHub
- Full URL: `https://github.com/owner/repo/pull/123`
- PR Number: `123` (requires repository detection from git config)

### GitLab (coming soon)
- Full URL: `https://gitlab.com/owner/repo/-/merge_requests/123`

### Bitbucket (coming soon)
- Full URL: `https://bitbucket.org/owner/repo/pull-requests/123`

## Output

The agent provides:

1. **Console Output**: 
   - Summary statistics
   - Critical issues
   - Warnings
   - Suggestions

2. **Detailed Report**: 
   - Saved to `.cmp-agents/pr-review-{number}.md`
   - Contains all review comments with full context

## What Gets Analyzed

### KMP-Specific Checks
- ✅ Expect/actual pattern validation
- ✅ Platform leak detection (platform-specific code in commonMain)
- ✅ Common dependency violations
- ✅ Missing platform implementations

### Code Quality
- ✅ Function complexity
- ✅ Code duplication patterns
- ✅ Naming conventions
- ✅ Style consistency

### Security
- ✅ Hardcoded secrets detection
- ✅ Insecure API usage
- ✅ Permission issues

### Testing & Documentation
- ✅ Test coverage analysis
- ✅ Missing test files
- ✅ Documentation completeness

## Environment Variables

- `GITHUB_TOKEN`: GitHub personal access token (for private repos or rate limit)
- `GITLAB_TOKEN`: GitLab access token (coming soon)
- `BITBUCKET_TOKEN`: Bitbucket app password (coming soon)

## Troubleshooting

### "Repository information required"
If you see this error when using a PR number, the agent couldn't detect your repository. Solutions:
1. Use the full PR URL instead
2. Ensure you're in a git repository with a configured origin remote
3. Manually provide repository info (coming soon)

### "Failed to fetch PR details"
Possible causes:
1. PR doesn't exist or is private (use GITHUB_TOKEN)
2. Network connectivity issues
3. Rate limiting (use GITHUB_TOKEN to increase limits)

### "Unsupported PR/MR URL format"
The URL format isn't recognized. Ensure you're using one of the supported formats:
- GitHub: `https://github.com/owner/repo/pull/123`
- GitLab: `https://gitlab.com/owner/repo/-/merge_requests/123`
- Bitbucket: `https://bitbucket.org/owner/repo/pull-requests/123`

## Integration Examples

### GitHub Actions
```yaml
- name: Review PR
  run: |
    cd agents
    ./gradlew installDist
    GITHUB_TOKEN=${{ secrets.GITHUB_TOKEN }} ./pr-review.sh ${{ github.event.pull_request.number }}
```

### Pre-commit Hook
```bash
#!/bin/bash
# .git/hooks/pre-push
./agents/pr-review.sh $(git rev-parse --abbrev-ref HEAD | grep -o '[0-9]*')
```

## Next Steps

- Add more providers (GitLab, Bitbucket)
- Integrate with AI models for smarter reviews
- Add inline comment posting to PRs
- Support for custom review rules
- Integration with CI/CD pipelines

