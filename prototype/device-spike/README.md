# PROTOTYPE: on-device spike (ticket #12). Throwaway, wipe me.

One-screen Android app that answers the open device questions. No product code is kept.

Build (SDK tools only, needs a JDK with javac): `./build.sh` produces `photoxpo-spike.apk`.
Install: copy the APK to the phone and open it, or `adb install -r photoxpo-spike.apk`.

## Run order (button names match the app)

1. **Perms**: allow photos, videos, location access ("Allow all" for photos), notifications.
2. **7 Device**: note the line, then in My Files > the drive > Storage info, write down FAT32 or exFAT.
3. **1 Pick drive**: does the picker offer the drive root? Pick it, allow. Then **1 Check grant**.
   Unplug and replug, reopen the app: "GRANT USABLE"? Reboot the phone, plug in, reopen: same check.
4. **3 List**: time for the real photo tree. Pick the folder that holds his ~600 GB pile.
5. **3 Write 256MB**, then **3 Write 2GB**: write and read-back MB/s, hash match.
6. **2 Svc connDev 5m**: "startForeground accepted"? Turn the screen off, plugged in; wait 5 min;
   open the app, read the log. Repeat unplugged. Repeat with **2 No wake lock**. Try **2 Svc dataSync** if connectedDevice is refused.
   Pull the cable mid-copy once and note what the log says.
7. **4 Samples (30)**: copy the log. Look for exifDate, offset, MOTION, SIZE-MISMATCH, gpsPlain vs gpsOriginal, preview sizes, poster.
8. **5 Gallery test**: does MediaStore list the drive file? Check Samsung Gallery and Google Photos by hand.
9. **6**: turn Samsung Gallery Recycle bin on. **6 Make test photo**, **6 Free space**, delete it in My Files
   (not Gallery), check Gallery > Recycle bin, **6 Free space**, empty the bin, **6 Free space**.

Use **Copy log** and paste it into the ticket answer.
