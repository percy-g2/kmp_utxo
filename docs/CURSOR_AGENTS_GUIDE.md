# Cursor AI Agents — Quick Start

This project uses Cursor skills for automated git workflows. Say the trigger phrase and the agent handles the rest.

## Available Skills

| Skill | What it does | Trigger phrases |
|---|---|---|
| **push-code** | Stage, commit, push with conventional commit message | "push my code", "commit and push" |
| **create-pr** | Create a GitHub PR with generated title and description | "create a PR", "open pull request" |
| **review-pr** | Review a PR for quality and submit verdict | "review PR #42", "check this PR" |
| **merge-pr** | Merge an approved PR after validation | "merge PR #42", "land this branch" |
| **pcr** | Full pipeline: push + create PR + review | "pcr", "push commit review" |

## Example Workflows

### Ship a feature

```
push my code
create a PR for this feature
review the PR I just created
merge PR #42
```

### One-shot pipeline

```
pcr
```

This pushes your changes, creates a PR, and reviews it in one go.

### With details

```
push my code with type feat and scope websocket
create a PR titled "Add Binance WebSocket integration" with label feature
review PR #42 focusing on memory leaks and coroutine usage
merge PR #42 with squash strategy
```

## Prerequisites

- GitHub CLI installed and authenticated: `gh auth status`
- Working on a feature branch (not `main`, `master`, or `dev`)
- For PR creation: changes committed and pushed

## File Locations

| File | Purpose |
|---|---|
| `.cursor/rules/*.mdc` | Coding conventions (auto-loaded by file pattern) |
| `.cursor/skills/*/SKILL.md` | Workflow skill definitions |
| `.claude/skills/*/SKILL.md` | Same skills for Claude Code |
| `AGENTS.md` | Full agent reference (tech stack, architecture) |
| `CLAUDE.md` | Condensed project rules for Claude Code |
