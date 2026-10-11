# Reading capture dates and making Previews for Samsung media

Research for ticket [#4](https://github.com/meetmrdb/photoxpo/issues/4), part of the MVP map (#1).
Researched 2026-10-11. Terms follow `GLOSSARY.md` (Archive Drive, Original, Preview, Backed up, Load). Android API names that contain the word "thumbnail" appear in code formatting only, because the glossary says to call our artefact a Preview.

## How to read this note

- Every claim names its source. Android reference pages were read from `developer.android.com`. Framework behaviour was read from AOSP source (`android.googlesource.com`, `refs/heads/main` as fetched on 2026-10-11).
- **Unconfirmed** means I could not verify it from a primary source. These items are collected in the last section with a proposed way to settle each one.
- Caveat on AOSP `main`: the S24 Ultra ships Android 14 (One UI 6.1) and may since have been updated. `main` is newer than what runs on his phone. Behaviour described from source is the intended behaviour, not a measurement on his device.
- I found no first-party Samsung document for the file layout of Samsung motion photos or for Samsung's EXIF tags. Everything Samsung-specific below is either from Samsung's own support or spec pages (what the camera offers) or is marked unconfirmed.

## Short answers

| Question | Answer |
|---|---|
| Date source for photos | EXIF `DateTimeOriginal`, read as a wall-clock string and **not** converted through a timezone. |
| Date source for videos | `MediaMetadataRetriever.METADATA_KEY_DATE` (UTC), which is also what MediaStore `DATE_TAKEN` holds for videos. |
| Last-resort date | `DATE_MODIFIED` (a `File.lastModified()` value in **seconds**). |
| Decoder for stills | `ImageDecoder` with `setTargetSize` inside `onHeaderDecoded`, `ALLOCATOR_SOFTWARE`, one image at a time. |
| Encoder | `Bitmap.compress(JPEG, 70)`; this writes no EXIF. |
| Video poster frame | `MediaMetadataRetriever.getScaledFrameAtTime(-1, OPTION_CLOSEST_SYNC, w, h)`, one video at a time. For 8K, budget about 130 MB of transient pixel memory (see section 4). |
| Motion photo Preview | Decode the file's own primary still. Do not touch the appended video. |
| Carry EXIF into the Preview | No. Keep the date in the database and manifest. |

---

## 1. Capture-date sources, fallback order and timezone pitfalls

### What each source actually is

**EXIF `DateTimeOriginal`.** Format `YYYY:MM:DD HH:MM:SS`, 24-hour, no timezone in the tag itself. Unknown dates are blank-filled. Source: [ExifInterface reference, `TAG_DATETIME_ORIGINAL`](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface). The companion tag `OffsetTimeOriginal` ("±HH:MM", the offset from UTC of the `DateTimeOriginal` value) exists since ExifInterface 1.2.0: same reference, `TAG_OFFSET_TIME_ORIGINAL`. ExifInterface reads JPEG, PNG, WebP, HEIC, DNG and several other raw formats (AVIF on API 31+): same reference, class description.

**MediaStore `DATE_TAKEN`.** Epoch **milliseconds**. The reference says it is the indexed value of `MediaMetadataRetriever.METADATA_KEY_DATE` or `ExifInterface.TAG_DATETIME_ORIGINAL`, and warns: "images must define both `TAG_DATETIME_ORIGINAL` and `TAG_OFFSET_TIME_ORIGINAL` to reliably determine this value in relation to the epoch." Source: [`MediaStore.MediaColumns.DATE_TAKEN`](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns).

**MediaStore `DATE_MODIFIED`.** Epoch **seconds**, "indexed value of `File.lastModified()`". Same reference. `DATE_ADDED` is also in seconds.

**Video date.** `METADATA_KEY_DATE` is documented only as "the date when the data source was created or modified" with no format stated: [MediaMetadataRetriever reference](https://developer.android.com/reference/android/media/MediaMetadataRetriever). How the platform parses it is visible in MediaProvider: the value is parsed with the patterns `yyyyMMdd'T'HHmmss.SSS` and `yyyyMMdd'T'HHmmss`, both in the **UTC** timezone ([`ModernMediaScanner.java`, `parseOptionalDate` and the static formatters](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/scan/ModernMediaScanner.java)).

### How MediaProvider builds `DATE_TAKEN` for an image (this is the pitfall)

From [`ModernMediaScanner.parseOptionalDateTaken`](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/scan/ModernMediaScanner.java) and [`ExifUtils.java`](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/util/ExifUtils.java):

1. If `OffsetTimeOriginal` is present, `DATE_TAKEN` is exact: the wall-clock string plus that offset.
2. Otherwise it **guesses** the offset: first from `GPSDateStamp`/`GPSTimeStamp` (difference to the wall-clock value, if under 24 h, rounded to 15-minute steps), then from the file's modified time (same 24 h and 15-minute rule).
3. If neither works, `DATE_TAKEN` is **null**.
4. Sub-second digits come from `SubSecTimeOriginal`. Strings that are all zeros are treated as unknown.

Consequence: for an image without `OffsetTimeOriginal`, `DATE_TAKEN` can be hours off or missing, and any code that turns it into a local calendar date using the phone's current timezone can land on the wrong day or month.

### Other pitfalls, each from a source above

- **Units differ.** `DATE_TAKEN` is milliseconds; `DATE_MODIFIED` and `DATE_ADDED` are seconds (MediaColumns reference). Comparing them directly is a bug.
- **A wall-clock value is the right thing to file by.** The drive layout is `YYYY/YYYY-MM`. A person filing "photos from March" means the local time at the place they stood. EXIF `DateTimeOriginal` is exactly that. Converting it to an instant and back through the phone's *current* timezone can move a photo taken abroad, or near midnight at a month boundary, into the wrong month folder. (This is my reasoning from the definitions above, not a documented statement.)
- **Videos only carry an instant (UTC).** There is no wall-clock field in `METADATA_KEY_DATE`. Rendering it in the phone's timezone is correct only if the phone is in the timezone where the video was shot. Month-boundary errors for travel videos are therefore possible and cannot be fixed from the metadata alone. (Whether Samsung writes any extra local-time or offset field in video files: unconfirmed.)
- **GPS tags are hidden by default.** With scoped storage "the system hides location information by default"; reading unredacted EXIF needs `ACCESS_MEDIA_LOCATION` granted at runtime plus `MediaStore.setRequireOriginal(uri)`, and there is "no guarantee" the user grants it ([Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media), sections "Media location permission" and "Location where media was captured"). So the GPS-based offset guess is not available to our own code unless that permission is granted. MediaProvider does the guess itself with full access, which is another reason `DATE_TAKEN` is a useful second source.
- **This also affects the Original, not just dates.** If the app copies the bytes through a plain MediaStore URI, the copy may have its GPS data redacted, so it would not be identical to what the camera wrote. This only matters if the redaction applies to the stream we read (the documentation says location is hidden by default; I did not verify at the byte level). The MVP needs a decision on `ACCESS_MEDIA_LOCATION` + `setRequireOriginal` before "Backed up" can honestly mean "identical to the Original". Flagged for the map; it is outside this ticket's question.
- **EXIF may be absent or garbage** (blank, zeros, 1970, far-future). The documentation says a blank field is treated as unknown (ExifInterface reference); MediaProvider ignores all-zero strings (`ExifUtils`).

### Recommended fallback order

For a **still** (JPEG, HEIC, DNG):

1. EXIF `DateTimeOriginal` as a wall-clock `LocalDateTime` (use it directly for the folder `YYYY-MM`). Keep `OffsetTimeOriginal` too, if present, so an exact instant can be stored.
2. MediaStore `DATE_TAKEN` (ms), rendered in the phone's timezone.
3. `DATE_MODIFIED` (seconds), rendered in the phone's timezone.

For a **video**:

1. `METADATA_KEY_DATE` (UTC), equivalently MediaStore `DATE_TAKEN`; render in the phone's timezone.
2. `DATE_MODIFIED`.

Sanity-check each candidate (reject all-zero, year before 2000 or after now plus a small margin) and fall through. Record *which* source was used per file so a later correction pass is possible.

This is a small refinement of the settled map line "Date from EXIF, then date taken, then modified time": videos have no EXIF, and for images the EXIF value should be read as wall-clock, not converted to an instant.

---

## 2. What an S24 Ultra produces, and which decoders handle it

What Samsung's own pages say:

- Rear cameras 200 MP wide, 12 MP ultra-wide, 50 MP 5x telephoto, 10 MP 3x telephoto; front 12 MP; video up to 8K (7680 x 4320) at 30 fps, slow motion 240 fps at FHD and 120 fps at UHD ([Samsung Galaxy S24 Ultra specs](https://www.samsung.com/ph/smartphones/galaxy-s24-ultra/specs/)).
- Camera settings include "Advanced picture options: you can choose to save images in HEIF or RAW formats", "Advanced video options: High efficiency videos, High bitrate videos, Reduce file size, HDR10+ videos", and a Motion photo on/off switch ([Camera modes and settings on Galaxy phones and tablets](https://www.samsung.com/us/support/answer/ANS10001353/)). That page is generic across Galaxy models, not S24-specific.

What follows for file types, with the Android side from [Supported media formats](https://developer.android.com/guide/topics/media/platform/supported-formats):

| Output | Android decoding | Notes |
|---|---|---|
| JPEG | `ImageDecoder`, `BitmapFactory`: supported, base + progressive | Presumed default photo format; HEIF is described as an opt-in setting, but the default state is not stated on Samsung's page (unconfirmed). Handle both. |
| HEIC / HEIF | `ImageDecoder` from Android 9, but "Device optional. Check `ImageDecoder.isMimeTypeSupported("image/heif")`"; 10-bit needs the optional HEVC Main10 profile | Check at runtime; fall back to `MediaMetadataRetriever` on failure. ExifInterface reads HEIC. |
| 50 MP / 200 MP stills | Same decoders | Output pixel dimensions and whether they are JPEG or HEIC: unconfirmed. At about 200 million pixels, a full ARGB_8888 decode would be about 800 MB (200e6 x 4 bytes), so a full decode must never happen. A 12 MP shot is about 48 MB. |
| Motion photo | Primary still decodes like any JPEG/HEIC; the appended video is a separate MP4 | See section 4. |
| Expert RAW (DNG) | ExifInterface can read DNG; whether `ImageDecoder` decodes DNG on this phone is **unconfirmed** (the Android supported-formats page does not list DNG) | Plan a fallback: embedded JPEG via `ExifInterface.getThumbnailBytes`, else a placeholder Preview. Where Expert RAW writes files (inside `DCIM/Camera` or not): unconfirmed. |
| Video H.264 / H.265 (HEVC), incl. HDR10+ (10-bit HEVC) and 8K | Android lists H.264 and H.265 HEVC decoders ("Android 5.0+" for HEVC) | Frame extraction of 10-bit HDR10+ and 8K HEVC works as a platform feature on paper, but the colour of the extracted frame (tone mapping) is **unconfirmed**; test on the device. Container is MP4 in practice (unconfirmed for Samsung). |
| Ultra HDR / gain-map JPEG | Android 14 `Bitmap` can carry a `Gainmap`; `ImageDecoder` decodes it (`ImageDecoder.cpp` has gain-map handling) | Whether the S24 camera writes gain-map JPEGs: unconfirmed. See the Preview encoding note in section 3. |

---

## 3. Making a 720 px Preview from a still

### Options compared

**`ImageDecoder` (API 28+, our minimum SDK is 34, so always available).**
- It decodes JPEG, PNG, WebP, GIF and HEIF ([ImageDecoder reference](https://developer.android.com/reference/android/graphics/ImageDecoder)).
- Size is set in `onHeaderDecoded`: `setTargetSize(w, h)` or `setTargetSampleSize(n)`. The reference says the sample size "has the same effect as calling `setTargetSize` with dimensions based on the sampleSize" and that only the last call is respected, both "must be called inside `onHeaderDecoded`" (same reference).
- Internals (AOSP [`ImageDecoder.cpp`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/hwui/ImageDecoder.cpp)): `setTargetSize` asks the codec for the best native sample size (`computeSampleSize`) and decodes at that size. If the result is not exactly the target, only the leftover scale is done on the already-small bitmap. The reported `ImageInfo.getSize()` is already in display orientation (width and height are swapped for EXIF rotations that swap them).
- It applies EXIF orientation itself (`SkEncodedOriginToMatrix` in the same file). The reference page itself does not mention orientation; the evidence is the source.
- It can return a HARDWARE bitmap by default; `setAllocator(ALLOCATOR_SOFTWARE)` forces a software bitmap. The platform's own preview generator does exactly that, with the comment that software is "the more flexible" choice ([`ThumbnailUtils.Resizer`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/ThumbnailUtils.java)).
- `createSource(ContentResolver, Uri)` works with content URIs, so MediaStore items need no file path.

**`BitmapFactory` with `inSampleSize`.** Two passes: first `inJustDecodeBounds = true` to read the size, then decode with `inSampleSize` ([Loading large bitmaps efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap)). The guide's helper picks a power of two; the final result is therefore usually larger than 720 px and needs a second resize. `BitmapFactory` does not apply EXIF orientation (the rotation work is done by hand in the platform's own `ThumbnailUtils` where it uses ExifInterface). It adds nothing over `ImageDecoder` at minimum SDK 34.

**Coil.** Coil is "an image loading library" with "memory and disk caching, downsampling the image" ([Coil overview](https://coil-kt.github.io/coil/)). It is built for displaying images in views. Use it in the gallery screen to show Previews; do not route Preview creation through it, since we want one decode and one JPEG write per Original with no caching layer. (The Coil pages I read do not describe an API for writing a re-encoded file; I did not look at its source.)

**`ContentResolver.loadThumbnail` / `ThumbnailUtils`.** `loadThumbnail(uri, size, signal)` (API 29) passes the size as a hint to the provider and "defensively resizes" the result ([ContentResolver reference](https://developer.android.com/reference/android/content/ContentResolver)). We cannot control the sizing, quality or the source (it may return the MediaStore's own cached smaller image), so it is a poor fit for a fixed 720 px / q70 Preview. `ThumbnailUtils` is useful as a reference implementation (below), not as the tool.

### What the platform itself does (a good template)

[`ThumbnailUtils.createImageThumbnail`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/ThumbnailUtils.java): read the EXIF orientation; for HEIC/AVIF use `MediaMetadataRetriever.getThumbnailImageAtIndex`; otherwise try the EXIF-embedded JPEG; otherwise `ImageDecoder.decodeBitmap` with a listener that sets `ALLOCATOR_SOFTWARE` and `setTargetSampleSize(max(widthSample, heightSample))`.

### Recommended recipe (Kotlin shape)

```kotlin
val source = ImageDecoder.createSource(contentResolver, uri)
val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
    val (w, h) = info.size.width to info.size.height      // already display-oriented
    val scale = 720f / maxOf(w, h)
    if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1),
                                          (h * scale).roundToInt().coerceAtLeast(1))
}
// bmp.gainmap?.let { bmp.setGainmap(null) }   // API 34, see below
out.use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }
bmp.recycle()
```

Notes:
- Never enlarge: skip `setTargetSize` when the image is already 720 px or smaller.
- Run one decode at a time per worker, off the main thread, from the copy on the phone (flash storage), not from the Archive Drive. The 720 px bitmap is about 720 x 540 x 4 = 1.5 MB, so the only large transient is inside the codec. Parallelism above 2 workers: unmeasured, tune on the device.
- The platform code path for HEIC (`MediaMetadataRetriever.getThumbnailImageAtIndex`) exists because HEIC grids can have their own embedded image. For a 720 px target `ImageDecoder` on HEIC should be fine, but I have not confirmed its memory behaviour on large HEIC.

### JPEG output settings

- `Bitmap.compress(JPEG, quality, stream)`: the quality is "a hint to the compressor, 0-100. The value is interpreted differently depending on the `CompressFormat`" and the call "may take several seconds", so it belongs on a worker thread ([Bitmap reference](https://developer.android.com/reference/android/graphics/Bitmap)). The map's q70 is a sensible starting constant; the resulting file size is not documented and must be measured on sample photos.
- Under the hood ([`Bitmap.cpp`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/hwui/Bitmap.cpp)): JPEG goes through `SkJpegEncoder` with only `fQuality` set. In Skia's current source ([`SkJpegEncoder.h`](https://skia.googlesource.com/skia/+/refs/heads/main/include/encode/SkJpegEncoder.h), [`SkJpegEncoderImpl.cpp`](https://skia.googlesource.com/skia/+/refs/heads/main/src/encode/SkJpegEncoderImpl.cpp)) the default chroma subsampling is 4:2:0, an ICC profile is appended from the bitmap's colour space, and no EXIF is written. The Skia revision inside Android 14 was not checked.
- **Gain maps.** `Bitmap.compress` in `Bitmap.cpp` writes a JPEG with a gain map when the bitmap has one (`hasGainmap()` branch). A Preview should be a plain JPEG, so drop it first: `Bitmap.setGainmap(null)` (API 34, [Bitmap reference](https://developer.android.com/reference/android/graphics/Bitmap)).
- **Colour.** Samsung photos may be tagged wide-gamut (Display P3); that is unconfirmed. Because the encoder embeds the ICC profile of the decoded bitmap, colours should survive; confirm by comparing a Preview with the Original on the phone.
- WebP lossy (`WEBP_LOSSY`, API 30) would give smaller files at the same visual quality, but the map and glossary settle on JPEG for compatibility. Not pursued.

---

## 4. Video poster frames and motion photos

### Video

API: `MediaMetadataRetriever` with `setDataSource(context, uri)`, and `close()` when done ([reference](https://developer.android.com/reference/android/media/MediaMetadataRetriever)).

- `getFrameAtTime(timeUs, option)`: with a negative time, "any frame that the implementation considers as representative may be returned". Options: `OPTION_CLOSEST_SYNC` (nearest key frame), `OPTION_CLOSEST` (any frame, "often has larger performance overhead"). The reference advises: "If you don't need a full-resolution frame ... use `getScaledFrameAtTime()` instead" (API 27).
- `BitmapParams.setPreferredConfig` lets you ask for another bitmap config; the default is `ARGB_8888` ([BitmapParams](https://developer.android.com/reference/android/media/MediaMetadataRetriever.BitmapParams)).
- The platform's own video preview does: embedded cover art via `getEmbeddedPicture()` first, then `getFrameAtTime(-1, OPTION_CLOSEST_SYNC)` if the target is larger than the video, else `getScaledFrameAtTime(-1, OPTION_CLOSEST_SYNC, w, h)` ([`ThumbnailUtils.createVideoThumbnail`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/ThumbnailUtils.java)).
- Rotation is already applied to the returned frame: the native code rotates pixels by the track's rotation angle ([`android_media_MediaMetadataRetriever.cpp`, `getBitmapFromVideoFrame`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/jni/android_media_MediaMetadataRetriever.cpp)). A portrait phone video comes out portrait.
- **Memory catch for `getScaledFrameAtTime`.** The same function first allocates the full-size frame bitmap, rotates it, and only then calls `Bitmap.createScaledBitmap` to shrink it. So scaling does not reduce the peak. For an 8K frame (7680 x 4320), ARGB_8888 is 7680 x 4320 x 4 = 132,710,400 bytes (about 127 MiB), plus a native frame buffer of similar size in the retriever service (the frame is held in shared memory before it is copied; `FrameDecoder.cpp`). Plan for about 130 to 260 MB transient per 8K video and process **one video at a time**. A 4K frame is a quarter of that. `BitmapParams.setPreferredConfig(RGB_565)` would halve the Java-side bitmap but can band gradients; only use it if measurement shows pressure. (The doubled figure is my reading of the source, not a measurement.)
- Which frame: a representative frame (`-1`) is simplest. A fixed offset such as 1 s can land on a black fade-in; a representative frame avoids this. Use `OPTION_CLOSEST_SYNC` for speed (key frames only).
- Fallback if `getScaledFrameAtTime` returns null (some HDR10+ or odd-profile files): try `getFrameAtTime` with another option; if still null, store a placeholder Preview and flag the file. Whether any S24 video modes fail extraction: unconfirmed; test on the device.

### Motion photo layout

The Android reference defines Motion Photo 1.0 ([Motion Photo format 1.0](https://developer.android.com/media/platform/motion-photo-format)):

- A primary still (JPEG, HEIC or AVIF) with a secondary MP4/MOV video **appended** to it. The video item must be last, with no bytes after it.
- XMP in the primary image: `Camera:MotionPhoto` (1 = motion photo), `Camera:MotionPhotoVersion`, `Camera:MotionPhotoPresentationTimestampUs` (which video frame matches the still; if absent, the middle of the video is used). The `Container:Directory` lists items with `Item:Mime`, `Item:Semantic` (`Primary`, `MotionPhoto`), `Item:Length`, and `Item:Padding`.
- To find the video: "start from the end of the file and move back `<Length>` bytes". For HEIC/AVIF the video sits in an `mpvd` box and `Padding` must be 8.
- `Camera:MotionPhoto=1` "is not definitive": editors can leave it set after the video is stripped, so "readers must always confirm that a video is present". The older `MicroVideo*` attributes "must be ignored".
- Recommended file names end in `MP` (for example `...MP.jpg`) but "readers may ignore" the metadata if the pattern is not followed.

**Does Samsung write this format?** I found no first-party Samsung document. Samsung's support pages describe only the on/off switch ("a short video clip of the action right before releasing the Capture button"). Search results from third-party forums mention a Samsung-specific trailer with a `MotionPhoto_Data` marker that changed between One UI versions. Those are not primary sources, so treat the Samsung layout as **unconfirmed**. It does not change the Preview design, because the Preview never reads the video (below).

### What the Preview should be for a motion photo

The primary image is a normal still. Make the Preview from that, exactly as for any JPEG/HEIC. Do **not** extract a frame from the appended video: the still is the picture the user chose and is cheaper and sharper to obtain. Two checks to run in the spike (section 7): that `ImageDecoder` decodes a Samsung motion-photo file cleanly with the trailing video bytes present (the Android spec describes the file as an image followed by video, but I have not confirmed decoder tolerance from a primary source), and that EXIF reads work on it (they should: MediaProvider scans these files with ExifInterface).

To let the gallery show a "motion" badge, record a boolean at backup time: XMP says `Camera:MotionPhoto = 1` (via `ExifInterface.TAG_XMP`) **and** a video appears to exist (the container item length is positive and fits in the file). This is optional and not needed for the MVP Preview.

---

## 5. Whether to carry EXIF into the Preview

Recommendation: **no**.

- `Bitmap.compress` does not write EXIF (above), and `ImageDecoder` already applied the orientation to the pixels, so the Preview is upright with no orientation tag needed.
- It removes GPS and device details from the small files, which will also sit in `Photoxpo/.photoxpo/` on the Archive Drive and might be shared.
- The capture date, the source used (section 1) and the Original's identity belong in the Room database and the drive manifest, so the gallery never has to read the Preview's EXIF.
- If a Preview ever needs its own date (for example when rebuilding from the drive on a new phone, a "Not yet specified" item on the map), ExifInterface can write `DateTimeOriginal` and `OffsetTimeOriginal` into a JPEG (it can write JPEG, PNG and WebP; ExifInterface reference). That is a cheap later addition. Do not copy all tags.

---

## 6. Recommendation

1. **Dates.** For stills, file by EXIF `DateTimeOriginal` read as a wall-clock `LocalDateTime` (no timezone conversion for the `YYYY/YYYY-MM` folder), falling back to MediaStore `DATE_TAKEN`, then `DATE_MODIFIED` (seconds, not ms). For videos use `METADATA_KEY_DATE` (UTC; same as `DATE_TAKEN`), then `DATE_MODIFIED`. Validate every value, store the chosen source and the value in Room, and accept that travel videos near a month boundary can land in the neighbouring month.
2. **Still Previews.** `ImageDecoder.decodeBitmap` with `ALLOCATOR_SOFTWARE` and `setTargetSize` so the long edge is 720 px (never enlarge), read from the phone copy, one decode at a time, then `Bitmap.compress(JPEG, 70)` (clear the gain map first), then recycle. Do not use Coil, `BitmapFactory` or `loadThumbnail` for this.
3. **Video Previews.** `MediaMetadataRetriever.getScaledFrameAtTime(-1, OPTION_CLOSEST_SYNC, ...)`, then the same JPEG step. One video at a time; budget 130 to 260 MB transient for 8K. Try `getEmbeddedPicture()` first only if desired (the platform does).
4. **Motion photos.** Preview is the file's primary still; ignore the appended video. Optionally flag `isMotionPhoto`.
5. **EXIF.** Do not carry it. The date lives in the database and manifest.
6. **Before "Backed up" is declared truthful**, decide on `ACCESS_MEDIA_LOCATION` and `MediaStore.setRequireOriginal` so the Original on the Archive Drive has the same bytes (including GPS) the camera wrote. Raised for the map; outside this ticket.
7. **Run the validation spike** below on Nandhu's actual phone before locking the constants (q70, 720 px) and the fallbacks.

### Possible conflicts with settled map text

- Map: "Date from EXIF, then date taken, then modified time." Refinement suggested in section 1 (videos have no EXIF; images are wall-clock). Not a contradiction of an ADR (no ADRs exist yet).
- Map: "Videos and motion photos get a still poster frame." Consistent; for motion photos the poster frame is the primary still.

---

## 7. Unconfirmed items and how to settle them

Copy about 30 files off the phone (a few of each: default JPEG, HEIF with the setting on, 12 MP, 50 MP, 200 MP, motion photo, portrait-orientation photo, Expert RAW, 4K and 8K HEVC video, HDR10+ video, a video shot in portrait, an edited photo) and run a throwaway test app or script over them.

| Unconfirmed | How to settle it |
|---|---|
| Does the S24 write `OffsetTimeOriginal` for photos? Does it write any offset for videos? | Dump EXIF and the MP4 header for each sample; list which have the offset. |
| Default photo format (JPEG vs HEIF) and pixel dimensions of 12/50/200 MP output | Check `ImageInfo.size` and MIME type per sample. |
| Samsung motion-photo layout and decoder tolerance for trailing video bytes | Decode with `ImageDecoder`; check XMP `Camera:MotionPhoto`; try the end-of-file `Item:Length` rule. |
| Whether `ImageDecoder` decodes Expert RAW DNG; where those files are saved | Try it; check the folder in MediaStore `RELATIVE_PATH`. |
| Does the S24 camera write Ultra HDR / gain-map JPEGs? | Check `Bitmap.hasGainmap()` on decoded samples. |
| Colour correctness of Previews (Display P3 and HDR10+ frames) | Visual comparison of Preview vs Original on the phone. |
| 8K and HDR10+ frame extraction success and peak memory | Run `getScaledFrameAtTime` on the samples while watching native memory. |
| JPEG q70 file sizes at 720 px | Measure across the samples; the glossary-level constant stays tunable. |
| Whether the MediaStore stream is GPS-redacted at the byte level | Hash the file via a MediaStore URI with and without `setRequireOriginal`, and compare to a copy pulled over USB. |
| Behaviour on the phone's actual Android build (AOSP `main` was read) | The spike above covers it. |

## Sources

- Android: [ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder), [Bitmap](https://developer.android.com/reference/android/graphics/Bitmap), [Bitmap.CompressFormat](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat), [BitmapFactory.Options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options), [Loading large bitmaps efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap), [ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface), [MediaStore.MediaColumns](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns), [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever), [MediaMetadataRetriever.BitmapParams](https://developer.android.com/reference/android/media/MediaMetadataRetriever.BitmapParams), [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver), [Supported media formats](https://developer.android.com/guide/topics/media/platform/supported-formats), [Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media), [Motion Photo format 1.0](https://developer.android.com/media/platform/motion-photo-format).
- AOSP (`refs/heads/main`): `frameworks/base/libs/hwui/hwui/ImageDecoder.cpp`, `frameworks/base/libs/hwui/hwui/Bitmap.cpp`, `frameworks/base/media/jni/android_media_MediaMetadataRetriever.cpp`, `frameworks/base/media/java/android/media/ThumbnailUtils.java`, `frameworks/av/media/libstagefright/FrameDecoder.cpp`, `packages/providers/MediaProvider/src/com/android/providers/media/scan/ModernMediaScanner.java`, `packages/providers/MediaProvider/src/com/android/providers/media/util/ExifUtils.java`; Skia `include/encode/SkJpegEncoder.h` and `src/encode/SkJpegEncoderImpl.cpp`.
- Samsung: [Galaxy S24 Ultra specs](https://www.samsung.com/ph/smartphones/galaxy-s24-ultra/specs/), [Camera modes and settings](https://www.samsung.com/us/support/answer/ANS10001353/), [Galaxy S24 camera specs explained](https://www.samsung.com/us/explore/mobile/buying-guide/samsung-galaxy-s24-camera-specs-explained/).
- Coil: [Coil overview](https://coil-kt.github.io/coil/), [Coil video frames](https://coil-kt.github.io/coil/videos/).
