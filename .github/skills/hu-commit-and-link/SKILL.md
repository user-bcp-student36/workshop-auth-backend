---
name: hu-commit-and-link
description: Stage and commit OpenSpec change artifacts for one HU in workshop-auth-backend with a conventional message referencing the GitHub issue, and push only when asked. Use when the user asks to commit or push HU planning artifacts. Also use when the user says "commit HU", "push HU" or "git add and commit".
license: MIT
compatibility: Requires git.
metadata:
  scope: workshop-auth-backend only
---

Commit the change artifacts on the HU branch and link them to the issue.

## Guardrails

- Commit only when the user asks.
- Push only when the user asks.
- Work only in `workshop-auth-backend` on the current `feature/HU-XX` branch. Do not create or switch branches.
- Stage only the change folder. Do not stage unrelated files, such as `.vscode/mcp.json`.

## Steps

1. **Check the branch.** Run `git branch --show-current`. Expected: `feature/HU-XX`. Stop otherwise.

2. **Check the status.** Run `git status --short`. List the pending files and confirm each one belongs to the change folder. Stop and ask about anything else.

3. **Stage explicitly.** Run `git add openspec/changes/<change-name>/`.

4. **Commit.** Use a conventional message and the issue reference:
   `git commit -m "docs(openspec): <summary>" -m "Refs #<issue>"`

5. **Verify.** Run `git status --short` and `git log --oneline -1`. The tree should be clean for this change.

6. **Push, only if asked.** Run `git push`. If the branch has no upstream, use `git push -u origin feature/HU-XX`.

## Output

- Commit hash and message
- Files included
- Push status, if requested
