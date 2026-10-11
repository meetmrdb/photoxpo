# Spike run on the Android 15 emulator (Pixel 6 profile, API 35), 2026-10-11

Stand-in drive: the folder `/sdcard/FakeDrive` on emulated internal storage. Samples: 8 synthetic
12 MP JPEGs (EXIF date, GPS, offset on 4 of them) and 3 synthetic 1080p H.264 clips. Not real Samsung media.

## Answered (holds on any Android 15 device)

- **Q2 connectedDevice**: refused on API 35 with a SecurityException until the manifest also declares one
  of the prerequisite permissions. `CHANGE_NETWORK_STATE` (normal permission, auto-granted) is enough.
  With it, startForeground(type 16) was accepted, a 5-minute copy ran to the end with the screen off and a
  partial wake lock held (dumpsys showed it). Not tested: unplugged, no wake lock, Doze, cable pull.
- **Q1 (mechanics only)**: the persisted tree grant was usable after an app restart and after a full reboot.
  The picker refuses the storage root on internal storage ("Can't use this folder"); only a subfolder works.
  Whether it offers a USB drive root is unanswered.
- **Q3 (mechanics only)**: raw DocumentsContract create, "wt" write, fsync, read-back, SHA-256, rename and delete all
  work. Emulator speed (182 MB/s write, 212 MB/s read) says nothing about the HDD.
- **Q4 (code path only)**: ImageDecoder 720 px preview, 150 KB at q70 for noisy 12 MP input (a worst case).
  EXIF date and OffsetTimeOriginal read; GPS readable plain and with setRequireOriginal. Video poster
  from MediaMetadataRetriever comes back at FULL size (1920x1080), so the app must downscale it.
  Video date on a clip without a creation_time atom reads as 1904-01-01, so the fallback to modified time must catch that.

## Not answerable on the emulator (still open for the real S24 Ultra + Seagate)

Q1 picker offering the USB drive root and replug survival; Q2 unplugged / Doze / no wake lock;
Q3 real listing time, HDD throughput, cable pull; Q4 real Samsung files (motion photos, HEVC, default format,
GPS stripping); Q5 gallery visibility of USB content (the emulator's folder is on primary storage, so MediaStore
listing it proves nothing); Q6 Samsung Recycle bin; Q7 One UI version and FAT32 vs exFAT.
