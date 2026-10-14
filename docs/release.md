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
