---
name: hu-github-branch-setup
description: Create a GitHub issue for a user story (HU) in the workshop-auth-backend repository, create a development branch linked to it, and check that branch out locally. Use when the user asks to create an issue for an HU, open a branch linked to an issue, or prepare a branch before an OpenSpec change. Also use when the user says "crear issue HU", "rama vinculada" or "branch setup".
license: MIT
compatibility: Requires git and GitHub MCP tools (or gh CLI).
metadata:
  scope: workshop-auth-backend only
---

Prepare a GitHub issue and a linked branch for one HU. Work only in `workshop-auth-backend`. Do not read or change other repositories in the workspace.

## Guardrails

- Do not create or switch branches other than the one this skill creates, unless the user asks.
- Do not modify functional code, `pom.xml`, or `src/`.
- Do not commit or push unless the user asks.
- Do not guess the issue body. Build it from the HU file (for example `HU-01.md`) and quote the acceptance criteria as written.

## Steps

1. **Confirm the repository.** Run `git remote -v` and `git branch --show-current` in `workshop-auth-backend`. Stop if the remote is not the expected owner/repo.

2. **Check the working tree.** Run `git status --short`. If there are uncommitted changes that touch the branch to be created, stop and ask the user. Changes to unrelated files (for example `.vscode/mcp.json`) can make `gh issue develop --checkout` fail, so checkout is done in a separate step.

3. **Verify tool availability.** Before claiming a GitHub capability, check that the GitHub tool exists (use tool search for "github issue"). If the Projects v2 tools are missing, say so; do not invent results.

4. **Create the issue.** Title: `HU-XX - <short title from the HU file>`. Body: the context, the acceptance criteria, and the expected evidence from the HU file. Record the issue number.

5. **Create the linked branch.** Use `gh issue develop <number> --name feature/HU-XX --base main` (or the equivalent GitHub MCP call). The branch name follows `feature/HU-XX`.

6. **Check out the branch locally.** Run `git fetch origin` then `git checkout feature/HU-XX`. If checkout fails because of local changes, report the files and stop.

7. **Verify.** Run `git branch --show-current` (expected: `feature/HU-XX`) and `git status --short`. Report the issue URL, the branch name, and whether the branch tracks `origin`.

## Output

Report in short form:
- Issue number and URL
- Branch name and tracking status
- Any step that was skipped or failed, with the reason
