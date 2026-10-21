---
name: hu-proposal-and-specs
description: Write the OpenSpec proposal.md and delta specs for one HU in workshop-auth-backend, grounded in the HU file, docs/ENDPOINTS.md and the affected code. Use when the user asks to create a proposal or specs for an HU, or starts a new OpenSpec change for an HU. Also use when the user says "propuesta HU" or "specs HU".
license: MIT
compatibility: Requires openspec CLI and git. Uses the openspec-propose skill for CLI conventions.
metadata:
  scope: workshop-auth-backend only
---

Create `proposal.md` and the delta specs for one HU. This phase does not write design or tasks.

## Guardrails

- Work only in `workshop-auth-backend` on the current `feature/HU-XX` branch. Do not create or switch branches.
- Do not edit `src/`, `pom.xml`, or any functional code.
- Do not create `design.md` or `tasks.md`.
- Do not cite H-codes, CA-codes, or behavior that was not verified in the files.
- Do not run `openspec init`. The project already has `openspec/`.

## Steps

1. **Check preconditions.**
   - Run `git branch --show-current`. Expected: `feature/HU-XX`. Stop otherwise.
   - Run `openspec list --json` and confirm `root` is not null.
   - Read the HU file (`HU-XX.md`) and note the issue number.

2. **Gather facts.** Read `docs/ENDPOINTS.md` and the source files the HU touches. Note what the code actually does, including any mismatch with the HU text.

3. **Create the change.** Run `openspec new change "<change-name>"`, using kebab-case, for example `hu-01-token-firmado`. Follow the `openspec-propose` skill for CLI conventions.

4. **Write `proposal.md`** with these sections: Why, What Changes, Capabilities (New or Modified), Behavior to preserve, Scope, Out of scope, Impact. Reference the issue and the HU file. Flag discrepancies between the HU and the code.

5. **Write delta specs** under `specs/<capability>/spec.md`. Use `## ADDED Requirements` or `## MODIFIED Requirements`. Each requirement needs at least one `#### Scenario:` with WHEN and THEN lines.

6. **Validate.** Run `openspec validate "<change-name>"` and fix errors until it passes.

7. **Stop.** Show the files and wait for the user before the design phase.

## Output

- Files created
- Validation result
- Discrepancies between the HU and the code
- Next step: design gate (`hu-design-gate`)
