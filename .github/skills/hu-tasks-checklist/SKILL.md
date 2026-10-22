---
name: hu-tasks-checklist
description: Write tasks.md for one approved OpenSpec change in workshop-auth-backend as a trackable checklist, with characterization tests first and a verification per task. Use when the user asks to create tasks for an HU change after its design is approved. Also use when the user says "tareas HU" or "tasks HU".
license: MIT
compatibility: Requires openspec CLI. Runs only after hu-design-gate has passed.
metadata:
  scope: workshop-auth-backend only
---

Write `tasks.md` for an approved change. This phase does not implement anything.

## Guardrails

- Work only in `workshop-auth-backend` on the current `feature/HU-XX` branch.
- Do not start if `design.md` has open questions or is not approved.
- Do not edit functional code, `pom.xml`, or specs in this phase.
- Do not add work that is not in the proposal, specs, or design.

## Steps

1. **Check the gate.** Confirm `design.md` has no open questions and the user approved it. Stop otherwise.

2. **Read the inputs from disk.** Read `proposal.md`, `specs/**`, and `design.md`. Run `openspec instructions tasks --change "<change-name>"` and follow its template.

3. **Write `tasks.md`** using this structure:
   - Numbered `##` groups.
   - Each task is `- [ ] X.Y <description>; verificar con <command or observable result>`.
   - Group 1 holds characterization tests that pass on the current code.
   - Following groups implement the change incrementally. Each group lands its own tests and docs.
   - The last group holds integration checks only.
   - Optional `## Workflow follow-up` section with plain bullets (sync, archive). These are not tracked checkboxes.

4. **Check ordering.** Each task must depend only on earlier tasks. Each task must name a concrete verification.

5. **Validate.** Run `openspec validate "<change-name>"` and `openspec status --change "<change-name>"`. Expected: 4/4 artifacts.

6. **Stop.** Show the file and wait for the user before any implementation or commit.

## Output

- Number of tasks per group
- Validation result
- Open items, if any
