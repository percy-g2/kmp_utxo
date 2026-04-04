# Push Code

Push the current changes to a remote branch safely — never directly to main or master.

## Workflow

1. **Check the current branch** using `git branch --show-current`.

2. **If on `main` or `master`:**
   - Run `git diff HEAD --stat` and `git diff HEAD` to understand what changed.
   - Generate a descriptive branch name from the diff. Use the format `<type>/<short-description>` where type is one of: `feat`, `fix`, `refactor`, `chore`, `docs`, `perf`, `test`, `style`. Keep the description to 2-4 words, kebab-cased. Examples:
     - `refactor/price-alert-ux`
     - `fix/notification-formatting`
     - `feat/swipe-to-delete-alerts`
   - Also check `git status` for any untracked files that should be staged.
   - Tell the user the branch name you've chosen and confirm before proceeding.
   - Create and switch to the new branch: `git checkout -b <branch-name>`
   - Stage relevant files (prefer specific filenames over `git add -A` to avoid committing secrets or binaries).
   - If there are no existing commits on this branch for the current changes, create a commit with a clear message. Follow the repo's commit message conventions if visible from `git log`.
   - Push with upstream tracking: `git push -u origin <branch-name>`

3. **If already on a feature branch (not main/master):**
   - Run `git status` to confirm there are changes to push.
   - If there are uncommitted changes, ask the user if they want to commit first.
   - Push to the current branch: `git push -u origin <current-branch>`

4. **After pushing**, show the user the remote URL or a summary of what was pushed.

## Safety Rules

- **NEVER** run `git push` targeting `main` or `master`. If somehow the branch resolves to main/master, abort and tell the user.
- **NEVER** use `git push --force` unless the user explicitly requests it.
- **NEVER** stage files that look like secrets (`.env`, `*.jks`, `key.txt`, `credentials.*`, `*.pem`, `*.key`).
- Always confirm the branch name with the user before creating it.
