# Long-running job model on Android 14 and 15

Resolves ticket [#5](https://github.com/meetmrdb/photoxpo/issues/5), under the MVP map [#1](https://github.com/meetmrdb/photoxpo/issues/1).
Researched 2026-10-11. All pages were fetched live that day; Android docs change, so re-check before building.

## Question

What job model should run a multi-hour, resumable first backup (~600 GB of Originals from the phone to the Archive Drive) on Android 14 and 15 (minSdk 34)? The Galaxy S24 Ultra may also be on Android 16, so Android 16 changes are included where they bite.

## Summary

- Android 15 caps `dataSync` foreground services at 6 hours per 24 hours (for apps targeting API 35+). A 600 GB backup with read-back verification may or may not fit in one window; the cap is a poor foundation for the main job.
- User-initiated data transfer (UIDT) jobs are network-only on Android 14+ and must declare a required network. A copy to a USB-attached Archive Drive is not a network transfer, so UIDT does not fit.
- WorkManager's long-running workers are a wrapper around a foreground service. They add job-quota exposure (tighter on Android 16) and cannot express "Archive Drive is attached", so they add cost without removing the need for an app-owned pause/resume design.
- The `connectedDevice` foreground service type covers USB devices, has no documented time limit, and the Android docs name "transferring data to a locally connected device, by a USB connection" as its use. Its documented prerequisite is awkward for a Storage Access Framework (SAF) flow; see "Not confirmed".
- Keeping the CPU running with the screen off needs an explicit partial wake lock when the app uses its own foreground service. Samsung's sleeping-app policy and the user's Stop button in the Task Manager mean the job must always be resumable from per-file state.

## Findings

### 1. Foreground service types and the Android 15 `dataSync` limit

- Android 14 (targeting API 34+): every foreground service must declare a type in the manifest and hold the matching `FOREGROUND_SERVICE_*` permission, or the system throws. These type permissions are normal permissions, granted automatically. [S3][S5]
- `dataSync` is described as covering network transfers in either direction, backup operations, import/export and local file processing. There is no runtime prerequisite. [S2]
- Android 15, apps targeting API 35+: the system lets `dataSync` (and the new `mediaProcessing`) foreground services run a total of 6 hours in any 24-hour period, shared across all of the app's services of that type. [S1][S4]
- At the limit the system calls `Service.onTimeout(int, int)`; the service has a few seconds to call `stopSelf()` or the app crashes with a `RemoteServiceException` ("did not stop within its timeout"). After `onTimeout` the service is no longer treated as foreground. [S1][S23]
- The timer resets when the user brings the app to the foreground. If the 6 hours are used up, starting another `dataSync` service throws `ForegroundServiceStartNotAllowedException` until the user opens the app. [S1]
- Docs advise starting `dataSync` only from direct user interaction (so the full 6 hours counts after the app goes to the background) and suggest an alternative API instead of `dataSync`. [S1]
- `dataSync` cannot be launched from a `BOOT_COMPLETED` receiver for apps targeting API 35+. [S2][S4]
- The cap is opt-in by target: the Android 15 behavior page lists it as applying to apps targeting API 35+; the timeout page shows a compat flag to switch it on for lower targets when testing. [S1][S4] Staying on targetSdk 34 to dodge it is possible but only defers the problem (not confirmed how long that gate lasts).
- `shortService` is limited to about 3 minutes and is irrelevant here. [S2]
- `connectedDevice`: "interactions with external devices that require a Bluetooth, NFC, IR, USB, or network connection". The timeout page names only `dataSync` and `mediaProcessing` as time-limited. [S1][S2] Prerequisite (at least one of): a manifest-declared `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `NFC` or `TRANSMIT_IR`; a granted Bluetooth/UWB runtime permission; or a call to `UsbManager.requestPermission()`. [S2][S3]
- The "Data transfer background task options" guide lists `connectedDevice` for "transferring data to a locally connected device, by a USB connection, NFC, or a local internet connection", and says to prefer a purpose-built API (for example companion device manager) when one exists. [S10] No purpose-built API exists for a USB mass-storage Archive Drive reached through SAF (none found in the docs read).
- Starting a foreground service from the background is blocked on Android 12+ except for listed exemptions, which include a transition from a user-visible state, a user action on a notification, and "the user turns off battery optimizations for your app". [S6] Starting the backup from a button tap in the visible app satisfies this.

### 2. User-initiated data transfer (UIDT) jobs

- UIDT is a `JobScheduler` job flagged with `setUserInitiated(true)`, new in Android 14. It needs `RUN_USER_INITIATED_JOBS`, a mandatory notification set through `JobService.setNotification()`, and must be scheduled while the app is visible (or otherwise allowed to launch an activity). Otherwise scheduling fails with `RESULT_FAILURE`. [S7][S8]
- Benefits: not subject to App Standby Bucket quotas, exempt from the ordinary job quotas on Android 16, and the system "may be able to run [it] for an extended period". [S7][S5]
- The system stops a UIDT job if a developer-set constraint fails, if it decides the job has run longer than needed for the transfer (it uses the estimated payload size), on thermal pressure, or when the process is killed for low memory. [S7]
- Pressing Stop in the Task Manager kills the whole process, skips `onStopJob()`, and blocks rescheduling. [S7]
- The decisive constraint: the JobInfo source states that on Android 14 UIDT jobs "can only be used for network data transfers" and must declare a required network; `build()` throws "A user-initiated data transfer job must specify a valid network type" when none is set. The same check is present in the `android14-release`, `android15-release` and `android16-release` branches. [S9]
- Consequence: a copy to a USB-attached Archive Drive has no honest network requirement. Attaching a dummy network constraint would make the job refuse to run offline and, per [S7], invite the system to cut it short against a network-byte estimate that does not describe a local copy. Not verified on a device, and not recommended.
- There is no Jetpack library for UIDT; Google suggests WorkManager's foreground path as the fallback on older Android. [S7]

### 3. WorkManager long-running workers

- A worker that calls `setForeground()` / `setForegroundAsync()` runs inside a WorkManager-managed foreground service with a notification you supply. On Android 14+ the `ForegroundInfo` must carry a foreground service type, and the type must also be merged into `SystemForegroundService` in the manifest. [S11]
- WorkManager persists scheduled work in its own SQLite database and reschedules it across reboots. [S12]
- A worker that loses a constraint mid-run is stopped and retried when constraints hold again. [S32] Constraints cannot describe "Archive Drive attached" (the available ones are network, charging, battery, storage, idle). [S12] (This list is from the docs' general constraint description; no drive/USB constraint was seen.)
- Docs state long-running workers are "subject to the same restrictions as a foreground service and jobs", including limits on launching from the background and execution limits; jobs over 10 minutes "will be rescheduled by the system". [S10]
- Calling `setForegroundAsync()` while the app is subject to foreground-service start restrictions throws `ForegroundServiceStartNotAllowedException`. [S13]
- Android 16: jobs running concurrently with a foreground service, including WorkManager long-running workers, now count against runtime quotas (quota is "generous" only in the active standby bucket). Docs suggest launching the foreground service directly or using UIDT for user-driven transfers. [S11][S21][S19]
- On `dataSync`/`shortService` timeouts, WorkManager 2.10.0 added the stop reason `STOP_REASON_FOREGROUND_SERVICE_TIMEOUT`; the source shows it stops the matching foreground workers and then stops the service. [S13][S14] Whether WorkManager then reschedules the work was not confirmed.

### 4. Keeping the CPU awake with the screen off

- Docs: if the app runs a foreground service and the device suspending with the screen off would hurt the user, "you might need to use a wake lock". Work run through WorkManager, JobScheduler or DownloadManager gets a wake lock acquired on its behalf. [S15]
- A foreground service alone does not appear in the docs read as a guarantee of CPU wakefulness; the awake-API guide treats the wake lock as a separate decision. [S15]
- `PARTIAL_WAKE_LOCK` keeps the CPU running while the screen and keyboard backlight go off. It needs the `WAKE_LOCK` permission; `acquire(long timeout)` auto-releases after the timeout. [S16][S17]
- Docs push for the shortest hold time and always releasing. [S15][S17] A long-held lock while the app is in the background is the pattern Android vitals flags as "stuck" (the numeric threshold was not on the page read); vitals applies to Play-distributed apps, which Photoxpo is not. [S17]
- Doze: when the device is unplugged and stationary with the screen off, Doze suspends network, defers jobs, and "ignores wake locks". Plugging in a charger exits Doze. [S18] Jobs have "no execution limits except for restricted standby bucket" while charging. [S20]
- Open point: the pages read do not say whether a running foreground service exempts the app from Doze. See "Not confirmed".
- Whether the phone charges while it powers the Archive Drive depends on the adapter (pass-through charging). This is hardware, not answered by the Android docs; it decides whether Doze can apply during the run.

### 5. Samsung battery behaviour

- Samsung's developer page on application management describes "sleeping" apps (background apps unused for about 3 days that Samsung deems to be hurting system health) and "deep sleeping" apps (unused for a long period, currently 16 days, "subject to change"). For sleeping apps a bucket restriction limits job, alarm and foreground-service features; deep sleeping apps only run when opened. [S26]
- Users can exempt an app under Settings > Battery and device care > Battery > Background usage limits ("Never sleeping apps"). [S26][S27]
- The same page says foreground services of apps targeting Android 14 work as intended when they follow Android's foreground-service policy. [S26]
- Only Samsung's own pages are used here. Community posts and third-party lists claim One UI 8 is more aggressive; those are not primary and are not relied on.
- Relevance: the backup app is used rarely, so after about 3 days unused it can become a sleeping app. Opening it and tapping start is a user launch, but how One UI treats an active foreground service in an app it has already put to sleep is not documented in the pages read.

### 6. What the user must grant

| Item | Needed for | Source |
|---|---|---|
| Notification permission (`POST_NOTIFICATIONS`, runtime on Android 13+) | Seeing the foreground notification and progress. Apps do not need it to start a foreground service, but the notification is hidden in the drawer without it. | [S24] |
| Archive Drive folder via the system picker, with `takePersistableUriPermission()` | Writing to the drive; survives reboot. Access is lost if the document is moved or deleted. | [S28] |
| Foreground service type permission (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`) | Declared in the manifest; normal permissions, no prompt. | [S3] |
| `WAKE_LOCK` | Partial wake lock; normal permission. | [S17] |
| Battery: set the app to unrestricted / "Never sleeping apps" on Samsung | Recommended, not required: it reduces the chance of Samsung sleeping the app and is a listed exemption for starting foreground services from the background. | [S6][S26] |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional direct Doze/standby exemption prompt. Android calls it for "unusual applications" and warns they show as high power consumers; Play policy restricts it, irrelevant for a sideloaded APK. | [S25][S18] |

### 7. Surviving being killed, and resuming

- Task Manager Stop (Android 13+): the whole process is removed, the notification goes, no callback is sent; on next start the app can check `ApplicationExitInfo` for `REASON_USER_REQUESTED`. [S22] This is outside the app's control, so resumption must come from persisted state, not from a clean shutdown path.
- `START_STICKY`: if the process dies while started, the system re-creates the service and calls `onStartCommand` with a null intent. [S31] Whether that restart from the background is allowed under the Android 12+ foreground-service start rules was not confirmed.
- UIDT note on state: even if the stop callback is skipped, the app must be able to rebuild its state from storage when work starts again. [S7] The same rule applies to any model here.
- Reboot: nothing auto-starts. The map already says the user opens the app after plugging in; this also keeps the design out of the `BOOT_COMPLETED` restrictions. [S4][S6]
- Persisted SAF permission survives restarts. [S28]

### 8. What must not run while the Archive Drive is detached

Sources describe the detection tools, not the policy; the policy is a design consequence of the map's rules ("nothing is Backed up until verified", drive identified by id file).

- Detection tools: `StorageManager.registerStorageVolumeCallback()` (API 30+) reports volumes changing between mounted and unmounted states [S29]; `UsbManager.ACTION_USB_DEVICE_DETACHED` is broadcast on USB host-mode detach [S30]; `Intent.ACTION_MEDIA_EJECT` asks apps to close open files under the mount point [S30]. I/O errors during writes are the fallback signal. Whether the volume callback maps cleanly to a SAF tree URI was not confirmed.
- Must not run while detached: writing any Original or Preview into `Photoxpo/` on the drive; reading back for verification; updating the manifest in `.photoxpo/`; marking an Original Backed up; any delete of an Original on the phone (it depends on Backed up); Load.
- Safe while detached: reading phone files, and generating Previews on the phone from source Originals (the drive copy of each Preview must wait).
- On reattach the app should confirm the drive id file matches before resuming, then re-verify any file that was mid-copy rather than trusting a partial file.

## Options compared

| Option | Fits a local USB copy? | Time limit | Survives Task Manager Stop | Verdict |
|---|---|---|---|---|
| `dataSync` foreground service | Yes (description includes backup, local file work) | 6 h per 24 h on targetSdk 35+; resets when the user opens the app | No (nothing does) | Workable only with an explicit session-per-window design; a poor base |
| UIDT job | No: must declare a required network; network-only on Android 14+ | System decides from estimated bytes | No (Stop blocks rescheduling) | Rejected |
| WorkManager long-running worker | Partly: runs a foreground service for you; cannot express drive attached | Rides on the chosen foreground service type; job quotas on Android 16; docs note jobs over 10 min are rescheduled | Persisted, but a user Stop is not retried by the system | Rejected for the main job |
| `connectedDevice` foreground service owned by the app | Yes (USB listed) | None documented | No, so resume from state | Recommended, with the prerequisite caveat below |
| `shortService` | No | about 3 min | No | Rejected |

Rough duration, not from a source: at an assumed 40 to 100 MB/s write and a full read-back, 600 GB takes about 3 to 8 hours. Real throughput through SAF on this drive is unmeasured. If the write step alone approaches 6 hours the `dataSync` cap matters; the estimate is only here to show the cap is close, not clearly safe.

## Not confirmed

1. Whether `connectedDevice` is accepted for a SAF-mounted mass-storage drive without a `UsbManager.requestPermission()` call. The docs list manifest-declared `CHANGE_NETWORK_STATE` and similar as sufficient, but the fit to a drive the app never opens through the USB API is a judgment call. Needs a test on the S24 Ultra (Android 15 and 16): start the service with the type declared and check logcat plus `adb shell dumpsys activity services`.
2. Whether a running foreground service (with or without a partial wake lock) keeps the CPU running through Doze when the phone is unplugged. The docs say Doze ignores wake locks and exempts apps on the battery-optimization allowlist; they do not state a foreground-service exemption. Test unplugged with `adb shell dumpsys deviceidle force-idle`.
3. Android 17 or later changes: the "Changes to foreground services" page read lists up to Android 16 only, so none were seen. Not searched elsewhere.
4. Samsung One UI treatment of an already-sleeping app that starts a foreground service, and any One UI 8 specifics beyond the developer page; the page is undated and says the thresholds can change.
5. Whether `START_STICKY` restarts of a killed foreground service are allowed from the background on Android 12+ (the exemption list does not mention system restarts).
6. Whether WorkManager reschedules a worker stopped with `STOP_REASON_FOREGROUND_SERVICE_TIMEOUT`.
7. Mapping a SAF tree URI to the `StorageVolume` seen by `StorageVolumeCallback`.
8. Real copy and verify throughput, and whether the adapter passes charging through to the phone.
9. How long the targetSdk 34 gate on the `dataSync` cap lasts.

Method note: key claims (timeout, UIDT, awake-API, power tables, Android 16 changes) were checked against the raw page text, not only a summariser's output. The Samsung developer page renders by script, so it was read through a summariser only (its wording is paraphrased here and worth a manual look). The WorkManager constraint-retry statement [S32] also came through a summariser. The JobInfo behaviour was checked against AOSP source in three release branches.

## Recommendation

Use one app-owned foreground service of type `connectedDevice` for the first backup, treated as a series of short resumable runs, not one uninterrupted job.

1. Start it only from a visible button tap ("Back up now"), which avoids the background-start restrictions.
2. Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`, plus one of the documented prerequisite permissions (for example `CHANGE_NETWORK_STATE`; normal, no prompt). Verify on the S24 Ultra first (item 1 above).
3. Hold a `PARTIAL_WAKE_LOCK` only while a file is actively being copied or verified, acquired with a timeout and renewed; release it on pause, error, or completion.
4. Keep per-file state in Room (pending, copying, copied, verified/Backed up). On start, treat any file left "copying" as incomplete and redo it. Never assume a clean shutdown.
5. Register a storage volume callback and handle I/O errors: on detach, stop writes, release the wake lock, drop the foreground state, and show "Archive Drive disconnected: reconnect and tap Resume". On reattach, check the drive id file before continuing.
6. Implement `onTimeout(int, int)` even though `connectedDevice` has no documented limit: checkpoint, call `stopSelf()` within seconds, and show the same "tap to resume" state. This also covers any limit added later, and the fallback below.
7. Ask for notification permission and guide the user to set the app to "Never sleeping apps" on the Galaxy (Settings > Battery and device care > Battery > Background usage limits). Offer the battery-optimization exemption prompt as optional. Tell Nandhu that pressing Stop in the Task Manager halts the backup and that opening the app and tapping Resume continues it.
8. Do not use UIDT (network-only) or WorkManager long-running workers (job quotas, no way to express "drive attached") for this job.

Fallback if the device test shows `connectedDevice` is rejected or unreliable: use a `dataSync` foreground service with the same session design. Plan for the 6 h per 24 h cap: checkpoint at the limit, and let the user's next app open reset the timer.

This amends the map's line "foreground service for long jobs" by fixing the service type (`connectedDevice`, not `dataSync`), the wake-lock rule, and the pause/resume behaviour. No conflict with existing ADRs (none exist yet).

## Sources

- [S1] Foreground service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
- [S2] Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- [S3] Foreground service types are required (Android 14): https://developer.android.com/about/versions/14/changes/fgs-types-required
- [S4] Android 15 behavior changes: https://developer.android.com/about/versions/15/behavior-changes-15
- [S5] Changes to foreground services: https://developer.android.com/develop/background-work/services/fgs/changes
- [S6] Restrictions on starting a foreground service from the background: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- [S7] User-initiated data transfer jobs: https://developer.android.com/develop/background-work/background-tasks/uidt
- [S8] JobInfo.Builder.setUserInitiated: https://developer.android.com/reference/android/app/job/JobInfo.Builder
- [S9] AOSP JobInfo.java (android14/15/16-release): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/apex/jobscheduler/framework/java/android/app/job/JobInfo.java
- [S10] Data transfer background task options: https://developer.android.com/develop/background-work/background-tasks/data-transfer-options
- [S11] Long-running workers (WorkManager): https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- [S12] Task scheduling / WorkManager overview: https://developer.android.com/develop/background-work/background-tasks/persistent
- [S13] WorkManager release notes: https://developer.android.com/jetpack/androidx/releases/work
- [S14] androidx SystemForegroundDispatcher / SystemForegroundService source: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/work/work-runtime/src/main/java/androidx/work/impl/foreground/SystemForegroundDispatcher.java
- [S15] Choose the right API to keep the device awake: https://developer.android.com/develop/background-work/background-tasks/awake
- [S16] PowerManager and PowerManager.WakeLock reference: https://developer.android.com/reference/android/os/PowerManager and https://developer.android.com/reference/android/os/PowerManager.WakeLock
- [S17] Use wake locks and stuck/excessive wake lock pages: https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set , https://developer.android.com/topic/performance/issues/stuck-wakelock
- [S18] Optimize for Doze and App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
- [S19] App Standby Buckets: https://developer.android.com/topic/performance/appstandby
- [S20] Power management resource limits: https://developer.android.com/topic/performance/power/power-details
- [S21] Android 16 behavior changes, all apps: https://developer.android.com/about/versions/16/behavior-changes-all
- [S22] Handle user-initiated stopping of apps running foreground services: https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
- [S23] Troubleshoot foreground services: https://developer.android.com/develop/background-work/services/fgs/troubleshooting
- [S24] Notification runtime permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- [S25] Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: https://developer.android.com/reference/android/provider/Settings
- [S26] Samsung application management (developer page): https://developer.samsung.com/mobile/app-management.html
- [S27] Samsung support, sleeping apps: https://www.samsung.com/us/support/answer/ANS10003442/
- [S28] Open files using the Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- [S29] StorageManager.StorageVolumeCallback: https://developer.android.com/reference/android/os/storage/StorageManager.StorageVolumeCallback
- [S30] UsbManager and Intent reference: https://developer.android.com/reference/android/hardware/usb/UsbManager , https://developer.android.com/reference/android/content/Intent
- [S31] Service reference (START_STICKY, onTimeout): https://developer.android.com/reference/android/app/Service
- [S32] Define your WorkManager requests (constraints, retry): https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
