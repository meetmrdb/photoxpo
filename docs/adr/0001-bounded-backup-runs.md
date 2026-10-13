# Backup runs are bounded and never continue on their own

A Backup run ends at a size or time limit, when the battery runs low, when the Archive Drive fills or when Android's foreground-service time limit nears, and the person starts the next one. We do not auto-continue between runs: the phone has one USB-C port, so it cannot charge while the Archive Drive is attached, and a long unattended job would drain it. Every way a run can end early is a normal, clearly explained result, not an error. A big library is simply many Backup runs.

## Considered Options

- **Continue automatically while charging.** Rejected: the Archive Drive and the charger cannot both be attached, so the condition almost never holds.
- **One unbounded run.** Rejected: Android 15 stops a data-transfer foreground service after 6 hours in any 24, and a dead battery looks the same to the app as a pulled cable.
