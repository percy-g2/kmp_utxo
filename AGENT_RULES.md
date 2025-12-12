# Cursor AI Agent Rules - Compose Multiplatform Crypto App

## Agent Overview

This project uses **5 specialized agents** for the development workflow:

1. **Git Commit & Push Agent** - Commits and pushes code changes
   - ✅ **Branch Protection**: MANDATORY - Auto-creates feature branch if on main/master
   
2. **GitHub PR Creation Agent** - Creates Pull Requests
   - ✅ **Branch Protection**: MANDATORY - Aborts if on main/master
   
3. **GitHub PR Review Agent** - Reviews Pull Requests
   - ℹ️ **Branch Protection**: N/A - Reviews any PR
   
4. **GitHub PR Merge Agent** - Merges approved Pull Requests
   - ℹ️ **Branch Protection**: N/A - Merges PRs (GitHub handles protection)
   
5. **Master Planning Agent** - Plans and executes features
   - ✅ **Branch Protection**: Uses Git Commit & Push Agent (inherits protection)

**⚠️ ALL agents MUST respect branch protection rules. Pushing to main/master is STRICTLY PROHIBITED.**

## Project Context
You are building a Compose Multiplatform (Kotlin) application with the following features:
- **Crypto Price List**: Real-time cryptocurrency prices from Binance WebSocket
- **News Feed**: RSS news feed for selected cryptocurrency
- **App Settings**: User preferences and configuration

## Core Principles
- Always plan before executing
- Break down tasks into logical steps
- Verify prerequisites before proceeding
- Provide detailed explanations for all changes
- Follow Kotlin and Compose best practices
- Ensure proper error handling and edge cases

## ⚠️ CRITICAL: Branch Protection Rules

**NEVER push directly to `main` or `master` branch. This is STRICTLY PROHIBITED.**

**MANDATORY Workflow:**
1. **ALWAYS** check current branch first: `git status` or `git branch`
2. **IF** on `main` or `master`: **MUST** create a feature branch first
3. **NEVER** commit or push to `main`/`master` directly
4. **ALWAYS** create PR from feature branch to main

**Violation Consequences:**
- If you push to main/master, you have violated a critical rule
- User will lose trust and may delete Cursor
- Always create a feature branch when on main/master

**Branch Protection Check MUST be the FIRST step in ANY git operation.**

---

## Git Commit & Push Agent

### Purpose
Push code changes to the current branch with comprehensive commit messages and proper validation. Automatically creates a new branch if attempting to commit on `main` or `master`.

### Branch Naming Rules

When creating a new branch, follow these naming conventions:

**Format**: `<type>/<short-description>`

**Types**:
- `feat/` - New features
- `fix/` - Bug fixes
- `refactor/` - Code refactoring
- `docs/` - Documentation updates
- `style/` - Code style changes (formatting, etc.)
- `test/` - Test additions or updates
- `chore/` - Build tasks, dependency updates, etc.
- `perf/` - Performance improvements

**Description Guidelines**:
- Use lowercase letters and hyphens (kebab-case)
- Keep it concise (2-4 words)
- Be descriptive but brief
- Examples:
  - `feat/i18n-strings` - Adding internationalization strings
  - `fix/websocket-reconnection` - Fixing WebSocket reconnection issue
  - `refactor/settings-screen` - Refactoring settings screen
  - `feat/dark-mode-toggle` - Adding dark mode toggle
  - `fix/memory-leak-news` - Fixing memory leak in news feed

**Branch Name Generation Process**:
1. Analyze changed files to determine the primary change type
2. Identify the main feature/area affected (scope)
3. Generate branch name: `<type>/<scope>-<description>`
4. If multiple changes detected, use the most significant one
5. Ensure branch name doesn't already exist (check with `git branch -a`)

### Workflow
1. **Pre-Commit Checks - MANDATORY FIRST STEP**
   - **CRITICAL: Branch Protection Check MUST be done FIRST**
   - Run `git status` to verify current branch and changed files
   - **Extract current branch name from git status output**
   - **Branch Protection Check (MANDATORY)**:
     - **IF current branch is `main` or `master`:**
       - **STOP immediately - DO NOT proceed with commit**
       - Analyze changes to determine appropriate branch name using branch naming rules
       - Check if branch already exists: `git branch -a | grep <branch-name>`
       - Create new branch: `git checkout -b <generated-branch-name>`
       - Inform user: "⚠️ On main branch detected. Created branch '<branch-name>' from main to protect main branch"
       - **Continue with workflow on new branch ONLY**
     - **IF current branch is NOT `main`/`master`:**
       - Proceed normally with commit workflow
   - Check for merge conflicts
   - Verify no untracked files that should be ignored

**⚠️ ERROR HANDLING:**
- If you detect you're on main/master AFTER committing: **ABORT IMMEDIATELY**
- Do NOT push to main/master under ANY circumstances
- If already pushed to main: Inform user immediately and provide rollback instructions

2. **Code Quality Validation**
   - Run Kotlin linter: `./gradlew ktlintCheck` (if available)
   - Run unit tests: `./gradlew test`
   - Check build: `./gradlew build`
   - If any checks fail, report errors and do not proceed

3. **Commit Message Structure**
   ```
   <type>(<scope>): <subject>
   
   <body>
   
   <footer>
   ```
   
   **Types**: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`
   
   **Scope examples**: `websocket`, `news-feed`, `settings`, `ui`, `api`, `navigation`

4. **Commit Message Guidelines**
   - **Subject line** (50 chars max): Imperative mood, capitalize first letter, no period
   - **Body**: Detailed explanation of WHAT changed and WHY
     - List all modified files/modules
     - Explain business logic changes
     - Document any breaking changes
     - Include implementation decisions
   - **Footer**: Reference issues, breaking changes, co-authors

5. **Example Commit**
   ```
   feat(websocket): implement Binance WebSocket connection for crypto prices
   
   - Added WebSocketManager class to handle Binance stream connections
   - Implemented retry logic with exponential backoff (max 5 attempts)
   - Created CryptoPriceModel data class for price updates
   - Added StateFlow to emit real-time price updates to UI
   - Configured connection to subscribe to BTC, ETH, and top 50 coins
   
   Technical details:
   - Using Ktor WebSocket client for cross-platform compatibility
   - Integrated kotlinx.serialization for JSON parsing
   - Implemented proper coroutine scoping for lifecycle management
   
   Closes #12
   ```

6. **Push Command**
   ```bash
   # VERIFY BRANCH AGAIN BEFORE PUSHING
   CURRENT_BRANCH=$(git branch --show-current)
   if [ "$CURRENT_BRANCH" = "main" ] || [ "$CURRENT_BRANCH" = "master" ]; then
     echo "ERROR: Cannot push to main/master branch"
     exit 1
   fi
   
   git add .
   git commit -m "<generated-message>"
   git push origin <current-branch>
   ```
   
   **⚠️ CRITICAL: Before executing `git push`, verify the branch name again**
   - Double-check: `git branch --show-current` or `git status`
   - If branch is `main` or `master`: **ABORT and create feature branch first**
   - Only push to feature branches

7. **Post-Push Verification**
   - Confirm push success
   - Display commit hash and branch name
   - Provide GitHub URL to view the commit

---

## GitHub PR Creation Agent

### Purpose
Create GitHub Pull Request using GitHub CLI with comprehensive description and automated checks.

### Prerequisites Check
- Verify GitHub CLI is installed: `gh --version`
- Verify authentication: `gh auth status`
- **CRITICAL: Branch Protection Check (MANDATORY FIRST STEP)**:
  - Run `git status` or `git branch --show-current` to get current branch name
  - **IF current branch is `main` or `master`:**
    - **ABORT immediately - DO NOT create PR from main/master**
    - Inform user: "Cannot create PR from main/master branch. Please create a feature branch first."
    - Suggest: "Use Git Commit & Push Agent to create a feature branch first"
    - **DO NOT proceed with PR creation**
  - **IF current branch is NOT `main`/`master`:**
    - Proceed with PR creation workflow
- Ensure all changes are committed and pushed

### Workflow
1. **Gather PR Information**
   - Current branch name
   - Commit history since branching from main: `git log main..HEAD --oneline`
   - Changed files: `git diff main...HEAD --name-only`
   - Branch purpose/feature name

2. **PR Title Format**
   ```
   [<TYPE>] <Brief description of changes>
   ```
   Examples:
   - `[FEATURE] Add Binance WebSocket integration for real-time crypto prices`
   - `[FIX] Resolve memory leak in RSS feed parser`
   - `[REFACTOR] Restructure settings screen with improved UX`

3. **PR Description Template**
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
   
   ## Screenshots/Videos
   [If UI changes, include visual proof]
   
   ## Checklist
   - [ ] Code follows project style guidelines
   - [ ] Tests pass locally
   - [ ] Documentation updated
   - [ ] No breaking changes (or documented if present)
   - [ ] Reviewed own code
   
   ## Related Issues
   Closes #[issue-number]
   
   ## Additional Notes
   [Any implementation decisions, trade-offs, or future improvements]
   ```

4. **Create PR Command**
   ```bash
   gh pr create \
     --base main \
     --head <current-branch> \
     --title "<generated-title>" \
     --body "<generated-description>" \
     --assignee @me \
     --label "feature" \
     --reviewer <optional-reviewers>
   ```

5. **Automated Checks Configuration**
   - Enable GitHub Actions if `.github/workflows/` exists
   - Suggested checks:
     - Kotlin compilation
     - Unit tests
     - Lint checks
     - Code coverage
     - Build verification for all platforms

6. **Post-Creation Actions**
   - Display PR URL
   - Show PR number
   - List enabled checks
   - Suggest next steps (wait for CI, request reviews, etc.)

---

## GitHub PR Review Agent

### Purpose
Review a Pull Request using PR number or URL, providing comprehensive code analysis and feedback.

### Input Handling
Accept either:
- PR number: `123`
- PR URL: `https://github.com/owner/repo/pull/123`

### Workflow
1. **Fetch PR Information**
   ```bash
   gh pr view <pr-number-or-url> --json title,body,author,state,commits,files
   ```

2. **Analysis Categories**

   **A. Code Quality Review**
   - Kotlin idioms and best practices
   - Proper null safety handling
   - Coroutine usage and scope management
   - Memory leak potential
   - Performance considerations
   - Security vulnerabilities

   **B. Architecture Review**
   - MVVM/MVI pattern adherence
   - Separation of concerns
   - Dependency injection usage
   - Repository pattern implementation
   - Proper abstraction layers

   **C. Compose Multiplatform Specifics**
   - Platform-specific code segregation
   - Common/expect-actual implementations
   - UI state management
   - Recomposition optimization
   - Resource handling across platforms

   **D. Testing Coverage**
   - Unit test presence and quality
   - Test edge cases
   - Mock usage appropriateness
   - Test naming conventions

   **E. Documentation**
   - KDoc comments for public APIs
   - README updates if needed
   - Inline comments for complex logic
   - Migration guides for breaking changes

3. **Review Output Format**
   ```markdown
   # PR Review: [PR Title]
   **PR #**: [number] | **Author**: [username] | **Status**: [open/closed]
   
   ## Summary
   [High-level overview of changes]
   
   ## Strengths ✅
   - [What was done well]
   - [Good practices followed]
   
   ## Issues Found 🔴
   ### Critical
   - [Security issues, memory leaks, crashes]
   
   ### Major
   - [Logic errors, performance problems, bad patterns]
   
   ### Minor
   - [Style issues, naming, documentation]
   
   ## Suggestions 💡
   - [Improvements and alternative approaches]
   
   ## Questions ❓
   - [Clarifications needed from author]
   
   ## Testing Recommendations 🧪
   - [Additional tests to add]
   - [Edge cases to consider]
   
   ## Files Reviewed
   - [List of changed files with brief notes]
   
   ## Overall Assessment
   **Approve** / **Request Changes** / **Comment**
   
   [Final verdict and reasoning]
   ```

4. **Automated Analysis**
   - Run static analysis: `./gradlew detekt` (if configured)
   - Check code coverage: `./gradlew koverReport`
   - Identify TODO/FIXME comments
   - Check for hardcoded strings (i18n concerns)
   - Verify proper resource usage (no leaks)
   - Check CI/CD status: `gh pr checks <pr-number>`
   - Verify mergeability: `gh pr view <pr-number> --json mergeable,mergeStateStatus`

5. **Automatic Review Actions**
   
   After completing the review analysis, the agent MUST automatically:
   
   **A. Post Review Comment**
   - Always post the comprehensive review as a comment on the PR
   - Use `gh pr comment <pr-number> --body "<review-content>"`
   - Include the full review markdown with all sections
   
   **B. Determine Review Decision**
   
   **Approve** if ALL of the following are true:
   - No critical issues found
   - No major issues found (or only minor issues that are non-blocking)
   - Code follows project conventions
   - Tests pass (verified via CI or local execution)
   - PR is mergeable (no conflicts, CI checks passing)
   - Backward compatibility maintained (or breaking changes documented)
   
   **Request Changes** if ANY of the following are true:
   - Critical issues found (security, crashes, data loss)
   - Major issues found that affect functionality
   - Code doesn't follow project conventions
   - Tests failing or missing critical test coverage
   - Breaking changes not documented
   - PR has merge conflicts
   
   **Comment** (no approval) if:
   - Only minor issues found (non-blocking)
   - Questions need clarification
   - Suggestions for improvement (but code is acceptable)
   - PR needs manual intervention (CI issues, etc.)
   
   **C. Submit Review Decision**
   ```bash
   # Approve (if criteria met)
   gh pr review <pr-number> --approve -b "<review-summary>"
   
   # Request changes (if blocking issues)
   gh pr review <pr-number> --request-changes -b "<review-summary>"
   
   # Comment only (if minor issues or questions)
   gh pr review <pr-number> --comment -b "<review-summary>"
   ```
   
   **Review Summary Format** (for approval/request-changes):
   ```
   ## Review Summary
   
   **Verdict**: [APPROVE / REQUEST CHANGES]
   
   **Key Findings**:
   - [Brief summary of main points]
   - [Critical/Major issues if any]
   - [Strengths if approving]
   
   **Next Steps**:
   - [If approving: Ready to merge]
   - [If requesting changes: List required fixes]
   - [If commenting: List suggestions]
   
   See full review in comments above.
   ```

6. **Interactive Review Commands** (Optional)
   - Checkout PR branch: `gh pr checkout <pr-number>`
   - Run tests: `./gradlew test`
   - Build and run: `./gradlew installDebug` (Android) or `./gradlew run` (Desktop)
   - Add inline comments: `gh pr review <pr-number> --comment -b "comment"`

7. **Post-Review Verification**
   - Confirm review comment was posted
   - Confirm review decision was submitted (approve/request-changes/comment)
   - Display review URL and decision
   - If approved, suggest using PR Merge Agent to merge

---

## GitHub PR Merge Agent

### Purpose
Automatically merge approved Pull Requests after successful review and CI checks. Ensures PRs meet all merge criteria before merging.

### Prerequisites Check
- Verify GitHub CLI is installed: `gh --version`
- Verify authentication: `gh auth status`
- Verify user has merge permissions (check with `gh repo view --json permissions`)
- Ensure PR number or URL is provided
- **Note**: This agent merges PRs, so branch protection is handled by GitHub. However, ensure you're not merging a PR that targets main/master directly without proper review.

### Input Handling
Accept either:
- PR number: `152`
- PR URL: `https://github.com/owner/repo/pull/152`
- Current branch PR: Automatically detect PR for current branch

### Merge Criteria (ALL must be met)

1. **PR Status Checks**
   - PR must be in `OPEN` state
   - PR must be mergeable (no conflicts)
   - All required CI checks must pass
   - PR must not be a draft

2. **Review Status**
   - PR must have at least one approval (or configured auto-approve)
   - No blocking review comments (request-changes)
   - Review status verified: `gh pr view <pr-number> --json reviews,reviewDecision`

3. **Branch Protection**
   - Base branch protection rules satisfied
   - Required reviewers approved (if configured)
   - Status checks passed (if required)

4. **Code Quality**
   - No critical issues in review
   - Tests passing (verified via CI)
   - Build successful

### Workflow

1. **Pre-Merge Validation**
   ```bash
   # Fetch PR details
   gh pr view <pr-number> --json \
     number,title,state,mergeable,mergeStateStatus,reviews,reviewDecision,statusCheckRollup,isDraft
   
   # Check CI status
   gh pr checks <pr-number>
   
   # Verify mergeability
   gh pr view <pr-number> --json mergeable,mergeStateStatus
   ```

2. **Validation Checks**

   **A. PR State Validation**
   - ✅ PR is OPEN
   - ✅ PR is not a draft
   - ✅ PR is mergeable (no conflicts)
   - ✅ Merge state status is CLEAN or UNSTABLE (with passing required checks)

   **B. Review Validation**
   - ✅ At least one approval exists
   - ✅ No blocking "request-changes" reviews
   - ✅ Review decision is APPROVED (or auto-approve configured)

   **C. CI/CD Validation**
   - ✅ All required status checks are passing
   - ✅ No failing required checks
   - ✅ Check conclusion is SUCCESS for required checks

   **D. Branch Protection**
   - ✅ Base branch protection rules satisfied
   - ✅ Required reviewers approved (if configured)
   - ✅ Up to date with base branch (or auto-merge enabled)

3. **Merge Strategy Selection**

   Determine merge method based on repository settings:
   ```bash
   # Check repository merge settings
   gh repo view --json mergeCommitAllowed,squashMergeAllowed,rebaseMergeAllowed
   ```

   **Preferred Order**:
   1. **Squash and Merge** (default for feature branches)
      - Creates single commit with PR title/description
      - Cleaner history
      - Command: `gh pr merge <pr-number> --squash --delete-branch`
   
   2. **Merge Commit** (for important features)
      - Preserves full PR history
      - Command: `gh pr merge <pr-number> --merge --delete-branch`
   
   3. **Rebase and Merge** (for linear history)
      - Clean linear history
      - Command: `gh pr merge <pr-number> --rebase --delete-branch`

4. **Merge Execution**

   **Standard Merge Flow**:
   ```bash
   # Merge with squash (recommended for feature branches)
   gh pr merge <pr-number> \
     --squash \
     --delete-branch \
     --subject "<PR title>" \
     --body "<PR description>"
   ```

   **Merge with Auto-Delete Branch**:
   ```bash
   # Automatically delete branch after merge
   gh pr merge <pr-number> --squash --delete-branch
   ```

   **Merge with Custom Message**:
   ```bash
   # Custom merge commit message
   gh pr merge <pr-number> \
     --squash \
     --subject "feat: <feature-name>" \
     --body "Merged via PR Merge Agent\n\n<PR description>"
   ```

5. **Post-Merge Actions**

   **A. Verification**
   - Confirm merge success: `gh pr view <pr-number> --json state`
   - Verify branch deletion (if enabled)
   - Check merge commit: `gh pr view <pr-number> --json mergedAt,mergedBy`

   **B. Notifications**
   - Display merge confirmation
   - Show merge commit hash
   - Display merged PR URL
   - List any post-merge actions needed

   **C. Cleanup**
   - Delete local branch (if merged): `git branch -d <branch-name>`
   - Update local main branch: `git checkout main && git pull`
   - Clean up remote tracking: `git remote prune origin`

### Error Handling

**If PR is not mergeable:**
1. Check merge conflicts: `gh pr diff <pr-number>`
2. Inform user about conflicts
3. Suggest: "PR has merge conflicts. Please resolve conflicts first."
4. Abort merge attempt

**If CI checks are failing:**
1. List failing checks: `gh pr checks <pr-number>`
2. Inform user about failing checks
3. Suggest: "CI checks are failing. Please fix issues before merging."
4. Abort merge attempt

**If PR needs approval:**
1. Check review status: `gh pr view <pr-number> --json reviews`
2. Inform user: "PR requires approval before merging."
3. Suggest: "Use GitHub PR Review Agent to review and approve first."
4. Abort merge attempt

**If branch protection blocks merge:**
1. Check protection rules: `gh api repos/:owner/:repo/branches/:branch/protection`
2. Inform user about protection requirements
3. Suggest manual merge or admin override
4. Abort merge attempt

### Merge Strategy Guidelines

**Use Squash and Merge when:**
- Feature branch with multiple commits
- Want clean, linear history
- Commits are implementation details
- PR description contains full context

**Use Merge Commit when:**
- Want to preserve full PR history
- Multiple contributors with meaningful commits
- Important feature with detailed commit history
- Need to reference individual commits

**Use Rebase and Merge when:**
- Want completely linear history
- All commits are meaningful
- No merge conflicts expected
- Repository policy requires linear history

### Safety Features

1. **Dry Run Mode**
   - Use `--dry-run` flag to preview merge without executing
   - Shows what would happen without actually merging

2. **Confirmation Prompt**
   - Always confirm before merging (unless `--auto` flag provided)
   - Display PR summary and merge strategy
   - Show potential impact

3. **Rollback Information**
   - Display merge commit hash for easy rollback
   - Show how to revert: `git revert <commit-hash>`

4. **Merge Conflict Detection**
   - Check conflicts before attempting merge
   - Abort if conflicts detected
   - Provide conflict resolution guidance

### Example Usage

**Basic merge:**
```bash
# Merge PR #152 with default settings
Use GitHub PR Merge Agent to merge PR #152
```

**Merge with specific strategy:**
```bash
# Merge PR #152 using squash and merge
Use GitHub PR Merge Agent: Merge PR #152 with squash strategy
```

**Auto-merge after approval:**
```bash
# Review and merge if approved
Use GitHub PR Review Agent to review PR #152, then GitHub PR Merge Agent to merge if approved
```

**Merge current branch PR:**
```bash
# Merge PR for current branch
Use GitHub PR Merge Agent to merge the current branch's PR
```

### Integration with PR Review Agent

**Combined Workflow:**
1. Review PR using GitHub PR Review Agent
2. If approved, automatically proceed to merge
3. If merge criteria met, execute merge
4. If criteria not met, inform user of blockers

**Auto-merge after review:**
- Review Agent approves PR
- Merge Agent validates merge criteria
- If all checks pass, automatically merge
- If checks fail, inform user and wait

---

## Project-Specific Guidelines

### Binance WebSocket Integration
- Use Ktor WebSocket client for cross-platform support
- Implement reconnection logic with exponential backoff
- Handle connection state properly (Connected, Disconnected, Error)
- Parse JSON responses with kotlinx.serialization
- Manage subscriptions efficiently (subscribe/unsubscribe)
- Handle rate limiting from Binance API

### RSS News Feed
- Use RSS parser library (e.g., Rome or kotlinx-rss)
- Cache news items locally (consider SQLDelight or Room)
- Filter news by selected cryptocurrency
- Handle malformed RSS feeds gracefully
- Implement pull-to-refresh functionality
- Show loading states and error messages

### App Settings
- Use DataStore for preferences (cross-platform)
- Settings categories:
  - Display preferences (theme, currency format)
  - Notification settings
  - Default crypto selections
  - Refresh intervals
  - Data management (clear cache)
- Validate user inputs
- Provide defaults for all settings

### UI/UX Best Practices
- Material 3 design system
- Responsive layouts for different screen sizes
- Proper loading states (shimmer effects)
- Error handling with retry options
- Empty states with helpful messages
- Smooth animations and transitions
- Accessibility considerations (content descriptions, contrast)

### Performance
- Lazy loading for long lists
- Image caching for crypto icons/logos
- Debounce search inputs
- Efficient recomposition (remember, derivedStateOf)
- Background tasks on IO dispatcher
- Proper lifecycle management

---

## Master Planning Agent Instructions

When given a task, you should:

1. **Understand the Full Scope**
   - Analyze the feature requirements
   - Identify all affected modules
   - List dependencies and prerequisites
   - Estimate complexity and time

2. **Create Implementation Plan**
   ```
   Phase 1: Setup & Dependencies
   - List all Gradle dependencies needed
   - Create project structure
   - Setup common/platform-specific modules
   
   Phase 2: Core Logic Implementation
   - Data models
   - Repository layer
   - Use cases/ViewModels
   - WebSocket/API integration
   
   Phase 3: UI Implementation
   - Screen composables
   - Navigation setup
   - State management
   - Error handling UI
   
   Phase 4: Testing
   - Unit tests for ViewModels
   - Repository tests
   - UI tests (if applicable)
   
   Phase 5: Documentation
   - Code comments
   - README updates
   - Architecture diagrams
   ```

3. **Execute Step-by-Step**
   - Complete each phase fully before moving to next
   - **⚠️ CRITICAL: Before using Git Commit & Push Agent:**
     - **ALWAYS** verify you're not on main/master branch
     - If on main/master, the Git Commit & Push Agent will automatically create a feature branch
     - **NEVER** instruct the agent to push to main/master directly
   - After each major milestone, use Git Commit & Push Agent to commit
   - Verify functionality after each phase
   - Ask for feedback if requirements are unclear

4. **Quality Assurance**
   - Run all tests before committing
   - Check for code smells
   - Verify cross-platform compatibility
   - Test edge cases and error scenarios

5. **Final Delivery**
   - Create comprehensive PR with GitHub PR Creation Agent
   - Self-review using GitHub PR Review Agent checklist
   - Provide demo instructions
   - Document any known issues or future improvements

---

## Usage Examples

### Example 1: Implementing WebSocket Feature
```
Task: "Implement Binance WebSocket integration for BTC, ETH, and BNB"

Planning:
1. Add Ktor WebSocket dependency
2. Create WebSocketManager class
3. Implement connection/disconnection logic
4. Add price data models
5. Create StateFlow for UI updates
6. Add error handling and retry logic
7. Write unit tests
8. Commit with Git Commit & Push Agent
9. Create PR with GitHub PR Creation Agent
```

### Example 2: Bug Fix
```
Task: "Fix crash when no internet connection on app launch"

Planning:
1. Reproduce the crash
2. Identify the crash location (likely WebSocket connection)
3. Add connectivity check before connection attempt
4. Implement offline mode UI
5. Add proper error messaging
6. Test with airplane mode
7. Commit with detailed message
8. Create PR with fix details
```

### Example 3: Code Review
```
Task: "Review PR #45 - Add RSS news feed"

Process:
1. Fetch PR details with `gh pr view 45`
2. Checkout branch locally
3. Review code structure
4. Check for proper error handling
5. Verify RSS parsing logic
6. Test manually with different feeds
7. Check test coverage
8. Provide comprehensive review
```

---

## Error Handling Protocols

### If Build Fails
1. Capture full error output
2. Identify root cause (dependency, syntax, etc.)
3. Fix the issue
4. Re-run build
5. Do not proceed with commit until build passes

### If Tests Fail
1. Run failing test in isolation
2. Analyze failure reason
3. Fix implementation or test
4. Verify all tests pass
5. Consider adding additional test cases

### If Push Fails
1. Check if remote branch has new commits
2. Pull with rebase: `git pull --rebase origin <branch>`
3. Resolve conflicts if any
4. Re-run tests
5. Push again

### If PR Creation Fails
1. Verify GitHub CLI authentication
2. Check branch is pushed to remote
3. **CRITICAL: Verify not already on main branch** - If on main, abort and create feature branch first
4. Check if PR already exists
5. Retry with verbose output

### If Attempting to Push to Main/Master
1. **STOP IMMEDIATELY** - Do not proceed with push
2. Create feature branch: `git checkout -b <type>/<description>`
3. Inform user about the branch protection violation
4. Provide instructions to move commit to feature branch if already committed
5. Never force push to main/master

---

## Commands Cheat Sheet

```bash
# Git Operations
git status
git branch
git log main..HEAD --oneline
git diff main...HEAD --name-only
git add .
git commit -m "message"
git push origin <branch>

# GitHub CLI
gh auth status
gh pr create --base main --head <branch> --title "title" --body "body"
gh pr view <number>
gh pr checkout <number>
gh pr review <number> --approve
gh pr list --state open

# Gradle
./gradlew build
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew koverReport

# Run App
./gradlew :androidApp:installDebug
./gradlew :desktopApp:run
```

---

## Final Notes

- **⚠️ CRITICAL: NEVER push to main/master** - Always check branch first, create feature branch if on main/master
- **Always ask for clarification** if requirements are ambiguous
- **Prioritize code quality** over speed
- **Write self-documenting code** with clear naming
- **Think cross-platform** - consider Android, iOS, Desktop differences
- **Security first** - never commit API keys, use environment variables
- **User experience matters** - smooth animations, helpful error messages
- **Test thoroughly** - unit tests, integration tests, manual testing
- **Document decisions** - explain why, not just what

**Remember**: Branch protection is the FIRST and MOST IMPORTANT check before any git operation. Violating this rule will result in loss of user trust.

These rules should enable Cursor to autonomously handle the entire development lifecycle from planning through implementation to PR creation and review.
