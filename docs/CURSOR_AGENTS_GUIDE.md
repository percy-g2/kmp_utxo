# Cursor AI Agents Guide for Developers

This guide explains how to use the Cursor AI agents in this project with simple, single-command prompts.

## Quick Start

The project includes three specialized agents that automate common development workflows:
- **Git Commit & Push Agent** - Automated commits with conventional commit messages
- **GitHub PR Creation Agent** - Create PRs with GitHub CLI
- **GitHub PR Review Agent** - Comprehensive code reviews

## Prerequisites

Before using the agents, ensure you have:
- ✅ GitHub CLI installed (`gh --version`)
- ✅ GitHub CLI authenticated (`gh auth status`)
- ✅ Working on a feature branch (not `main` or `master`)
- ✅ Changes committed (for PR creation)

---

## Git Commit & Push Agent

### Purpose
Automatically commit and push your changes with a properly formatted conventional commit message.

### Single Command Usage

Simply tell Cursor to commit your changes:

```
Use Git Commit & Push Agent to commit and push my changes
```

Or be more specific:

```
Use Git Commit & Push Agent: Commit and push my changes with type "feat" and scope "websocket"
```

### What It Does
1. ✅ Checks current branch (aborts if on main/master)
2. ✅ Runs code quality checks (`./gradlew build`, `./gradlew test`, `./gradlew ktlintCheck`)
3. ✅ Generates conventional commit message
4. ✅ Commits and pushes to remote

### Example Commands

**Basic commit:**
```
Commit my changes using Git Commit & Push Agent
```

**Feature commit:**
```
Use Git Commit & Push Agent to commit this feature: Add Binance WebSocket connection
```

**Fix commit:**
```
Git Commit & Push Agent: Commit fix for memory leak in RSS parser
```

**With issue reference:**
```
Use Git Commit & Push Agent to commit and reference issue #42
```

### Commit Message Format
The agent automatically generates messages in this format:
```
<type>(<scope>): <subject>

<body>

Closes #<issue-number>
```

**Types**: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`

---

## GitHub PR Creation Agent

### Purpose
Create a GitHub Pull Request with comprehensive description and proper formatting.

### Single Command Usage

**Basic PR creation:**
```
Use GitHub PR Creation Agent to create a pull request
```

**With specific title:**
```
Use GitHub PR Creation Agent: Create PR titled "Add RSS news feed feature"
```

**With labels:**
```
GitHub PR Creation Agent: Create PR with label "feature" and assign to me
```

### What It Does
1. ✅ Verifies GitHub CLI setup
2. ✅ Gathers commit history and changed files
3. ✅ Generates comprehensive PR description
4. ✅ Creates PR with proper title, body, labels, and assignees
5. ✅ Provides PR URL and next steps

### Example Commands

**Standard PR:**
```
Create a pull request using GitHub PR Creation Agent
```

**Feature PR:**
```
Use GitHub PR Creation Agent to create PR for the WebSocket integration feature
```

**Fix PR:**
```
GitHub PR Creation Agent: Create PR for bug fix, label it as "bug"
```

**With reviewers:**
```
Use GitHub PR Creation Agent to create PR and request review from @username
```

### PR Description Includes
- ✅ Clear description of changes
- ✅ List of modified files
- ✅ Testing checklist
- ✅ Type of change indicators
- ✅ Related issues

---

## GitHub PR Review Agent

### Purpose
Review a Pull Request and provide comprehensive code analysis and feedback.

### Single Command Usage

**Review by PR number:**
```
Use GitHub PR Review Agent to review PR #42
```

**Review by URL:**
```
GitHub PR Review Agent: Review this PR https://github.com/percy-g2/kmp_utxo/pull/42
```

**Review current branch:**
```
Use GitHub PR Review Agent to review the current branch's PR
```

### What It Does
1. ✅ Fetches PR details and changed files
2. ✅ Analyzes code quality, architecture, and best practices
3. ✅ Checks for Kotlin/Compose-specific issues
4. ✅ Reviews test coverage
5. ✅ Provides actionable feedback
6. ✅ Optionally approves or requests changes

### Example Commands

**Basic review:**
```
Review PR #45 using GitHub PR Review Agent
```

**Detailed review:**
```
GitHub PR Review Agent: Do a comprehensive review of PR #45, check for memory leaks and performance issues
```

**Review with approval:**
```
Use GitHub PR Review Agent to review and approve PR #45 if it looks good
```

**Review specific aspects:**
```
GitHub PR Review Agent: Review PR #45 focusing on architecture and testing coverage
```

### Review Output Includes
- ✅ Summary of changes
- ✅ Strengths and good practices
- ✅ Critical, major, and minor issues
- ✅ Suggestions for improvement
- ✅ Testing recommendations
- ✅ Overall assessment (Approve/Request Changes/Comment)

---

## Combined Workflows

### Complete Feature Workflow

**1. After implementing a feature:**
```
Use Git Commit & Push Agent to commit and push my changes
```

**2. Create PR:**
```
Use GitHub PR Creation Agent to create a pull request for this feature
```

**3. Self-review:**
```
Use GitHub PR Review Agent to review the PR I just created
```

### Quick Fix Workflow

**1. Commit fix:**
```
Git Commit & Push Agent: Commit this bug fix
```

**2. Create PR:**
```
GitHub PR Creation Agent: Create PR for bug fix, label as "bug"
```

---

## Advanced Usage

### Custom Commit Messages

You can guide the agent with specific details:

```
Use Git Commit & Push Agent to commit with:
- Type: feat
- Scope: websocket
- Subject: Implement Binance WebSocket connection
- Body: Added WebSocketManager class with retry logic
- Closes: #12
```

### PR with Custom Details

```
Use GitHub PR Creation Agent to create PR with:
- Title: [FEATURE] Add RSS news feed
- Labels: feature, enhancement
- Assignees: @me
- Reviewers: @username1, @username2
```

### Targeted Code Review

```
GitHub PR Review Agent: Review PR #42 focusing on:
- Memory leaks
- Coroutine usage
- Compose recomposition
- Test coverage
```

---

## Troubleshooting

### Git Commit & Push Agent Fails

**If build fails:**
```
Git Commit & Push Agent: Commit anyway, skip tests (build is failing due to unrelated issue)
```

**If on main branch:**
The agent will automatically abort. Switch to a feature branch first:
```bash
git checkout -b feature/my-feature
```

### GitHub PR Creation Agent Fails

**If not authenticated:**
```
GitHub PR Creation Agent: Check GitHub CLI authentication first
```

**If PR already exists:**
```
GitHub PR Creation Agent: Check if PR already exists, if so, update it instead
```

### GitHub PR Review Agent Fails

**If PR not found:**
```
GitHub PR Review Agent: List all open PRs first, then review PR #X
```

**If you want to checkout PR locally:**
```
GitHub PR Review Agent: Review PR #42 and checkout the branch for local testing
```

---

## Tips for Best Results

1. **Be Specific**: The more context you provide, the better the results
   - ✅ "Use Git Commit & Push Agent to commit this WebSocket feature"
   - ❌ "Commit"

2. **Use Natural Language**: The agents understand context
   - ✅ "Create a PR for the bug fix I just committed"
   - ✅ "Review the latest PR"

3. **Chain Commands**: You can combine workflows
   - ✅ "Use Git Commit & Push Agent to commit, then GitHub PR Creation Agent to create PR"

4. **Reference Issues**: Always mention related issues
   - ✅ "Use Git Commit & Push Agent to commit, closes #42"

---

## Command Cheat Sheet

| Task | Command |
|------|---------|
| Commit changes | `Use Git Commit & Push Agent to commit my changes` |
| Create PR | `Use GitHub PR Creation Agent to create a pull request` |
| Review PR | `Use GitHub PR Review Agent to review PR #42` |
| Commit + PR | `Use Git Commit & Push Agent to commit, then GitHub PR Creation Agent to create PR` |
| Full workflow | `Use Git Commit & Push Agent to commit, GitHub PR Creation Agent for PR, then GitHub PR Review Agent to review` |

---

## Need More Details?

For complete agent specifications and advanced configurations, see:
- [`../AGENT_RULES.md`](../AGENT_RULES.md) - Complete agent rules and specifications
- [`.cursorrules`](../.cursorrules) - Quick reference

---

## Examples from Real Workflows

### Example 1: Adding a New Feature

**Developer:** "I've implemented the RSS news feed. Use Git Commit & Push Agent to commit this."

**Git Commit & Push Agent Response:**
- ✅ Checks branch (feature/rss-feed)
- ✅ Runs tests (all pass)
- ✅ Generates commit: `feat(news-feed): implement RSS news feed parser`
- ✅ Pushes to remote

**Developer:** "Now create a PR using GitHub PR Creation Agent"

**GitHub PR Creation Agent Response:**
- ✅ Creates PR with comprehensive description
- ✅ Adds labels: `feature`
- ✅ Assigns to developer
- ✅ Provides PR URL

### Example 2: Bug Fix

**Developer:** "Git Commit & Push Agent: Commit this memory leak fix"

**Git Commit & Push Agent:**
- ✅ Commit: `fix(memory): resolve memory leak in WebSocketManager`
- ✅ Pushes changes

**Developer:** "GitHub PR Creation Agent: Create PR for this fix"

**GitHub PR Creation Agent:**
- ✅ Creates PR with bug label
- ✅ Links to issue #45

---

## Support

If an agent doesn't work as expected:
1. Check the prerequisites (GitHub CLI, branch, etc.)
2. Review the error message
3. Try being more specific in your command
4. Refer to [`AGENT_RULES.md`](../AGENT_RULES.md) for detailed specifications
