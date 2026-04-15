---
name: review-pr
description: Review a GitHub Pull Request for code quality, architecture compliance, and project conventions using GitHub CLI. Approve if good, or request changes with specific feedback. Use when the user asks to review a PR, check a pull request, or approve a PR.
---

# Review PR

Review a Pull Request and submit an approval, request-changes, or comment via GitHub CLI.

## Input

Accept either:
- PR number: `123`
- PR URL: `https://github.com/owner/repo/pull/123`
- "current branch PR" — auto-detect with `gh pr view`

## Workflow

### 1. Fetch PR details

```bash
gh pr view <pr> --json number,title,body,author,state,commits,files,reviews,reviewDecision,mergeable,mergeStateStatus,isDraft
gh pr diff <pr>
gh pr checks <pr>
```

### 2. Analyze the diff

Review each changed file against these categories:

**Code Quality**
- Kotlin idioms and null safety
- Coroutine usage and scope management (rethrow `CancellationException`)
- Memory leak potential (uncancelled jobs, unclosed clients)
- Performance (unnecessary allocations, recomposition triggers)

**Architecture**
- MVVM pattern adherence
- Separation of concerns (ViewModel vs UI vs network)
- Proper use of `StateFlow`, `combine`, `stateIn`

**Compose Multiplatform**
- Platform `expect`/`actual` correctness
- No hardcoded colors or strings
- Recomposition optimization (`remember`, `derivedStateOf`, stable keys)
- Lifecycle-aware work (pause/resume in `DisposableEffect`)

**Testing**
- Test presence and quality
- Edge case coverage

### 3. Produce review output

```markdown
# PR Review: [Title]
**PR #**: [number] | **Author**: [username] | **Status**: [state]

## Summary
[High-level overview]

## Strengths
- [What was done well]

## Issues Found
### Critical
- [Security, crashes, data loss]
### Major
- [Logic errors, performance, bad patterns]
### Minor
- [Style, naming, docs]

## Suggestions
- [Improvements and alternatives]

## Testing Recommendations
- [Additional tests to add]

## Overall Assessment
**Approve** / **Request Changes** / **Comment**
[Reasoning]
```

### 4. Submit review

**Approve** if ALL of the following are true:
- No critical or major issues
- Code follows project conventions
- Tests pass / CI green
- PR is mergeable

**Request Changes** if ANY of the following are true:
- Critical or major issues found
- Tests failing or missing critical coverage
- Breaking changes undocumented
- Merge conflicts present

**Comment** if only minor issues or questions remain.

```bash
# Approve
gh pr review <pr> --approve -b "<summary>"

# Request changes
gh pr review <pr> --request-changes -b "<summary>"

# Comment only
gh pr review <pr> --comment -b "<summary>"
```

Also post the full review as a PR comment:

```bash
gh pr comment <pr> --body "<full-review-markdown>"
```

### 5. Post-review

- Confirm the review was submitted.
- Display the review URL and verdict.
- If approved, suggest using the merge-pr skill.

## Safety rules

- If the diff is very large (>3000 lines changed), warn the user and offer to review specific files only.
- Never approve a PR with known critical issues.
