# Device notes

Observed facts from testing Build 1 (issue #22). Observed, not gates, except where a stop-ship item is marked.
Each entry: date, stage, device, what was done, result.

## Driving the app from adb

How to set up and read a test, so it is not rediscovered. The phone is `10.0.0.10:5555` over wifi (the address can change; check `adb devices`), the emulator is `emulator-5554`. The package is `app.photoxpo`; `adb install -r` keeps data.

- **Read Home as text:** `scripts/home-state.sh [serial]` prints the screen's text. Use a screenshot only for how something looks. Screenshot coordinates are scaled; multiply by the factor the image reports.
- **Archive Drive on the emulator:** `/sdcard/FakeArchiveDrive`. Run files are in `Photoxpo/.photoxpo/runs/`, Originals in `Photoxpo/Originals/<year>/<year>-<month>/`.
- **Fake the battery:** `adb shell dumpsys battery unplug`, then `set level 12`; undo with `reset`. Home reads the sticky broadcast, so relaunch or pull down to refresh.
- **Fake a big backlog:** `truncate -s 6000000000 /sdcard/DCIM/Camera/NAME.mp4`, then `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/Camera/NAME.mp4`. MediaStore reports the full size; the file takes no space until a run reads it. Remove it afterwards.
- **A copied duplicate** of an existing photo counts as backed up with `fileCount 0` in the footer, because the copier stores one copy per hash.
- **Where state lives** (`adb shell run-as app.photoxpo`): `shared_prefs/photoxpo.backed-up.xml` is the ledger of Originals; `photoxpo.checked-at.xml` holds `hash|size|modified|checkedAtMillis`; Room `databases/photos.db` table `backup_runs` caches Backup history.
- **Room caches run history.** Moving run files off the drive does not change Home's time and battery estimate. Clear it with `sqlite3 databases/photos.db "delete from backup_runs"` after copying `photos.db*` somewhere to restore from.
- **Time estimate needs no history to warn.** With real runs on record the copy speed is the measured one (12 MB/s on the emulator), so a long-run warning shows only with no usable history (fallback 3 MB/s).

## 2026-10-12 — Stage 1 (emulator)

**Device:** Android emulator `HomeVault_API35` (Android 15, API 35, x86_64). A folder on emulated storage (`/sdcard/FakeArchiveDrive`), chosen through the real system folder picker, stood in for the Archive Drive. The emulator's `DCIM/Camera` held 8 photos and 3 videos (2 of the videos are byte-identical).

| # | Item | Result |
|---|------|--------|
| 1 | Every file in the run is Backed up and the read-back hash matches (**stop-ship**) | **Pass.** 11 source files; 9 stored (the 2 duplicate videos stored once, one manifest entry listing all 3 names). Independent `sha256sum` on the device of every stored file equals its source: 0 mismatches. Footer `checked: true`, 14 MB/s on emulated storage. |
| 3 | Force-stop mid-run, reopen: resumes with no duplicates (**stop-ship**) | **Pass.** Killed during the 5th file: 4 manifest lines, a leftover `.photoxpo-part`, no footer. After reopening, the second run added the other 5 files, removed the part file, wrote a footer. 9 files, no duplicates, all hashes match. The interrupted run's manifest stays without a footer. |
| 4 | Load of 5 random Originals: hash equals the drive's; appears in Gallery | **Pass.** 5 Originals Loaded into `Pictures/Photoxpo Loaded`; SHA-256 of each equals the drive copy. MediaStore lists all 5 (`is_pending=0`). The screen says "On this phone and your drive". Only a MediaStore query was checked, not the Gallery app itself. |
| 5 | Dates and poster frames (emulator part) | **Pass.** After wiring MediaStore date taken and EXIF into the source, the 8 photos are tagged `exif` and filed under their real months (2024-01 … 2024-08). The test videos have no usable metadata date, so they fall back to the modified time (2026). Nothing is dated 1904. The video poster frame is 720x405, no EXIF. |
| 6 | Previews display with the drive missing | **Pass.** With the drive folder renamed away, the Photos tab still shows every Preview grouped by month; Home shows the calm "Your Archive Drive is not connected" banner with the last backed-up time. |
| 7 | The grant survives an emulator reboot | **Pass.** After `adb reboot` the app still probed the drive as ready, and a new run wrote its run file to the drive with no picker. |

Other things seen on the emulator:

- A wiped drive (id file gone while the phone remembers one) is refused: nothing was copied, and Home offered "Use this drive instead". After confirming, the run copied everything.
- The system picker refuses the storage root ("Can't use this folder"). On the real Seagate the picker may behave the same: Stage 2 must check that it offers a usable folder on the drive.
- The Photos tab was empty after a real run until the post-run step (Previews, then filling the cache from the Manifests) was added.
- Home showed "Backed up 0" after the app restarted; the count now starts from the phone's cache.

Not covered by the emulator (Stage 2): cable pulled mid-copy (item 2, stop-ship), real motion/HEVC/GPS files, the USB picker, HDD speed, Doze with the screen off, the `connectedDevice` service with the drive unplugged, gallery visibility of USB content, One UI version, FAT32 versus exFAT, a file over 4 GiB on FAT32.
