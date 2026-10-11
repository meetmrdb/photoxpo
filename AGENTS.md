## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Delegation (Cursor and Antigravity)

Claude orchestrates and follows the user-level `delegate-router` skill (`~/.claude/skills/delegate-router/SKILL.md`), including its Rule 0 usage preflight. Project specifics:

- **Stays with Claude:** anything that deletes, moves, overwrites or verifies an Original or a Preview (the delete flow, the **Backed up** check), USB-C **Archive Drive** and SAF access or permissions, long-running background jobs, and UI design. A mistake in these can lose a person's photos.
- **Fine to delegate to Cursor:** unit tests for pure logic, boilerplate, mechanical multi-file edits, lint and type fixes.
- **Fine to delegate to agy (read-only):** summarizing `docs/`, research notes and ADRs, and checking wording against `GLOSSARY.md`.
- Write delegated task text in the `GLOSSARY.md` terms (Archive Drive, Original, Preview, Backed up) and avoid the terms it lists under _Avoid_.
- Delegate only from a clean git tree on a feature branch, never inside `.claude/worktrees/`.
- Verify command: `./gradlew testDebugUnitTest` (JVM unit tests, run from the repo root). The Android project does not exist yet; the scaffold ticket (Build 1: Project scaffold and verify command) creates it, and this command must pass once it lands.
