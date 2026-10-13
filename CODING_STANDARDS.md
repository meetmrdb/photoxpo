# Coding standards

Read during review. Mechanical rules are checks in CI, not entries here.

## Slow steps show their own progress

Any step that can take more than a few seconds says what it is doing and how far along it is, in its own words and numbers, never under another step's headline. A first run that re-verifies every Original must not read as "Backing up" with a count that does not move; it is "Checking your backed up photos" with "Checked N of M". When a change adds a step to a Backup run, check that Home shows it.
