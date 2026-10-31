# Photoxpo

Back up your Android phone's photos and videos to a USB drive you own. No cloud, no account, no server.

Plug in a USB-C drive, pick a folder on it, and tap **Back up**. Photoxpo copies each full-quality **Original** to the drive, reads it back to check the copy is exact, and only then calls it **Backed up**. It keeps a small **Preview** of every photo on the phone, so you can browse your whole library by month with the drive unplugged, and **Load** an Original back onto the phone when you want it.

- Nothing leaves your phone except onto your own drive. The app has no internet access.
- Photoxpo only reads your photos. It never changes or deletes anything on your phone.
- Big libraries back up in stages. Pause or unplug any time and it carries on where it left off.

Needs Android 14 or newer and a USB-C drive or memory stick.

## Install

- [Google Play](https://play.google.com/store/apps/details?id=app.photoxpo) (coming soon)
- [GitHub Releases](https://github.com/meetmrdb/photoxpo/releases)

Builds from different stores are signed with different keys, so you can't update a Play install from another store, or the other way round. Uninstall first to switch, which clears the app's Previews.

## Build

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Release builds and signing are described in [docs/release.md](docs/release.md). The words the app and the code use (Archive Drive, Original, Preview, Backed up, Load) are defined in [GLOSSARY.md](GLOSSARY.md).

## Privacy

See [PRIVACY.md](PRIVACY.md). In short: Photoxpo collects nothing.

## License

[GNU GPL v3](LICENSE). Copyright © 2026 meetmrdb.

The Instrument Serif font is under the SIL Open Font License, see [assets/fonts/instrument-serif/OFL.txt](assets/fonts/instrument-serif/OFL.txt).
