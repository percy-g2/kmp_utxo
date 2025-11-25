# GitHub Integration Guide

The PR Review Agent now includes full GitHub integration using GitHub CLI for authentication and PR management.

## Features

✅ **GitHub CLI Authentication** - Secure authentication using `gh auth`  
✅ **Create Pull Requests** - Automatically create PRs with auto-generated descriptions  
✅ **Push Changes** - Stage, commit, and push changes to GitHub  
✅ **Review PRs** - Review existing PRs with rulebook-driven analysis  

## Setup

### 1. Install GitHub CLI

```bash
# macOS
brew install gh

# Linux
# See: https://github.com/cli/cli/blob/trunk/docs/install_linux.md

# Windows
# See: https://github.com/cli/cli/blob/trunk/docs/install_windows.md
```

### 2. Authenticate

```bash
# Authenticate with GitHub CLI
pr-review auth

# Or manually
gh auth login
```

This will open your browser for authentication. The agent will use your GitHub CLI credentials automatically.

## Usage

### Review a PR

```bash
# Review by URL
pr-review review https://github.com/percy-g2/kmp_utxo/pull/142

# Review by number (auto-detects repository)
pr-review review 142

# Backward compatible (without 'review' command)
pr-review https://github.com/percy-g2/kmp_utxo/pull/142
```

### Create a Pull Request

```bash
# Create PR from current branch (auto-generated title and description)
pr-review create-pr

# Create PR with custom title
pr-review create-pr --title "Fix bug in API client"

# Create PR with custom title and body
pr-review create-pr --title "Add new feature" --body "This PR adds..."

# Create draft PR
pr-review create-pr --title "WIP: New feature" --draft

# Create PR from specific branch
pr-review create-pr --title "Update deps" --base main --head feature-branch
```

### Push Changes

```bash
# Stage, commit, and push all changes
pr-review push --message "Fix bug"

# Push to specific branch
pr-review push --message "Update code" --branch feature-branch

# Push specific files only
pr-review push --message "Update config" --files "build.gradle.kts,settings.gradle.kts"

# Force push (use with caution!)
pr-review push --message "Rebase" --force
```

## Workflow Examples

### Complete Workflow: Make Changes → Push → Create PR

```bash
# 1. Make your changes
# ... edit files ...

# 2. Push changes to GitHub
pr-review push --message "Add new feature"

# 3. Create PR
pr-review create-pr --title "Add new feature" --draft

# 4. Review the PR
pr-review review <PR_NUMBER>
```

### Automated PR Creation

```bash
# Create a branch, make changes, and create PR
git checkout -b feature/new-feature
# ... make changes ...
pr-review push --message "Implement new feature"
pr-review create-pr --title "New Feature" --base main
```

## GitHub CLI Commands Used

The agent uses these GitHub CLI commands under the hood:

- `gh auth status` - Check authentication status
- `gh auth login` - Authenticate user
- `gh auth token` - Get authentication token
- `gh api user` - Get user information
- `gh repo view` - Get repository information
- `gh pr create` - Create pull request

## Authentication

The agent automatically uses GitHub CLI authentication. No need to set `GITHUB_TOKEN` environment variable when using GitHub CLI.

### Check Authentication Status

```bash
gh auth status
```

### Re-authenticate

```bash
pr-review auth
# or
gh auth login
```

## Error Handling

### GitHub CLI Not Installed

```
Error: GitHub CLI (gh) is not installed.
Install it from: https://cli.github.com/
```

**Solution**: Install GitHub CLI using the instructions above.

### Not Authenticated

```
Error: Not authenticated with GitHub CLI.
Run: gh auth login
```

**Solution**: Run `pr-review auth` or `gh auth login`

### Branch Not Pushed

```
Error: Branch feature-branch does not exist locally
```

**Solution**: Ensure you're on the correct branch and it exists locally.

## Integration with CI/CD

You can use the agent in CI/CD pipelines:

```yaml
# GitHub Actions example
- name: Review PR
  run: |
    ./gradlew :agents:installDist
    build/install/agents/bin/agents review ${{ github.event.pull_request.number }}
  
- name: Create PR
  run: |
    ./gradlew :agents:installDist
    build/install/agents/bin/agents create-pr --title "Auto-update" --draft
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Security

- ✅ Uses GitHub CLI for secure authentication
- ✅ No tokens stored in code
- ✅ Respects GitHub CLI credentials
- ✅ Supports environment variables for CI/CD

## Troubleshooting

### "gh: command not found"
Install GitHub CLI from https://cli.github.com/

### "Authentication failed"
Run `gh auth login` to re-authenticate

### "Repository not found"
Ensure you're in a git repository with a GitHub remote configured

### "Branch does not exist"
Create the branch first: `git checkout -b branch-name`

## Next Steps

- Review PRs automatically in CI/CD
- Create PRs from automated changes
- Integrate with your development workflow
- Customize PR templates in the rulebook

