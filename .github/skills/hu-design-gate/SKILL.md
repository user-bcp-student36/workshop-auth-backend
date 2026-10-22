---
name: hu-design-gate
description: Write design.md for one OpenSpec change in workshop-auth-backend and resolve its open questions with the user before any tasks are created. Use when the user asks to create or complete the design for an HU change, or to answer design open questions. Also use when the user says "diseño HU" or "design gate".
license: MIT
compatibility: Requires openspec CLI. Runs after hu-proposal-and-specs.
metadata:
  scope: workshop-auth-backend only
---

Write `design.md` and get its open questions answered. This phase does not write tasks.

## Guardrails

- Work only in `workshop-auth-backend` on the current `feature/HU-XX` branch.
- Do not edit functional code or `pom.xml`.
- Do not create `tasks.md`. It is blocked until this gate passes.
- Do not decide open questions on the user's behalf. Ask, then record the answer.

## Steps

1. **Check preconditions.**
   - Run `openspec validate "<change-name>"`. The proposal and specs must already be valid.
   - Run `openspec instructions design --change "<change-name>"` and follow its template.

2. **Read the inputs.** Read `proposal.md`, every file under `specs/`, and the source files named in the proposal. Re-read them from disk.

3. **Write `design.md`** with these sections:
   - Context
   - Goals / Non-Goals
   - Decisions (D1..Dn), each with alternatives and the chosen option
   - Characterization tests to add before changing behavior
   - Incremental strategy, where each step leaves the project compilable and tested
   - Risks / Trade-offs
   - Rollback
   - Open Questions

4. **Resolve open questions.** Ask the user each open question using a question tool with fixed options when possible. Include a recommended option. Do not continue until every question has an answer.

5. **Record answers.** Update the decisions and the open questions section in `design.md` to reflect the answers. Remove the question once it is answered.

6. **Validate.** Run `openspec validate "<change-name>"` and `openspec status --change "<change-name>"`.

7. **Gate.** Stop and ask for explicit approval of `design.md`. Only then can `hu-tasks-checklist` run.

## Output

- Decisions recorded and their answers
- Validation result
- Confirmation that the gate passed, or the questions still open
