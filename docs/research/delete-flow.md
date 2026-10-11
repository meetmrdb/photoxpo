# Deleting media from the phone: delete requests and Samsung trash

Research for ticket #6 (map: #1). Sources read on 2026-10-11. Terms follow `GLOSSARY.md`.

Confidence tags: **[confirmed]** read in a primary source cited next to it; **[inferred]** my reasoning from confirmed facts; **[unconfirmed]** no primary source found, needs a test on Nandhu's phone.

## Short answer

1. Use `MediaStore.createDeleteRequest` (permanent delete) from an on-screen Activity, in batches, never `MANAGE_MEDIA`. The system dialog is the confirmation the map already requires.
2. Do not trust `RESULT_OK`. The system's own code returns it even if the delete threw. Re-query MediaStore afterwards and mark only the Originals that are really gone.
3. `createTrashRequest` is the undo-friendly option, but trashed items keep using space for about 30 days, so it defeats the purpose for a full phone.
4. Whether Samsung Gallery's Recycle bin catches a delete made by another app is **not documented**. Test it (plan below). Detect it by checking for rows still present as trashed and by measuring free space.
5. Whether deleting a local file removes it from Google Photos or OneDrive is **not documented for deletes made by other apps**. Treat it as a risk and warn once.

## 1. The delete request APIs

| Fact | Source |
|---|---|
| `createDeleteRequest(ContentResolver, Collection<Uri>)` exists since API 30. It builds a `PendingIntent` that prompts the user to permanently delete the items; on approval `ContentResolver.delete` is called on them. | [MediaStore reference][ms-ref] [confirmed] |
| Launch it with `startIntentSenderForResult`; the operation has completely finished before the result arrives; test for `RESULT_OK`. | [MediaStore reference][ms-ref] [confirmed] |
| The prompt covers all items requested, including ones the app could already write. | [MediaStore reference][ms-ref] [confirmed] |
| `createTrashRequest(resolver, uris, value)` is the same shape; approval sets `IS_TRASHED` on the items instead of deleting. `createDeleteRequest` deletes "without placing them in the trash beforehand". | [MediaStore reference][ms-ref], [Access media files guide][media-guide] [confirmed] |
| **Batch limit:** an app that targets Android 16 (API 36, `BAKLAVA`) or higher may send at most 2000 URIs per request; more throws `IllegalArgumentException`. The same note appears on both the delete and trash methods. Below API 36 the reference documents no cap. | [MediaStore reference][ms-ref] [confirmed] |
| Each URI must name a specific media item by `_ID` (images, video, audio); a bare collection URI is rejected with `IllegalArgumentException`. | [MediaProvider.createRequest][mp-src] [confirmed] |
| An empty collection crashes: `createRequest` calls `it.next()` on the iterator first. Never call with an empty list. | [MediaStore.java][ms-src] [confirmed, source on `main`] |
| The returned `PendingIntent` is `FLAG_ONE_SHOT | FLAG_CANCEL_CURRENT`: build a fresh one for every launch, do not cache or re-fire it. | [MediaProvider.createRequest][mp-src] [confirmed] |
| The prompt text is "Allow *app* to delete N photos?" (or videos, or generic "items" for a mix), then a "Deleting N photos..." progress dialog. | [MediaProvider strings.xml][mp-strings] [confirmed, AOSP strings; One UI may restyle] |
| The dialog shows up to 3 thumbnails and a count (`MAX_THUMBS = 3`). The user cannot scroll through the whole batch. | [PermissionActivity.java][perm-src] [confirmed] |
| Declining returns `RESULT_CANCELED`. | [PermissionActivity.java][perm-src] [confirmed] |
| **`RESULT_OK` does not prove the delete worked.** The AOSP code applies the deletes inside a try/catch that only logs failures, then sets `RESULT_OK` unconditionally. | [PermissionActivity.java][perm-src] [confirmed on `main`; Samsung's build not checked] |

### Skipping the dialog: `MANAGE_MEDIA`

Since Android 12 a user can grant a special "media management" permission (`MANAGE_MEDIA`, granted in Settings via `ACTION_REQUEST_MANAGE_MEDIA`) that removes the confirmation dialog for write, trash and delete requests. It needs read access as well. [Access media files guide][media-guide], [Manifest.permission.MANAGE_MEDIA][manifest] [confirmed].

The map says deletion is "never silent" and gated by the system confirmation. **Do not request `MANAGE_MEDIA`** [inferred from the map's settled decision].

### Per-file alternative

A plain `ContentResolver.delete` on a file another app owns throws `RecoverableSecurityException` under scoped storage, one file at a time. The guide steers Android 11+ apps to the batch methods instead. [Access media files guide][media-guide] [confirmed]. The camera app owns the files in `DCIM/Camera` [inferred], so use the batch request.

## 2. Reading all camera media: permission rules

Photoxpo targets minimum SDK 34, so the Android 14 partial-access rules always apply.

| Fact | Source |
|---|---|
| Reading images and video needs `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (API 33+). | [Manifest.permission][manifest] [confirmed] |
| On Android 14+, an app that targets API 34+ lets the user pick "Select photos and videos" (partial access). The app queries MediaStore exactly as for full access, with no flag saying which one it has; the system just returns fewer rows. | [Partial photo/video access][partial] [confirmed] |
| To tell full from partial: full = `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` is granted; partial = only `READ_MEDIA_VISUAL_USER_SELECTED` is granted. | [Partial photo/video access][partial] [confirmed] |
| Declare `READ_MEDIA_VISUAL_USER_SELECTED` and request all three in one call. If it is not declared, the system runs the app in a compatibility mode: the read permissions show as granted for the session only, then are denied when the app is backgrounded or killed. | [Partial photo/video access][partial], [Manifest.permission][manifest] [confirmed] |
| Do not store the permission state; re-check on every `onResume` because the user can change it in Settings while the app is open. Access to user-selected items "expires eventually"; handle having no access to a URI. | [Partial photo/video access][partial] [confirmed] |
| `ACCESS_MEDIA_LOCATION` is needed for unredacted GPS in EXIF (relevant to the EXIF date/placement step, not to deleting). | [Access media files guide][media-guide] [confirmed] |
| The guide and the permission text say the `READ_MEDIA_*` permissions are restricted by Google Play policy. The map puts the Play Store out of scope (sideloaded APK). | [Access media files guide][media-guide] [confirmed] |

Consequences:

- Backing up `DCIM/Camera` needs **full** access. If only partial access is granted, the app must say so and ask for "Allow all", because items outside the selection are invisible to it and will never be Backed up [inferred].
- Check permission state at the start of every delete session, not once at install [confirmed above].
- Deleting only touches URIs the app already holds, but grants for those URIs can lapse under partial access, so a delete can fail on stale URIs [inferred].

## 3. Trash on Android versus Samsung's Recycle bin

### Android's trash (MediaStore)

- `IS_TRASHED` marks a trashed item, retained until `DATE_EXPIRES`. Default trashed expiry is "typically 30 days"; expired items are deleted automatically, typically at the next device idle period. [MediaColumns reference][mc-ref] [confirmed]
- By default, trashed items are filtered out of queries, updates and deletes. `QUERY_ARG_MATCH_TRASHED` with `MATCH_INCLUDE` or `MATCH_ONLY` shows them. [MediaStore reference][ms-ref] [confirmed]
- The guide says an OEM's preinstalled gallery can trash files with no dialog by setting `IS_TRASHED = 1` directly. [Access media files guide][media-guide] [confirmed]

So a trashed Original is invisible to a default query yet still occupies space until it expires [inferred from the three facts above]. A delete that "looks done" in the app may not have freed anything.

### Samsung Gallery's Recycle bin

| Fact | Source |
|---|---|
| Samsung Gallery keeps deleted photos and videos in a Recycle bin for 30 days before deleting them forever. | [Samsung: recover a deleted photo][sam-recover] [confirmed] |
| The Recycle bin must have been switched on (Gallery > Menu > Settings > Recycle bin) before the delete, or the photos are not recoverable from it. | [Samsung: recover a deleted photo][sam-recover] [confirmed] |
| Samsung does not document whether a delete made by **another app** through `createDeleteRequest` goes into this Recycle bin. | none found [**unconfirmed**] |
| Samsung does not document whether the Recycle bin is MediaStore's `IS_TRASHED` or Gallery's own store. The Android guide's OEM-gallery note makes `IS_TRASHED` plausible. | none found [**unconfirmed**; inference only] |
| Samsung does not document whether items in the Recycle bin still count against free storage. Ordinary logic says they do, but I found no Samsung statement. | none found [**unconfirmed**] |

The ticket's worry ("deleted items land in the Recycle bin and do not free space until it is emptied") is therefore plausible but **not confirmed from Samsung's documentation**. Only an on-device test settles it.

### On-device test plan (blocking for the first test build)

On the S24 Ultra, with the Recycle bin on, then off:

1. Note free space (Settings > Device care > Storage).
2. Take 3 test photos and 1 video, run `createDeleteRequest` from a scratch app, accept the dialog.
3. Check Gallery > Menu > Recycle bin: are the items there? Check free space again.
4. Query MediaStore with `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY`: do rows with `IS_TRASHED = 1` exist?
5. Repeat with `createTrashRequest`.
6. Record the Android and One UI versions (the device's current OS was not checked for this note).

## 4. Detecting that a file is gone

| Method | Source |
|---|---|
| Query MediaStore by `_ID`: no row means gone (default queries hide trashed items, so also run a `MATCH_ONLY` trashed query to tell "deleted" from "in the trash"). | [MediaStore reference][ms-ref] [confirmed] |
| Track change with `MediaStore.getVersion()` (cache invalidation) and `getGeneration(volume)` (monotonic while the version is unchanged). | [Access media files guide][media-guide] [confirmed] |
| Opening a URI that is gone throws `FileNotFoundException`; code must always handle having no access to a URI. | [Partial photo/video access][partial] [confirmed] |
| Free space: measure it before and after (for example `StatFs`). I have **not** researched the API or whether it updates immediately after a delete. | [**unconfirmed**] |

Decision rule [inferred]: an Original counts as removed from the phone only when its row is absent in **both** the default and the `MATCH_ONLY` trashed query. A row present as trashed is "parked", not freed.

## 5. Google Photos and Samsung sync

| Fact | Source |
|---|---|
| Deleting from the Google Photos app removes the item from the device **and** from every device with backup on. Items deleted there stay in its trash 30 days. (Samsung's own page still says 60 days for Google Photos; Google's page is newer and says 30.) | [Google Photos: delete][gp-delete], [Samsung: recover a deleted photo][sam-recover] [confirmed; sources disagree, Google's owns the claim] |
| Google's page lists the flows where a deletion propagates: it names deletes made **in Google Photos**. It does not say what happens when a different app deletes a local file that Google Photos had backed up. | [Google Photos: delete][gp-delete] [**unconfirmed** for deletes by other apps] |
| Samsung's Gallery cloud sync has moved from Samsung Cloud to OneDrive. | [Samsung: recover a deleted photo][sam-recover] [confirmed] |
| Deleting a synced file in the OneDrive app does not affect the copy on the phone. The reverse (deleting on the phone) is not stated. OneDrive's own Recycle bin keeps deletes 93 days. | [Samsung AE: Gallery to OneDrive][sam-onedrive-ae] (local copy unaffected), [Samsung US: OneDrive sync][sam-onedrive-us] (93 days) [confirmed for the stated direction; reverse **unconfirmed**] |
| A search-result summary claimed Gallery-to-OneDrive sync ends on 30 September 2026. I could not find it on a Samsung page I fetched. | [**unconfirmed**] |

Reading: the Archive Drive is the only copy Photoxpo promises. If Nandhu also has Google Photos backup or Samsung Gallery sync on, a delete here might remove his other copies too. No primary source says it does, none says it does not [inferred]. Mitigations: ask once, at the first delete, whether either service is on, and show a plain-words warning; record the answer in settings. Check the behaviour on the phone during the test above.

## 6. Recommended delete flow for large batches

Constants to tune: `DELETE_BATCH_SIZE = 500` (my choice, not from a source; well under the 2000 cap, small enough that one cancelled dialog costs little and a progress dialog finishes quickly).

1. **Preconditions (every session).** Full media access held (section 2). Archive Drive attached and identified. No import job running. Otherwise stop and say why.
2. **Select candidates.** Only Originals in state Backed up (copy read back from the Archive Drive, hash matched). Never "uploaded but unverified". Oldest first, matching the backup order.
3. **Drift check, per Original.** The MediaStore row still exists, with the `SIZE` and `DATE_MODIFIED` recorded when it was verified. If it changed (edited in the phone since backup), drop it from the batch and leave it for a new backup pass [inferred; the columns are standard MediaStore columns, I did not verify these particular names in this research].
4. **Drive check, per batch.** Just before the dialog, confirm each Original's file is still on the Archive Drive with the recorded size. A drive that has gone away pauses the job (the map's job model).
5. **Ask.** Launch `createDeleteRequest` for up to `DELETE_BATCH_SIZE` URIs, from an Activity the user is looking at (a Compose screen with a plain "Free up space: N photos, N videos" button, with the totals on screen because the system dialog only shows three thumbnails). Use `ActivityResultContracts.StartIntentSenderForResult`. Build a new `PendingIntent` per launch (it is one-shot).
6. **Verify, never trust the result code.** After the activity returns, re-query MediaStore for that batch's `_ID`s with default and trashed-only queries. Mark rows as removed from the phone only where both queries return nothing. Leave everything else Backed up, and tell the user how many were not removed.
7. **If the user cancels** (`RESULT_CANCELED`), keep the batch Backed up, stop the loop, and do not re-prompt automatically. One dialog per user tap. This keeps deletion "never silent".
8. **Next batch** only on an explicit tap, or an explicit "continue with the rest" that still re-opens the system dialog each batch. There is no way around the dialog without `MANAGE_MEDIA`, which we are not requesting.
9. **Check space was actually freed.** If trashed-only queries show parked rows, or free space did not move, tell the user plainly: "These are in the Recycle bin. Open Gallery > Menu > Recycle bin > Empty to free the space." Do not claim space was freed from the dialog result alone.
10. **Previews stay.** Deleting the Original never touches the Preview kept for it.
11. **First test build hides the whole action** (settled on the map). Ship the on-device test above in a scratch build first, then wire this flow.

Why not `createTrashRequest` by default: it gives the user an undo, but trashed items keep their space for about 30 days (and Samsung's Recycle bin adds its own 30 days if it is involved), which fails the one job of the feature on a full phone [inferred]. Because an Original is only deleted after a hash-verified read-back from the Archive Drive, the undo is less valuable than the freed space. If the on-device test shows `createDeleteRequest` lands in Samsung's Recycle bin anyway, the trade-off disappears and the flow needs only the "empty the bin" instruction.

## Open items for the map

- On-device test (section 3) before any delete code ships.
- Decide whether to ask about Google Photos / OneDrive sync at first delete (section 5).
- Confirm `DELETE_BATCH_SIZE` on the real phone with a realistic count of the ~600 GB library (progress-dialog time per 500 items is unmeasured).
- Target SDK choice: at API 36+ the 2000 cap is enforced; at lower targets no cap is documented but very large lists were not tested [unconfirmed].

## Sources

[ms-ref]: https://developer.android.com/reference/android/provider/MediaStore
[mc-ref]: https://developer.android.com/reference/android/provider/MediaStore.MediaColumns
[manifest]: https://developer.android.com/reference/android/Manifest.permission
[media-guide]: https://developer.android.com/training/data-storage/shared/media
[partial]: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
[ms-src]: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/apex/framework/java/android/provider/MediaStore.java
[mp-src]: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/MediaProvider.java
[perm-src]: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/PermissionActivity.java
[mp-strings]: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/res/values/strings.xml
[sam-recover]: https://www.samsung.com/uk/support/mobile-devices/how-can-i-recover-deleted-photo/
[sam-onedrive-ae]: https://www.samsung.com/ae/support/mobile-devices/how-to-sync-gallery-files-to-one-drive-on-my-samsung-phone
[sam-onedrive-us]: https://www.samsung.com/us/support/answer/ANS00060519/
[gp-delete]: https://support.google.com/photos/answer/6128858

Notes on sources: the Android developer pages are the platform owner's documentation. The AOSP source links point at `main`, which may differ from the Android version on the phone and from Samsung's modified build. The Samsung and Google pages are the vendors' own help articles; their text can change.
