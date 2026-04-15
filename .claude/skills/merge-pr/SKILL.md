---
name: merge-pr
description: Merge an approved GitHub Pull Request after validating CI checks, review status, and mergeability. Use when the user asks to merge a PR, complete a pull request, or land a branch.
---

# Merge PR

Merge an approved Pull Request after validating all merge criteria.

## Input

Accept either:
- PR number: `152`
- PR URL: `https://github.com/owner/repo/pull/152`
- "current branch PR" — auto-detect with `gh pr view`

## Workflow

### 1. Pre-merge validation

```bash
gh pr view <pr> --json number,title,state,mergeable,mergeStateStatus,reviews,reviewDecision,statusCheckRollup,isDraft
gh pr checks <pr>
```

Verify ALL of the following:
- PR state is `OPEN`
- PR is not a draft
- PR is mergeable (no conflicts)
- At least one approval exists (no blocking request-changes reviews)
- All required CI checks pass

If any check fails, report the specific blocker and stop.

### 2. Select merge strategy

```bash
gh repo view --json mergeCommitAllowed,squashMergeAllowed,rebaseMergeAllowed
```

**Preferred order:**

| Strategy | When to use | Flag |
|---|---|---|
| Squash and merge | Feature branches (default) | `--squash` |
| Merge commit | Important features with meaningful commit history | `--merge` |
| Rebase and merge | Linear history preference | `--rebase` |

Use whatever the user requests, or default to squash.

### 3. Merge

```bash
gh pr merge <pr> --squash --delete-branch
```

For custom messages:

```bash
gh pr merge <pr> --squash --delete-branch \
  --subject "<PR title>" \
  --body "<PR description summary>"
```

### 4. Post-merge cleanup

```bash
# Verify merge
gh pr view <pr> --json state,mergedAt,mergedBy

# Local cleanup
git checkout main && git pull
git branch -d <branch-name>
git remote prune origin
```

### 5. Report

- Confirm merge success with commit hash.
- Display merged PR URL.
- Provide revert instructions: `git revert <commit-hash>`.

## Error handling

| Problem | Action |
|---|---|
| Merge conflicts | Inform user, suggest resolving conflicts first |
| CI checks failing | List failing checks, suggest fixing before merge |
| Missing approval | Suggest using review-pr skill first |
| Branch protection blocks | Report protection requirements, suggest admin override |

## Safety rules

- **Never** merge a PR with failing required checks unless the user explicitly overrides.
- **Never** merge a draft PR.
- Always confirm the merge strategy with the user if uncertain.
