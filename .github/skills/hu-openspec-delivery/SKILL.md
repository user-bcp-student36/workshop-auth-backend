---
name: hu-openspec-delivery
description: Orchestrate the full OpenSpec delivery of one HU in workshop-auth-backend by running the phase skills in order - branch setup, proposal and specs, design gate, tasks, and commit. Use when the user asks to deliver an HU end to end or wants to know which phase comes next. Also use when the user says "entregar HU" or "flujo HU".
license: MIT
compatibility: Requires the phase skills hu-github-branch-setup, hu-proposal-and-specs, hu-design-gate, hu-tasks-checklist and hu-commit-and-link.
metadata:
  scope: workshop-auth-backend only
---

Run one HU through its phases. Each phase is its own skill. This skill only chooses the next phase and enforces the order.

## Phases

| Order | Skill | Stops when |
|---|---|---|
| 1 | `hu-github-branch-setup` | Issue created, branch `feature/HU-XX` checked out |
| 2 | `hu-proposal-and-specs` | Proposal and specs validate |
| 3 | `hu-design-gate` | `design.md` approved and open questions answered |
| 4 | `hu-tasks-checklist` | `tasks.md` written and 4/4 artifacts complete |
| 5 | `hu-commit-and-link` | Commit made; push only if requested |

## Guardrails

- Run one phase at a time. Wait for the user between phases.
- Do not skip a gate. Phase 4 cannot start before phase 3 passes.
- Do not implement functional code. Implementation is a separate step using `openspec-apply-change`.
- Work only in `workshop-auth-backend`.

## Steps

1. **Find the current phase.** Check the branch with `git branch --show-current`, then `openspec status --change "<change-name>"` if the change exists. If there is no issue or branch, start at phase 1.
2. **Run that phase's skill.** Read its `SKILL.md` and follow it.
3. **Report and stop.** Name the completed phase and the next one. Wait for the user's go-ahead.

## Notes

- Use `openspec-explore` before phase 2 if requirements are still unclear.
- After implementation and verification, use `openspec-sync-specs` and `openspec-archive-change`.
