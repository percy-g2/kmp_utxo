---
name: pcr
description: >-
  Run the full push, create PR, review pipeline in sequence: stage/commit/push
  (push-code), create a PR into main (create-pr), then approve or request
  changes on that PR (review-pr). Use when the user says pcr, push commit
  review, push and create pr and review, or wants one workflow from local
  changes to an opened and reviewed pull request.
---

# PCR — Push, Create PR, Review

Run the three git workflow skills in sequence as a single pipeline.

## Pipeline

```
push-code  ──>  create-pr  ──>  review-pr
```

### Step 1 — Push Code

Execute the [push-code](../push-code/SKILL.md) skill:
- Branch protection check
- Stage, commit, push with conventional commit message

**Stop the pipeline if this step fails.**

### Step 2 — Create PR

Execute the [create-pr](../create-pr/SKILL.md) skill:
- Generate PR title and description from commits
- Create PR via `gh pr create`

**Stop the pipeline if this step fails.** Capture the PR number for step 3.

### Step 3 — Review PR

Execute the [review-pr](../review-pr/SKILL.md) skill on the PR created in step 2:
- Analyze the diff
- Post review comment
- Submit approval or request-changes

## Output

After all three steps, report:
1. Commit hash and branch pushed
2. PR URL and number
3. Review verdict (approved / request-changes / comment)

## Safety

- Each step inherits the safety rules of its underlying skill.
- If any step fails, stop immediately and report what succeeded and what failed.
- **Never** push to protected branches.
- **Never** commit secrets.
