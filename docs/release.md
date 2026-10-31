# Release builds

The upload key is outside the repo, in `~/.android-keys/`:

- `photoxpo-upload.jks`: the upload key (alias `upload`).
- `photoxpo-keystore.properties`: its path and passwords, read by `app/build.gradle.kts`.

Set `PHOTOXPO_KEYSTORE_PROPERTIES` to use a properties file somewhere else. Without one, the release build is left unsigned, which is what CI builds.

Losing the upload key means asking Google Play support to reset it, so keep a copy of both files somewhere other than this machine.

Build:

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk, to send to a tester
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab, for Play
```

The release build is shrunk with R8. Run it once on the emulator before sharing (onboarding, a Backup run, Photos, Backup history): a class R8 removed only shows up at runtime. A release APK has a different signature from a debug one, so uninstall the debug build first, which clears the app's data.

Raise `versionCode` in `app/build.gradle.kts` for every build uploaded to Play.

## Stores

Photoxpo is GPL v3 and ships free in three places. Each signs the app with a different key, so an install from one can't be updated from another.

| Store | What to upload | Signed by |
|---|---|---|
| Google Play | `app-release.aab` | Google (Play App Signing); our key is only the upload key |
| GitHub Releases | `app-release.apk`, attached to a `v<versionName>` tag | our upload key |
| IzzyOnDroid | nothing: it picks up the APK from the latest GitHub release once the app is added | our upload key |
| F-Droid | nothing: F-Droid builds from the tag after a merge request to `fdroiddata` | F-Droid's key |

Store text and the 512 px icon live in `fastlane/metadata/android/en-US/`, which F-Droid and IzzyOnDroid read directly. Copy the same text into Play Console. Add `changelogs/<versionCode>.txt` for every release. The privacy policy is `PRIVACY.md`; Play needs it at a public URL (the file on GitHub works once the repo is public).

The app has no Google libraries (no Play Services, Firebase or billing), which is what F-Droid requires. Keep it that way, or put any such library in a separate Play-only build flavor.

## Play Console answers

Answers for the App content forms. They describe what the app does today; re-check them when the manifest changes.

- **Data safety:** no data collected, no data shared. The app has no `INTERNET` permission.
- **Ads:** no ads. **Target audience:** 18 and over. **Content rating:** utility, no user-generated content, no sharing between users.
- **Photo and video permissions** (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`): the app's core purpose is backing up the whole camera folder to a USB drive the person owns, and browsing that library afterwards. The Photo Picker can't do this, because a backup has to see every photo, including ones taken after setup, without the person choosing each one.
- **Foreground service, `connectedDevice`:** copies photos and videos to a USB drive attached to the phone, started by the person tapping Back up. If interrupted, the run pauses and the person has to reconnect and tap again. Record a short video: tap Back up with a drive attached, show the notification, then the finished run.
- **Foreground service, `dataSync`:** the fallback when Android refuses `connectedDevice`, for the same user-started Backup run. The run stops itself before Android's 6-hour limit.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:** a Backup run of a large library takes hours with the screen off, and some phones kill it under battery optimisation. The app asks once, explains why, and works if the person says no.

## First Play release

New personal developer accounts must run a closed test with at least 12 testers for 14 days in a row before production. Upload the AAB to a closed testing track, invite testers by email list, and keep them opted in: a tester who leaves and rejoins restarts the count.

Since 31 August 2026 new apps must target API 36.
