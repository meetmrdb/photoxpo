# SAF on the Archive Drive: access persistence, listing and write speed

Research ticket: [#3](https://github.com/meetmrdb/photoxpo/issues/3) (map: [#1](https://github.com/meetmrdb/photoxpo/issues/1)).
Researched 2026-10-11. Scope: Android 14 and 15, the Storage Access Framework (SAF), an exFAT/FAT32 USB mass-storage disk (the **Archive Drive**).

Vocabulary follows `GLOSSARY.md`: **Archive Drive**, **Original**, **Preview**, **Backed up**, **Load**.

## How to read this

- **[S]** = confirmed by reading AOSP source or Android reference text (cited).
- **[I]** = inferred by me from source I read; not run on a device.
- **[?]** = could not confirm from a primary source. Needs an on-device test.

AOSP is not Samsung One UI. One UI modifies the picker (DocumentsUI), may modify the storage stack, and is not open source. Nothing below is a statement about One UI beyond what AOSP guarantees, and the Samsung section says so explicitly.

Source line numbers are for the `android14-release` / `android15-release` branch heads as fetched on 2026-10-11 and will drift. Symbols are the stable handle. Full URLs are in the Sources table at the end ([A1] to [A19]).

## Answers at a glance

| Question | Answer | Confidence |
|---|---|---|
| Does a persisted folder permission survive unplug and replug of the same drive? | Yes in AOSP. The grant is stored by URI, the URI embeds the filesystem UUID, the UUID is stable for the same filesystem, and the grant service has no volume-awareness that would drop it on unmount. Reformatting the drive changes the UUID and loses it. | [S] for AOSP, [?] for One UI |
| Can the user grant the drive root? | Yes for USB. The "no volume root" rule in the docs targets SD cards; `ExternalStorageProvider` explicitly exempts USB, root included. | [S] AOSP, [?] Samsung picker |
| How do we detect the Archive Drive is attached and usable? | `StorageManager.registerStorageVolumeCallback` plus a probe query on the tree. A query against an unmounted drive returns a `null` cursor (not an exception). Mounted read-only must be treated as not usable. | [S] |
| Cost of listing tens of thousands of files | `DocumentFile.listFiles()` is the wrong tool: 1 query for ids, then 1 more provider query per property per file. A raw `DocumentsContract` children query returns all properties in one query per directory. No timing numbers in any primary source. | [S] mechanism, [?] timings |
| Best API for bulk copy and read-back hashing | Raw `DocumentsContract` for structure, `ContentResolver.openFileDescriptor` and `FileInputStream`/`FileOutputStream` on the `ParcelFileDescriptor` for bytes. Not `DocumentFile`. | [S] |
| Rename and move | Both work on this provider and are plain `rename(2)` calls inside the provider. Both return a new document id and invalidate the old URI. Rename silently renames to `name (1).ext` on collision instead of failing; move fails on collision. | [S] |
| Limits on persisted grants | 512 per app on Android 14 and 15; oldest are pruned beyond that. We need one. | [S] |
| Mid-write disconnect | Assume the app process is killed. On unmount vold sends SIGINT, then SIGTERM, then SIGKILL to every process with an open file under the volume. Anything that survives sees I/O errors. | [S] vold source, [I] that this hits our process, [?] on-device timing |
| Throughput | Not stated in any primary source. Depends on the disk, the USB link, the exFAT kernel driver, and whether FUSE passthrough is active. Must be measured. | [?] |

## 1. Access persistence

### What the grant is

- A tree grant from `ACTION_OPEN_DOCUMENT_TREE` can be persisted with `ContentResolver.takePersistableUriPermission`; the source comment says the grant "will be remembered across device reboots" ([A3], `takePersistableUriPermission`). The official guide says the same and warns that access is lost "if the associated document is moved or deleted" ([D1]).
- The grant is stored per package by URI in `UriGrantsManagerService` ([A5]). A grep of the whole file for `volume`, `StorageManager`, `unmount`, `eject` returns nothing, so nothing in the grant service reacts to a drive coming or going [S]. (Other system components could still revoke; I did not audit them [?].)

### Why the same drive gets the same URI

- `ExternalStorageProvider` builds the root id for a USB/public volume from the filesystem UUID: `rootId = volume.getFsUuid()` ([A6], `updateVolumesLocked`, ~L222). Document ids are `"<rootId>:<path>"` (`getRootFromDocId` splits on the first `:`, ~L545). A tree URI for `Photoxpo/` on the drive therefore looks like `content://com.android.externalstorage.documents/tree/<UUID>%3APhotoxpo` [S].
- vold also uses the filesystem UUID as the stable mount name: `/storage/<UUID>` ([A9], `PublicVolume::doMount`, "Use UUID as stable name, if available") [S].
- Consequence: unplug and replug the same filesystem and the identical URI resolves again, so the existing persisted grant applies. Reformat the drive, or swap to another drive, and the UUID changes: the grant is useless and the user must pick again [I].
- If the user renames or moves the granted folder with another app, the document id changes and the grant is dead ([D1] caution above) [S].

### Can the user pick the root of the drive?

- The official guide says that on Android 11+ `ACTION_OPEN_DOCUMENT_TREE` cannot be used to request "the root directory of each SD card volume that the device manufacturer considers to be reliable" ([D1]). That text says SD card and does not mention USB [S].
- The code that enforces the rule exempts USB: `shouldBlockDirectoryFromTree` returns `false` first for anything on `FLAG_REMOVABLE_USB`, with the comment "Allow all directories on USB, including the root." ([A6], ~L393; same text in `android14-release`) [S].
- `StorageVolume.createOpenDocumentTreeIntent()` builds a picker intent that starts at the volume root and sets `EXTRA_SHOW_ADVANCED` ([A8]) [S]. Use it to open the picker already on the Archive Drive.
- **Decision for the spec, not for this ticket:** a grant on the drive root also covers the existing messy photos (useful for the "index the existing drive" item in the map's *Not yet specified*) and cannot be broken by renaming a subfolder. A grant on `Photoxpo/` alone is narrower. Both are technically available.

### Limit on persisted grants

- `MAX_PERSISTED_URI_GRANTS = 512` in both `android14-release` ([A5b], L112) and `android15-release` ([A5], L122). `maybePrunePersistedUriGrantsLocked` drops the oldest persisted grants (by `persistedCreateTime`) past that [S]. The official guide gives no number ([D1]).
- Practical rule: take one tree grant, do not take per-file grants. Never need to think about the limit.

## 2. Detecting that the Archive Drive is attached and usable

### What the platform exposes

- `StorageManager.registerStorageVolumeCallback(executor, callback)` delivers `onStateChanged(StorageVolume)` when a volume's state changes, e.g. to `MEDIA_MOUNTED` or `MEDIA_UNMOUNTED`. It is public API ([A7], L535-L595) [S]. It only works while the app process is alive, which fits the map's "user opens the app after plugging in".
- `StorageVolume.getUuid()` returns the filesystem UUID; `getState()` returns one of `MEDIA_MOUNTED`, `MEDIA_MOUNTED_READ_ONLY`, `MEDIA_CHECKING`, `MEDIA_UNMOUNTABLE`, `MEDIA_BAD_REMOVAL`, ... ([A8]) [S]. Match the tree URI's UUID prefix to `getUuid()` to find "our" volume.
- Legacy broadcasts `ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_BAD_REMOVAL`, `ACTION_MEDIA_EJECT` still exist in `Intent` ([A10], ~L3554-L3614); `ACTION_MEDIA_EJECT` is documented as "Applications should close all files they have open within the mount point". `StorageVolumeCallback` is the cleaner route [S].
- `UsbManager` attach events fire when the device is plugged, not when the filesystem is mounted. I did not read them; do not use them as "ready" [?].

### Mounting takes time and can fail

- Before mount vold runs a filesystem check: `fsck_msdos` for FAT ([A11], `Vfat::Check`) and `fsck.exfat` for exFAT ([A12], `Exfat::Check`). If the check fails, the mount fails with `-EIO` ([A9], `doMount`) [S]. A drive that was yanked mid-write may therefore take longer to appear, may be repaired, or may not mount. The `MEDIA_CHECKING` state exists for this window.
- exFAT mount falls back to read-only if the read-write mount fails (`Exfat::Mount`, "attempting read-only"), and vfat retries read-only on `EROFS` ([A11], [A12]) [S].
- `ExternalStorageProvider` lists any volume that is `isMountedReadable()`, so a read-only mount still shows up as a root. It only sets `Root.FLAG_SUPPORTS_CREATE` when the volume is mounted writable ([A6], L188 and the `isMountedWritable` branch) [S]. **A read-only Archive Drive looks present but every write fails.** The app must check `StorageVolume.getState() == MEDIA_MOUNTED` (not `MEDIA_MOUNTED_READ_ONLY`) before starting a backup.

### Probing through SAF

- When the volume is not mounted, `getRootFromDocId` throws `FileNotFoundException("No root for <UUID>")` ([A6], ~L550) and `DocumentsProvider.query` catches `FileNotFoundException`, logs "Failed during query", and returns `null` ([A13], L940-L942). `ContentResolver.query` therefore returns a `null` cursor for a tree on an absent drive [S].
- Write operations through `DocumentsContract.createDocument/renameDocument/deleteDocument` go through `ContentResolver.call`; the provider throws and `DocumentsContract` logs and rethrows `FileNotFoundException` for apps targeting API 26+ (`rethrowIfNecessary`, [A4], ~L1766), or returns `null`/`false` otherwise [S]. Treat both a `null` result and a `FileNotFoundException` as "drive gone".
- **Identity check:** the UUID only proves "same filesystem". The map already settles that the drive is identified by an id file in `.photoxpo/`. Read it on every attach; this also catches a reformatted drive that happens to be plugged in [I].

## 3. Listing cost for tens of thousands of files

### What `DocumentFile` does

From androidx `TreeDocumentFile` ([A14]) and `DocumentsContractApi19` ([A15]):

1. `listFiles()` runs **one** children query that asks only for `COLUMN_DOCUMENT_ID`, and wraps each row in a new `TreeDocumentFile` (`TreeDocumentFile.listFiles`, L130-L160) [S].
2. Every later `getName()`, `length()`, `lastModified()`, `isDirectory()`, `canWrite()`, `exists()` on that object is **its own** `ContentResolver.query` for a single column, via `queryForString`/`queryForLong` (`DocumentsContractApi19`, L150-L195) [S].
3. So reading name + size + modified time for N files is roughly 1 + 3N provider round trips, each crossing Binder to the system `MediaDocumentsProvider`/`ExternalStorageProvider` process [S for the structure; the exact IPC hop count is [I]].
4. The class doc itself says: "it has substantial overhead. For optimal performance and a richer feature set, use the DocumentsContract methods directly." ([A16], `DocumentFile` class comment, L33-L37) [S]. The official guide adds a generic caution that iterating a large number of files from an `ACTION_OPEN_DOCUMENT_TREE` tree "might" reduce performance ([D1]).

### What the provider does on the other side

- `FileSystemProvider.queryChildDocuments` lists the directory with `File.listFiles`, then calls `includeFile` for every child into one in-memory `MatrixCursor` ([A17], L403-L425). `includeFile` stats each file (`canWrite`, `lastModified`, `length`, mime lookup, doc-id build) whatever columns you asked for ([A17], `includeFile`, L595-L660) [S].
- So one raw children query costs one directory scan plus one `stat` per child, and the same per-child work is repeated for every single-column query `DocumentFile` makes afterwards [S]. Per-directory time on the disk itself was not measured [?].
- Because `ExternalStorageProvider.buildFile` resolves to `root.visiblePath` (`/storage/<UUID>/...`, L561), directory scans go through the FUSE layer, not straight to the kernel filesystem ([A6]) [S for the path; I for the performance consequence].

### No numbers in primary sources

- I found no Google or AOSP document that gives a timing for listing. Third-party write-ups exist (CommonsWare blog posts on `DocumentFile` costs; one claims 48 s vs 3.5 s for a large directory); these are secondary, I did not verify them, and I am not relying on them. [?] Measure on the S24 Ultra.

### Design consequence

The map's drive layout (`Photoxpo/Originals/YYYY/YYYY-MM/...`) already keeps each directory to hundreds or low thousands of entries. Plan to **never list the whole drive**. Room is the source of truth for "what is Backed up"; the drive is listed one month-directory at a time only to reconcile.

## 4. Best API for bulk copy and read-back hashing

Candidates compared:

| API | Verdict |
|---|---|
| `DocumentFile` | Convenient, but N+1 queries (section 3) and `canWrite()` is misleading: it returns true if the flags contain `FLAG_SUPPORTS_DELETE` *or* `FLAG_SUPPORTS_WRITE`; query `FLAG_SUPPORTS_WRITE` directly to know if you can edit ([D1]). Avoid for anything in a loop. |
| Raw `DocumentsContract` + `ContentResolver` | Right choice for structure: one children query with a full projection, `createDocument`, `renameDocument`, `moveDocument`, `deleteDocument` ([A4]). |
| `ContentResolver.openFileDescriptor(uri, mode)` → `ParcelFileDescriptor` | Right choice for bytes. The provider opens the real file and hands the app the file descriptor itself: `FileSystemProvider.openDocument` returns `ParcelFileDescriptor.open(file, mode, ...)` ([A17], L505-L530). After that the app reads/writes the fd directly with ordinary `FileInputStream`/`FileOutputStream`; no per-chunk Binder traffic [S]. |
| `ContentResolver.openInputStream/openOutputStream` | Thin wrappers over the same fd; fine, but `openOutputStream` hides the mode (see below). Prefer the explicit fd. |
| `StorageManager.openProxyFileDescriptor` | For *serving* bytes from your own code; not relevant here. |

### Mode string traps (all [S])

- `ParcelFileDescriptor.parseMode` → `FileUtils.translateModeStringToPosix`: `"w"` = `O_WRONLY|O_CREAT` and **does not truncate**; `"wt"` adds `O_TRUNC`; `"rw"` = `O_RDWR|O_CREAT`; `"wa"` adds `O_APPEND` ([A18], L1539-L1570). `ContentResolver` documents the same: "`w` is write only and `wt` is write and truncate" and that providers may differ ([A3], L1565-L1567, L1818).
- Therefore: re-writing a file that already exists with `"w"` leaves stale bytes after the new end if the new content is shorter. Use `"wt"` when overwriting.

### Name handling traps (all [S])

- `createDocument` runs the name through `buildValidFatFilename` (invalid characters become `_`, name trimmed to 255 UTF-8 bytes) ([A17], L250; [A18], L1137-L1160).
- `createDocument` uses `buildUniqueFile`: if the target exists it creates `name (1).ext`, `name (2).ext`, ... up to 32 tries, then fails ([A18], L1212-L1226; [A17], L257). It does **not** return "already exists". The app must look up an existing child by name *before* creating, otherwise a retry after a crash creates a duplicate with a different name.
- If the MIME type passed does not match the file extension, `splitFileName` appends the MIME type's default extension ([A18], L1285+). Pass the right MIME type or `application/octet-stream` (with which the name is kept as is, but the collision counter then lands after the extension: `IMG.jpg (1)`).

### Side effects on MediaStore (all [S] unless marked)

- `createDocument`, `renameDocument`, `moveDocument` and `deleteDocument` call `updateMediaStore` → `MediaStore.scanFile` on the file ([A17], L248-L368). `openDocument` for **read** also calls `MediaStore.scanFile` first and opens through MediaStore (`openFileForRead`, L513-L545). So Originals on the Archive Drive are indexed by MediaStore under the volume name returned by `StorageVolume.getMediaStoreVolumeName()` (the lowercase UUID) ([A8]).
- Expected effects: (a) extra per-file overhead on every read-open, size unmeasured [?]; (b) the Originals may appear in other gallery apps while the Archive Drive is attached [I]. Whether a `.nomedia` file in `Photoxpo/` suppresses (b) depends on MediaProvider scanning code I did not read [?].

### Read-back hashing and what "Backed up" really proves

- Read-back = `openFileDescriptor(uri, "r")` on the *drive* file, stream it through the hash, compare with the source hash. This is the right call and is the map's definition of **Backed up**.
- **Caveat [I]:** a read right after a write can be served from the Linux page cache (or the FUSE layer's cache), so the read-back may not touch the disk and would not catch a bad write. Public API cannot request direct I/O. Mitigations that *are* available: call `FileDescriptor.sync()` (documented `java.io` API) before closing the write fd; close and reopen the file for reading; and prefer verifying in a second pass after a batch of files, when earlier files have likely been evicted from cache by later writes. This cannot be proven from source; an on-device test (write a file, unplug/replug, hash it) is the only real check. [?]
- vold mounts the volume with `MS_DIRSYNC | MS_NOATIME` (exFAT: `| MS_NOEXEC`) ([A11] L140, [A12] L63) [S]. `MS_DIRSYNC` makes directory changes (create, rename) synchronous; it does **not** make file data writes synchronous. File data is flushed by the kernel's normal writeback or by `fsync`.
- Read-ahead for the FUSE mount of a public volume is configured to 256 KB and max dirty ratio 40% (`ConfigureReadAheadForFuse(..., 256u)`, `ConfigureMaxDirtyRatioForFuse(..., 40u)`, [A9], L255-L259) [S]. These are system tuning, not app knobs.

## 5. Rename and move

All [S] from `FileSystemProvider` ([A17]):

- `renameDocument`: validates the name for FAT, builds a unique target with `buildUniqueFile(before.getParentFile(), displayName)` and calls `File.renameTo` (L281-L309). A name collision produces `name (1).ext`, not an error. Because document ids embed the path, the new document id differs; the old URI is dead. `DocumentsContract.renameDocument` documents that "the original document is no longer valid" and returns the new URI ([A4], L1425-L1445); `TreeDocumentFile.renameTo` updates its own URI for you; with raw calls you must keep the returned URI.
- `moveDocument`: `new File(targetParent, before.getName())`, fails with `IllegalStateException("Already exists ...")` if present, else `renameTo` (L311-L348). Source and target must be on the same provider; across volumes `renameTo` would fail, and there is no copy fallback in this provider. Moving a file inside one volume is a directory-entry change, not a data copy [I].
- Flags: a document reports `FLAG_SUPPORTS_RENAME`, `FLAG_SUPPORTS_MOVE`, `FLAG_SUPPORTS_DELETE` only if `file.canWrite()` (`includeFile`, L595-L630). A read-only mount therefore reports none of them [S].
- Atomicity: the code is a single `rename(2)`. Whether that is crash-atomic on exFAT/FAT is a kernel-driver property that I did not verify against a primary source [?]. Do not rely on it for correctness; rely on the hash.

## 6. Mid-write disconnect

What the source says happens when the Archive Drive is unplugged (or ejected) while the app is writing:

1. vold destroys the volume. `VolumeBase::destroy` unmounts a mounted volume and sets `kBadRemoval` ([A19], L195-L215) [S]. The legacy `ACTION_MEDIA_BAD_REMOVAL` broadcast and `MEDIA_BAD_REMOVAL` state correspond to this ([A10], [A8]).
2. `PublicVolume::doUnmount` first calls `KillProcessesUsingPath(getPath())` ([A9], L304; also L360). That function sends **SIGINT**, then **SIGTERM**, then **SIGKILL**, to every process that has a file open, a mapping, or its cwd/root/exe under the path, checking `/proc/<pid>/{maps,cwd,root,exe,fd/*}` ([A20], `KillProcessesWithOpenFiles`; [A21], `KillProcessesUsingPath`, with `sSleepOnUnmount` waits of 5 s between steps). The FUSE daemon is skipped until the last round ([A20], L169) [S].
3. Our write fd points at `/storage/<UUID>/...` (the provider opens via `visiblePath`, [A6] L561), so it falls under that prefix [I]. **Expect the Photoxpo process to be signalled and probably killed**, including a foreground service. Whether Android's runtime handles SIGINT/SIGTERM gracefully enough to let us close files first, and how long that window is, I could not confirm [?].
4. If a write call does return before the process dies, it will fail with an I/O error (`IOException`/`ErrnoException`); the exact errno (EIO, ENODEV, ENOTCONN, ...) was not verified [?]. vold's own comment notes that killing the FUSE daemon first would produce `ENOTCONN` ([A9], `doUnmount` comment), which is why it kills clients first [S].
5. What is left on the drive: a partially written file, size and contents unknown. The file may even be in the directory without its last clusters, or the filesystem may be "dirty". On the next attach vold runs `fsck` before mounting ([A11], [A12], [A9] `doMount`), which may repair, or may refuse to mount. [S for fsck; I for the consequences]

**Design consequence:** treat process death at any instruction as the normal disconnect path. All state in Room, per file, written before and after each step, resumable from a cold start. A partial file is never trusted: it is deleted or overwritten with `"wt"`, then re-copied and re-verified. This is consistent with the map's "nothing is Backed up until verified".

## 7. File-system limits on the drive itself

- FAT32 cannot hold a file of 4 GiB or more. AOSP encodes this: `VolumeInfo.buildStorageVolume` sets `maxFileSize = 4294967295L` for `vfat` ([A22], L450-L451) [S]. The map says Nandhu's drive is "exFAT/FAT32" and that My Files can write to it, so the filesystem type is not yet known. Long or high-resolution phone videos can exceed 4 GiB [I].
- `StorageVolume.getMaxFileSize()` carries the limit but is `@hide` ([A8], L303) and `StorageVolume` has no public filesystem-type getter, so the app cannot cheaply ask. The robust approach is to attempt the copy and treat a write failure on a file ≥ 4 GiB as "this Archive Drive cannot hold this Original". [?] on the exact exception.
- Open question for the human: confirm whether the Seagate drive is FAT32 or exFAT (phone Settings → Storage, or `Files by Google`, or the drive's properties on a PC). This changes whether large videos are a real problem.
- Timestamps: vfat is mounted with `time_offset` fixed at mount time, so file times on FAT can be off by the DST offset ([A11], L156-L183 comment, b/315058275) [S]. Do not use the drive's modified time as a source of truth; the manifest should store the Original's own times.

## 8. Samsung / OEM quirks

- I found **no primary source** (no Samsung developer documentation, no AOSP bug on a Samsung USB case) that I could cite for One UI SAF behaviour with USB disks. Searches returned forum posts and blogs only, which I did not use as evidence. [?]
- Known-unknown list for One UI 6/7 on the S24 Ultra, to be answered by a device spike rather than by documents:
  1. Does Samsung's picker offer the USB drive root and let the user tap "Use this folder"? (AOSP allows it; the picker UI is OEM-modified.)
  2. Does the persisted grant survive replug and reboot on One UI?
  3. Does One UI mount a USB disk automatically, or does the user have to approve a prompt each time? (A forum report of changed OTG prompts in One UI 7 beta turned up in search; unverified.)
  4. Power: a bus-powered portable disk may draw more current than the phone port supplies. Not a software question; no source consulted.
- Because the picker and storage stack are OEM-modified and closed, every One UI statement here is marked [?] rather than guessed.

## 9. Throughput

- No primary source gives a throughput number for SAF or for FUSE-mediated writes to a USB disk.
- What the sources do say about the path: the app writes to a file descriptor opened by `ExternalStorageProvider` on `/storage/<UUID>/...`, which is a FUSE mount served by MediaProvider ([A6] L561; [A9] `MountUserFuse`). Android 12+ can bypass the FUSE daemon for reads and writes with FUSE passthrough, if the kernel supports it ([D2]). The page does not say whether it applies to public/removable volumes, and whether the S24 Ultra's kernel enables it for a USB disk is unknown. [?]
- The binding limit is probably the disk and the USB link rather than SAF, since bytes flow on a plain fd with no Binder hop per chunk. This is an inference [I].
- Back-of-envelope only (assumed rates, **not** measurements or sources): 600 GB at 100 MB/s is about 1.7 hours of pure writing, at 40 MB/s about 4.2 hours; the read-back verification roughly doubles the I/O, so plan for several hours for the first full backup and treat it as a long-running foreground job with progress and ETA. The spike below replaces these guesses.

## 10. What to test on the phone (spike checklist)

Everything marked [?] above, in the order that most affects the spec:

1. Pick the drive root with `ACTION_OPEN_DOCUMENT_TREE`, persist, unplug, replug, check `contentResolver.persistedUriPermissions` still has it and a children query returns rows. Then reboot the phone, and repeat.
2. Raw children query (columns: document id, display name, size, last modified, mime, flags) of a directory with 5k and 20k files, versus `DocumentFile.listFiles()` + `getName()`/`length()`. Record times.
3. Copy a 1 GB and a 4+ GB file with 1 MiB and 4 MiB buffers (`"wt"`, `FileDescriptor.sync()`, close), then read-back hash. Record MB/s for write and for read. Also a run of 2,000 small files, to see per-file overhead (MediaStore scan, createDocument).
4. Unplug the cable mid-copy. Record: did the Photoxpo process die, which signal, what exception (if any) reached the app, what the drive looks like after replug (does it mount, how long, what is left of the partial file).
5. Read-back honesty: write a file, `sync()`, close, immediately hash it; then unplug/replug and hash again. Confirm the results match and note whether the second read is far slower (shows the first was cached).
6. Does the Photoxpo folder's contents appear in Samsung Gallery / Google Photos while attached? Does `.nomedia` change that?
7. Filesystem type of the Seagate drive; try writing a > 4 GiB file if it is FAT32.
8. Rename and move: `renameDocument` collision behaviour and the returned URI; `.part` → final rename timing.

## Recommendation: MVP I/O approach

**Use SAF with a persisted tree grant, driven through raw `DocumentsContract` and `ContentResolver` file descriptors. Do not use `DocumentFile` in loops.** Concretely:

1. **Grant.** Launch `StorageVolume.createOpenDocumentTreeIntent()` for the USB volume. Persist read+write (`takePersistableUriPermission`). Store the tree URI and the volume UUID in Room. Decision for the spec author: grant the drive root (robust, also covers the later "index the existing drive" work) or only `Photoxpo/` (narrower).
2. **Identity and readiness.** On app start and on `StorageVolumeCallback.onStateChanged`: the volume with the stored UUID must be `MEDIA_MOUNTED` (reject `MEDIA_MOUNTED_READ_ONLY` and `MEDIA_CHECKING`), a probe children query must return a non-null cursor, and `.photoxpo/drive-id` must match the expected drive id. Any failure means "Archive Drive not ready"; pause jobs.
3. **One small module owns all drive I/O** (a deep module with a narrow interface: ensure directory, list one directory, write an Original, read an Original, rename, delete). It hides document-id handling, the cursor projections, the FAT name rules and the name-collision behaviour, and it is the seam where a fake drive can be substituted in tests. Callers never see a URI-per-file `DocumentFile`.
4. **Listing.** Children query with an explicit projection (`COLUMN_DOCUMENT_ID`, `COLUMN_DISPLAY_NAME`, `COLUMN_SIZE`, `COLUMN_LAST_MODIFIED`, `COLUMN_MIME_TYPE`, `COLUMN_FLAGS`), per directory, only when reconciling. Cache directory document ids in memory so `Originals/2026/2026-10` is resolved once per run.
5. **Copy.** Open the source for read and the destination with `openFileDescriptor(uri, "wt")`; stream with a large buffer (start at 1 MiB; tune in the spike); hash the source bytes as you copy; `FileDescriptor.sync()`; close. Create the destination under a temporary name (for example `<name>.photoxpo-part`) after first checking the name does not already exist, because `createDocument` silently renames on collision.
6. **Verify.** Reopen the destination with `"r"`, stream-hash it, compare with the source hash. Only on a match: rename `.photoxpo-part` to the final name (keep the URI `renameDocument` returns), then mark the file **Backed up** in Room. Where practical, run the read-back pass after a batch rather than file by file, to reduce the chance of verifying from cache. A temporary name also stops half-written files from looking like Originals when someone browses the drive in My Files.
7. **Disconnect.** Assume the process can be killed at any moment on unplug (section 6). Every step writes its state to Room before and after; startup reconciles: any `.part` file or any file in state "copying" is deleted or overwritten with `"wt"` and re-copied from byte 0. No partial-file resume in the MVP.
8. **Big files.** If the drive is FAT32, an Original of 4 GiB or more cannot be stored; surface that file as "does not fit this Archive Drive" rather than retrying forever.
9. **Not in the MVP:** `MANAGE_EXTERNAL_STORAGE` / raw path access to `/storage/<UUID>`, `DocumentFile`-based enumeration, per-file grants, and any dependence on rename being crash-atomic.

Residual risk: Samsung picker/grant behaviour (section 8) and real throughput (section 9) are unconfirmed. Run the spike checklist before locking the spec; if item 1 fails on One UI, the fallback is to ask the user to re-pick the folder on each attach, which is a one-tap flow because the picker can be launched at the drive root.

Side note for the source-reading ticket (outside this question): `TranscodeHelperImpl.supportsTranscode` only applies to `.mp4` files under `/storage/emulated/` in configured DCIM sub-paths ([A23], L849-L866), so the Archive Drive is not affected, but reads of phone videos from `DCIM/Camera` may be subject to Android's media-transcoding rules for apps that do not declare HEVC support; whether that can change the bytes we hash from the source was not investigated. Worth a check when the source-folder ticket is researched. [?]

## Sources

Android developer documentation:

- [D1] Access documents and other files from shared storage: https://developer.android.com/training/data-storage/shared/documents-files
- [D2] FUSE passthrough: https://source.android.com/docs/core/storage/fuse-passthrough

AOSP source (read from `android15-release` unless noted; `android14-release` spot-checked for [A5b] and the USB root exemption in `ExternalStorageProvider`):

- [A3] `ContentResolver.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/ContentResolver.java
- [A4] `DocumentsContract.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/provider/DocumentsContract.java
- [A5] `UriGrantsManagerService.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/uri/UriGrantsManagerService.java
- [A5b] same file on `android14-release`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/uri/UriGrantsManagerService.java
- [A6] `ExternalStorageProvider.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java
- [A7] `StorageManager.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/os/storage/StorageManager.java
- [A8] `StorageVolume.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/os/storage/StorageVolume.java
- [A9] vold `PublicVolume.cpp`: https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/model/PublicVolume.cpp
- [A10] `Intent.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/Intent.java
- [A11] vold `fs/Vfat.cpp`: https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/fs/Vfat.cpp
- [A12] vold `fs/Exfat.cpp`: https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/fs/Exfat.cpp
- [A13] `DocumentsProvider.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/provider/DocumentsProvider.java
- [A14] androidx `TreeDocumentFile.java`: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/documentfile/documentfile/src/main/java/androidx/documentfile/provider/TreeDocumentFile.java
- [A15] androidx `DocumentsContractApi19.java`: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/documentfile/documentfile/src/main/java/androidx/documentfile/provider/DocumentsContractApi19.java
- [A16] androidx `DocumentFile.java`: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/documentfile/documentfile/src/main/java/androidx/documentfile/provider/DocumentFile.java
- [A17] `FileSystemProvider.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/com/android/internal/content/FileSystemProvider.java
- [A18] `FileUtils.java` (`buildUniqueFile`, `buildValidFatFilename`, mode translation): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/os/FileUtils.java
- [A19] vold `model/VolumeBase.cpp`: https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/model/VolumeBase.cpp
- [A20] vold `Process.cpp` (`KillProcessesWithOpenFiles`): https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/Process.cpp
- [A21] vold `Utils.cpp` (`KillProcessesUsingPath`): https://android.googlesource.com/platform/system/vold/+/refs/heads/android15-release/Utils.cpp
- [A22] `VolumeInfo.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/os/storage/VolumeInfo.java
- [A23] MediaProvider `TranscodeHelperImpl.java`: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/android15-release/src/com/android/providers/media/TranscodeHelperImpl.java

(`[A1]`, `[A2]` intentionally unused; numbering kept stable with the working notes.)

## Not covered

- Real measurements of any kind (listing time, throughput, disconnect behaviour). No device was available.
- One UI behaviour.
- `MANAGE_EXTERNAL_STORAGE` or direct `/storage/<UUID>` path access, which the map has already ruled out in favour of SAF.
- The USB transport layer (UAS vs BOT, power negotiation).
