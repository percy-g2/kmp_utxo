---
name: create-pr
description: Create a GitHub Pull Request to merge the current branch into main with a well-structured title and description using GitHub CLI. Use when the user asks to create a PR, open a pull request, submit a merge request, or send changes for review.
---

# Create PR

Create a GitHub Pull Request from the current feature branch into `main`.

## Prerequisites

```bash
gh --version        # GitHub CLI installed
gh auth status      # Authenticated
git status          # All changes committed and pushed
```

### Branch protection check

```bash
CURRENT_BRANCH=$(git branch --show-current)
```

If `$CURRENT_BRANCH` is `main`, `master`, `dev`, `develop`, or any protected branch:

- **ABORT** — do not create a PR from a protected branch.
- Inform the user and suggest using the push-code skill to create a feature branch first.

## Workflow

### 1. Gather PR information

```bash
git log main..HEAD --oneline          # Commits since branching
git diff main...HEAD --name-only      # Changed files
git diff main...HEAD --stat           # Change summary
```

### 2. Generate PR title

Format: `[<TYPE>] <Brief description>`

Examples:
- `[FEATURE] Add Binance WebSocket integration for real-time crypto prices`
- `[FIX] Resolve memory leak in RSS feed parser`
- `[REFACTOR] Restructure settings screen with improved UX`

### 3. Generate PR description

Use this template:

```markdown
## Description
[Clear explanation of what this PR does and why]

## Changes Made
- [Bullet point list of all changes]
- [Include file/module names]
- [Mention new dependencies if any]

## Type of Change
- [ ] New feature
- [ ] Bug fix
- [ ] Refactoring
- [ ] Documentation update
- [ ] Performance improvement

## Testing Done
- [ ] Unit tests added/updated
- [ ] Manual testing on Android
- [ ] Manual testing on iOS (if applicable)
- [ ] Manual testing on Desktop (if applicable)

## Checklist
- [ ] Code follows project style guidelines
- [ ] Tests pass locally
- [ ] No breaking changes (or documented if present)

## Related Issues
Closes #[issue-number]
```

### 4. Create the PR

```bash
gh pr create \
  --base main \
  --head "$CURRENT_BRANCH" \
  --title "<generated-title>" \
  --body "<generated-description>" \
  --assignee @me
```

Add `--label`, `--reviewer` flags as appropriate based on context.

### 5. Post-creation

- Display the PR URL and number.
- List any CI checks that will run.
- Suggest next steps (wait for CI, request reviews).

## Safety rules

- **Never** create a PR from a protected branch.
- **Never** commit secrets or sensitive files.
