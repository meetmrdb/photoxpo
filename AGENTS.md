## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Delegation (Cursor)

Claude orchestrates and follows the user-level `delegate-router` skill (`~/.claude/skills/delegate-router/SKILL.md`), including its Rule 0 usage preflight. Project specifics:

- **Stays with Claude:** anything that deletes, moves, overwrites or verifies an Original or a Preview (the delete flow, the **Backed up** check), USB-C **Archive Drive** and SAF access or permissions, long-running background jobs, and UI design. A mistake in these can lose a person's photos.
- **Fine to delegate to Cursor:** unit tests for pure logic, boilerplate, mechanical multi-file edits, lint and type fixes.
- **Fine to delegate to a Cursor Grok worker (read-only task, told not to edit files):** summarizing `docs/`, research notes and ADRs, and checking wording against `GLOSSARY.md`.
- Write delegated task text in the `GLOSSARY.md` terms (Archive Drive, Original, Preview, Backed up) and avoid the terms it lists under _Avoid_.
- Delegate only from a clean git tree on a feature branch, never inside `.claude/worktrees/`.
- Verify command: `./gradlew testDebugUnitTest` (JVM unit tests, run from the repo root). Needs `ANDROID_HOME` or `local.properties` with `sdk.dir` (the file is gitignored).
- Testing on a phone or the emulator: `DEVICE-NOTES.md` ("Driving the app from adb") has serials, state locations and how to fake battery and large backlogs.
- Stage by explicit path (`git add <path>`), never `git add -A`: unrelated edits have been swept into commits before.
