# UI/UX references for backup state and offloaded photos

Research for ticket #7. Source: the Mobbin connector (`search_screens`, `search_flows`), queried 2026-10-11. Every reference below links to its Mobbin page, using the `mobbin_url` the connector returned. Vocabulary follows `GLOSSARY.md` (Archive Drive, Original, Preview, Backed up, Load).

## Method and limits

- The Mobbin connector was available and authenticated. All findings come from it.
- Mobbin indexes **iOS and web only**. There is no Android filter, so every reference is an iOS screen. Structure and wording transfer; exact Material styling (bottom sheets, snackbars) may differ on Android.
- **Not found on Mobbin:** Samsung Gallery, Immich, Synology Photos, and iCloud's "Optimize Storage" setting. A search naming Immich returned Google Photos, Dropbox and Apple Photos instead. Those apps are not covered here.
- **No Mobbin screen shows a "USB drive not connected" state.** The closest are "device not found" screens from hardware companion apps (section 6).
- **No Mobbin app fetches a full-quality file from a local drive.** The closest are download-in-progress overlays (section 4).
- Observations come from low-resolution previews. Wording is paraphrased unless a label is short and distinctive.
- The competitors are cloud services; Photoxpo has no cloud. Copy and icons below must be translated, not copied (see "Words to avoid").

## 1. Backup progress and status

- **Summary banner above the grid.** Google Photos shows a rounded card at the top of the library reading "Backup complete" with a count such as "28 photos backed up" ([Google Photos, library with backup banner](https://mobbin.com/screens/0ede086e-7804-4325-a2a3-eeaf6d86091a)). One line, calm, collapsible with a chevron.
- **Dedicated status screen.** Google Photos' "Backup is on" screen has a large check illustration, the sentence "Your backup is complete" and a count ([Google Photos, Backup is on](https://mobbin.com/screens/9e3dfb56-6846-41f2-a635-428bf3304a61)). A good model for Photoxpo's home screen: one big state, one count.
- **Inline progress line under a header.** Apple Photos shows a slim progress bar with "Syncing 2 Items to iCloud" and a **Pause** link ([Apple Photos, profile sheet](https://mobbin.com/screens/428aa147-6046-449d-98bb-02d0eebbb210)), and a compact "Syncing 1 Item..." line under the Library title ([Apple Photos, Library](https://mobbin.com/screens/9050e289-4e31-4e83-9950-f1d4fcb21aa7)). Dropbox shows a single row, "All done backing up", above the photo grid ([Dropbox, Photos tab](https://mobbin.com/screens/63cdba55-ee79-4dfa-bb56-9f57e7f75e46)).
- **Per-file list with states.** GoPro Quik lists each file as Complete, time remaining, or Waiting, with a per-row cancel and a notice to keep the app open ([GoPro Quik, Saving Media 2/4](https://mobbin.com/screens/d9d1710a-5585-42da-ae77-c4811a8ed7b0)). Dropbox's uploader lists "6 of 6 items" with a countdown and an "Uploaded" caption per file ([Dropbox, Uploading](https://mobbin.com/screens/0344b16a-a8e5-442b-a8f9-efddae1ab159)). Useful behind a "details" link; too heavy for the main screen.
- **Reassurance about leaving the screen.** Roku says uploads continue if the person leaves ([Roku, Adding photos](https://mobbin.com/screens/a0850ac5-0fa4-45cf-b7e6-e93cf86b8cbf)); GoPro Quik says to stay. Photoxpo uses a foreground service, so it can say leaving is fine.
- **Backup as a setting.** Google Photos' Settings list shows "Backup" with an Off/On value ([Google Photos, Settings](https://mobbin.com/screens/4d5c20bb-35d2-4805-8cc4-8f383cdec246)). Dropbox shows a "Camera uploads is turned off" sheet with an Open settings button ([Dropbox, camera uploads off](https://mobbin.com/screens/24d58660-8ee4-4e2e-a941-4d4a86ae4efd)).
- **Backup quality choice** (Storage saver vs Original quality) in a bottom sheet ([Google Photos, quality sheet](https://mobbin.com/screens/e89ec812-6fd3-4109-a0c7-d511b75a3251)). Photoxpo always keeps the Original, so there is no equivalent choice; do not add one.

Takeaway: one sentence of state plus one count (for example "12 of 340 photos Backed up"), a Pause control, and the per-file list only on request.

## 2. "Free up space" prompts and confirmation wording

- **Entry from a storage meter.** Settings > Apps & devices shows "This device 39% used" with **Free up space** and **Cache** chips ([Google Photos, Apps & devices](https://mobbin.com/screens/38594ba3-03e8-45f2-b7c4-9b661171cfd9)).
- **A scanning step first.** A modal reads "Finding items that are already backed up" with a progress bar and Cancel ([Google Photos, scanning](https://mobbin.com/screens/5ef03e66-42d5-412c-990c-357ca4745bd1)). This maps to Photoxpo's per-batch verification: show that the app is checking before it offers to delete.
- **A reassuring explainer before any delete.** "Free up space on this device": an illustration, two short reassurance lines (items are already safely backed up; you can still view them) and one primary button that names the count and the action, "Delete 80 items" ([Google Photos, Free up space](https://mobbin.com/screens/c61edbc7-37c4-4ed3-a95b-a4d505afc623)). Not a bare "OK".
- **Progress while freeing.** A checklist illustration, a progress bar and a note to keep the app open ([Google Photos, Freeing up space](https://mobbin.com/screens/09dc45ac-6db8-4084-8e36-7ee8a7ef829a)). Telegram ends a similar cleanup with a toast reporting the amount freed ([Telegram](https://mobbin.com/screens/865ba3fc-47ab-4da4-a80e-a2059254c3ac)).
- **Result screen with the amount reclaimed.** "You cleaned up 6 MB!" with a Done button ([Google Photos, cleanup done](https://mobbin.com/screens/4613e863-fef1-4a17-8c3b-15f8a3b18025)). Photoxpo should report gigabytes freed; drop the upsell block under it.
- **Review-and-select variant.** A list headed "Delete some items to make room" with a size-selected meter ([Google Photos, review large items](https://mobbin.com/screens/81e1c9b4-ef1d-4dd6-bf9c-568f63698a61)). Too fiddly for Nandhu; one confirmed batch is simpler.

Destructive-confirmation wording, for comparison:

- **A tick-box brake.** The "Move 1 Google Photos item to trash?" dialog keeps the button disabled until the person ticks an "I understand..." box ([unticked](https://mobbin.com/screens/04970ba9-79ab-4fd3-aa69-e84c9b00d1c5), [ticked](https://mobbin.com/screens/6eb4b478-92db-4b1f-84d7-0a7747c68370)). A strong brake; possibly too heavy for routine batches, but worth testing for the first-ever delete.
- **State the consequence and its reversibility.** Apple's system prompt says the photo is deleted everywhere and stays in Recently Deleted for 30 days ([Halide, system delete prompt](https://mobbin.com/screens/706ac918-3ce3-4c66-aa7d-87f2df5bc951)); the same text appears as a red-button sheet in [Apple Photos](https://mobbin.com/screens/c74527da-0e05-4ef2-af4b-c11a631b9217).
- **Plain two-button dialogs that name what is lost and whether it can be undone:** [Once, Delete Film](https://mobbin.com/screens/1be16210-a004-4e9e-8eb2-09ace455f214); [SoundCloud, Remove all downloads?](https://mobbin.com/screens/04ffeafa-4fd7-4553-b2ee-4e2695f67667), which says what the person will no longer be able to do offline; [GoPro Quik, "Are you sure?" sheet](https://mobbin.com/screens/5c8e430f-ca56-433e-8001-7f049fe66e09).
- **Weak example to avoid.** SHEIN asks a vague question with No/Yes buttons, which says nothing about what happens ([SHEIN](https://mobbin.com/screens/37aadeb1-45a7-4568-a5fd-2317c79f6f4c)).

For Photoxpo, Android shows its own delete confirmation (per the map). The in-app screen before it should do the reassuring, and the wording should say the Original stays safe on the Archive Drive and the Preview stays on the phone.

## 3. How offloaded photos are marked in the grid

- **Small corner badge, bottom-right of each tile.** Google Photos puts a small outlined cloud glyph on tiles, with a soft gradient behind it so it stays legible on any photo ([Google Photos, grid](https://mobbin.com/screens/aa710f37-8257-4681-a5ad-b546da46254b); [Favorites](https://mobbin.com/screens/991eba99-cb55-4188-b8d1-b2d6aa94d110)). In Google Photos the badge means "backed up", not "offloaded".
- **Per-row glyph in lists.** OpenPhone's Recents grid shows a small cloud beside the date and size, and "Waiting..." on a pending file ([OpenPhone](https://mobbin.com/screens/e8f062ee-eafa-4655-ac0a-61fca9ffe7e2)). Speechify's list pairs a cloud with a percentage ([Speechify](https://mobbin.com/screens/fc68b660-9afc-44d0-ba6b-2f31894a6e05)).
- **Download arrow as the "not here yet" marker.** Patreon and Glow put a download arrow at the right of each row for items not yet on the device ([Patreon](https://mobbin.com/screens/f6f36696-df97-416c-8ba2-d44e7925070b), [Glow](https://mobbin.com/screens/ac07e920-0dc6-4d6b-94d5-dd99fea7ecc2)).
- **Per-tile spinner while working.** Partiful shows a spinner and "Uploading" on the tile itself ([Partiful](https://mobbin.com/screens/c5a47d5b-1988-43d7-8acc-416952b02eb7)).

Observation: no reference greys or blurs the tile. The picture stays fully visible and a small badge carries the state. Since a Preview is always viewable, Photoxpo can do the same: the Preview looks normal and a small badge means "the Original is on the Archive Drive". Whether to badge every offloaded photo or only the exceptions (Originals still on the phone) is a question for the prototype.

## 4. Requesting an unavailable Original ("Load")

- **Blocking progress card over the item.** mymind shows a white rounded card with a spinner and "Downloading Original" over the image ([mymind](https://mobbin.com/screens/7a0e2d06-53b6-4a2c-9b45-353c6337c0f1)). The word "Original" matches the Photoxpo glossary.
- **Overlay with cancel on the grid.** Riveo shows a dark "Downloading" box with "Tap to cancel" over the grid ([Riveo](https://mobbin.com/screens/528dd68a-905d-44a7-8927-4a63075c7941)).
- **Sheet with percentage and Cancel.** "Preparing Captures...", a progress bar, "67% complete", Cancel ([PlayStation App](https://mobbin.com/screens/cbf836ea-dbc8-446d-a1c6-494521f88613)). Suits a Load of several Originals.
- **Button that becomes a status.** Linktree's download button turns into a disabled "Downloading..." ([Linktree](https://mobbin.com/screens/1e66ab17-7d4a-41ff-a52e-f6f8003aac39)).
- **Quiet centred toast** instead of a full modal ([Whatnot](https://mobbin.com/screens/e9a078d5-7572-4b6a-bc7f-520303150090)).
- **"Not yet available" dialog with a single OK**, giving the reason and what to do meanwhile ([Stake](https://mobbin.com/screens/5b359542-cbf4-40c9-9b44-6feb698d0671)). A useful shape for "the Archive Drive isn't connected, so this Original can't be Loaded right now".
- **File size beside the download arrow** ([Patreon](https://mobbin.com/screens/f6f36696-df97-416c-8ba2-d44e7925070b)). Showing size before a Load helps, especially for videos.

Takeaway: in the full-screen viewer, show the Preview immediately with one labelled "Load Original" button and its size. On tap, show progress with Cancel. If the Archive Drive is not attached, the same button leads to the "plug in your drive" state instead of failing.

## 5. Empty states

- Illustration, one short line, one big primary button: [Partiful, "Drop the pics"](https://mobbin.com/screens/37375c84-7df3-4178-bb33-9cf5a2f23267); [Turo, "No photos available"](https://mobbin.com/screens/d42d83b3-58c5-481d-bade-4263c9098e25) with an action sheet for adding.
- Plain "No media, yet." with a sentence on how to fill it ([GoPro Quik](https://mobbin.com/screens/64bf1c4a-b38c-41e3-93a2-14e902d9335d)); a bare "No media yet" ([CapCut](https://mobbin.com/screens/13d25ebe-a058-4108-ae79-2c81856f6455)).
- A "0 found" state with a Retry link ([Uniswap](https://mobbin.com/screens/8d704584-9208-49c8-8982-5465a31c64e2)).
- A first-run checklist card with struck-through finished steps ([Speechify, "Great start!"](https://mobbin.com/screens/fc68b660-9afc-44d0-ba6b-2f31894a6e05)). A good model: connect the Archive Drive, back up the first batch, done.
- A local-storage reassurance banner stating that media stays on the phone ([Life Reset](https://mobbin.com/screens/7c57ecb3-595f-4a2f-89a9-ff0c789547a1)). Photoxpo can say the equivalent about the Archive Drive.

Photoxpo has two empty cases: first run (no Previews yet; the button is "Back up my photos" after choosing the Archive Drive), and drive not attached with Previews present, which is not empty and uses the banner in section 6.

## 6. "Drive not connected" states

Mobbin has no external-drive example. Companion-app "not found" screens share a pattern:

- Big title naming the missing thing, a short checklist of fixes, **Try again** as the primary button, Help as secondary ([Philips Hue](https://mobbin.com/screens/33c875bb-1b3c-4e08-8457-64e04e1ba387); [Amazon Alexa](https://mobbin.com/screens/a50fe432-f4cf-447a-8669-46546338f126); [Fitbit](https://mobbin.com/screens/5becafee-af2f-4621-87fc-e3863b2d36d4); [Oura](https://mobbin.com/screens/f529277c-9371-4aec-8380-225d82941229); [Tonal](https://mobbin.com/screens/ea593c8a-c8d0-4f0e-b103-f59537038a8c)).
- **A calm offline row with "last seen" and a Reconnect link, not an error.** ChatGPT's offline host shows a greyed laptop, "Offline", "Last seen 18 minutes ago" and Reconnect ([ChatGPT](https://mobbin.com/screens/635cf9e7-fad6-4e72-b13d-20c138e4af00)). This tone suits Photoxpo best, because an unplugged Archive Drive is the normal state most of the time. It also gives a natural place for "Last backed up: yesterday".
- Starlink keeps the rest of the app visible and puts the explanation in a card with two buttons ([Starlink](https://mobbin.com/screens/a79e31e4-17b1-4ae9-a9c7-1bc3239b3f83)).
- Numbered steps with a confirmation button ([Hatch Sleep](https://mobbin.com/screens/50415435-978b-4734-88ab-16fa725bc82a)) are heavy for Nandhu; prefer two or three lines at most.
- Avoid a mascot-style error ([CHOPT](https://mobbin.com/screens/ebc40138-872c-4dab-b77c-1abed601374e)) and a bare contact-support dead end ([MyDyson](https://mobbin.com/screens/6bb89f68-f1bb-40ed-93ae-3f1eed29322b)).

Takeaway: when the Archive Drive is missing, keep the whole Preview gallery usable and show a slim, non-red banner such as "Archive Drive not connected. Plug it in to back up or Load Originals", with the last-backed-up time. Open the full "not found" screen only when the person tries something that needs the drive.

## 7. Date-grouped grid and full-screen viewer

Grid:

- Apple Photos Library: edge-to-edge square tiles, large title, a Select button, a floating pill tab bar, the date under the title ([Apple Photos, Library](https://mobbin.com/screens/267187c5-b4a4-4ab1-ae40-f3693a4bf87b)).
- Lapse: a **Month / Day** switch floating at the bottom and a heading per day with a select circle ([Lapse](https://mobbin.com/screens/a4a476e4-2036-4563-b2ab-724f97076d36)). Good for about 600 GB of photos: the person can change density.
- corner: a **year strip** across the top (2025, 2024, ...) with month headings and rounded tiles ([corner](https://mobbin.com/screens/b084c6a7-0399-4d47-bb01-e86fd67bb933)). Years are a fast way through a long archive.
- AllTrails: one large date heading over a 3-column grid, filter pills at the bottom ([AllTrails](https://mobbin.com/screens/b6ef56ec-5bb2-41ab-b557-6dcc79c50530)).
- Google Photos: a floating date chip ("Wed, Feb 25") over the grid, a pill tab bar ([Google Photos, grid](https://mobbin.com/screens/aa710f37-8257-4681-a5ad-b546da46254b)).
- Selection mode: a header count with a bottom sheet of Delete / Cancel ([GoPro Quik](https://mobbin.com/screens/5c8e430f-ca56-433e-8001-7f049fe66e09)); a bottom bar with the count and two actions ([Apple News](https://mobbin.com/screens/0fb77b7c-cbad-4cb3-81de-25425cc6915d)).

Full-screen viewer:

- Few photo viewers appeared. Common shape: back or close top-left, overflow top-right, a centred rounded image, share and save at the bottom ([ElevenLabs](https://mobbin.com/screens/a13f2232-3eb5-4cd8-9557-89bb41595095), [Grok](https://mobbin.com/screens/6bb84977-32a0-40bc-acd5-646b73b2bb14)). Replika adds "1 of 2" and a date at the top ([Replika](https://mobbin.com/screens/e182efd7-8beb-490e-927b-1c1c1da7068c)).
- The Load action belongs where these apps put their download arrow (bottom right), but as a labelled button, since a bare arrow is ambiguous for a non-technical user.

## 8. Settings and storage meters

- Google Photos' Backup page: an account storage bar with legend, and "When to back up" toggles ([Google Photos, Backup settings](https://mobbin.com/screens/c1db11e3-92c7-4387-9881-a4daa8af9e5d)). Photoxpo needs only the Archive Drive name and free space, the last backed-up time, and whether Previews are on.
- SoundCloud's Storage page: a coloured bar with a legend (apps, downloads, cache, free) and a "Remove all downloads" action with explanatory text ([SoundCloud](https://mobbin.com/screens/04ffeafa-4fd7-4553-b2ee-4e2695f67667)). A phone-storage bar split into Previews, Originals not yet Backed up, and everything else would show Nandhu why the phone is full.

## Recommended patterns and screens to react to in the prototype

1. **Home screen: one state sentence and one count**, modelled on [Google Photos "Backup is on"](https://mobbin.com/screens/9e3dfb56-6846-41f2-a635-428bf3304a61) and the [library banner](https://mobbin.com/screens/0ede086e-7804-4325-a2a3-eeaf6d86091a), with a Pause control as in [Apple Photos](https://mobbin.com/screens/428aa147-6046-449d-98bb-02d0eebbb210).
2. **Per-file progress list behind a details link**, modelled on [GoPro Quik](https://mobbin.com/screens/d9d1710a-5585-42da-ae77-c4811a8ed7b0), plus the "you can leave this screen" reassurance from [Roku](https://mobbin.com/screens/a0850ac5-0fa4-45cf-b7e6-e93cf86b8cbf).
3. **Free up space as a sequence:** a check step ([scan modal](https://mobbin.com/screens/5ef03e66-42d5-412c-990c-357ca4745bd1)), an explainer with a count-and-action button ([Free up space](https://mobbin.com/screens/c61edbc7-37c4-4ed3-a95b-a4d505afc623)), the system delete prompt, then a result screen with gigabytes freed ([cleanup done](https://mobbin.com/screens/4613e863-fef1-4a17-8c3b-15f8a3b18025), without the upsell).
4. **Test a tick-box brake for the first delete only**, from [Google Photos' trash dialog](https://mobbin.com/screens/04970ba9-79ab-4fd3-aa69-e84c9b00d1c5). Check whether it reassures or annoys the builder and Nandhu.
5. **Preview tiles that look normal, with a small bottom-right badge** meaning "Original is on the Archive Drive", as in [Google Photos' grid](https://mobbin.com/screens/aa710f37-8257-4681-a5ad-b546da46254b). Use a drive or arrow glyph, not a cloud.
6. **Month/Day switch plus a year strip** for the grid, from [Lapse](https://mobbin.com/screens/a4a476e4-2036-4563-b2ab-724f97076d36) and [corner](https://mobbin.com/screens/b084c6a7-0399-4d47-bb01-e86fd67bb933), on Apple Photos' edge-to-edge tiles ([Apple Photos](https://mobbin.com/screens/267187c5-b4a4-4ab1-ae40-f3693a4bf87b)).
7. **Viewer with a labelled "Load Original" button and file size** (size-beside-arrow pattern from [Patreon](https://mobbin.com/screens/f6f36696-df97-416c-8ba2-d44e7925070b)), with a progress card and Cancel ([PlayStation App](https://mobbin.com/screens/cbf836ea-dbc8-446d-a1c6-494521f88613), [mymind](https://mobbin.com/screens/7a0e2d06-53b6-4a2c-9b45-353c6337c0f1)).
8. **Drive-not-connected as a calm offline row with "Last seen" and Reconnect**, from [ChatGPT](https://mobbin.com/screens/635cf9e7-fad6-4e72-b13d-20c138e4af00); the gallery stays usable. Use the full not-found screen ([Philips Hue](https://mobbin.com/screens/33c875bb-1b3c-4e08-8457-64e04e1ba387)) only when a drive-dependent action is attempted, and the single-OK dialog shape ([Stake](https://mobbin.com/screens/5b359542-cbf4-40c9-9b44-6feb698d0671)) for a Load tried without the drive.
9. **First run as a short checklist card** ([Speechify](https://mobbin.com/screens/fc68b660-9afc-44d0-ba6b-2f31894a6e05)) and a single-button empty state ([Partiful](https://mobbin.com/screens/37375c84-7df3-4178-bb33-9cf5a2f23267)).
10. **Phone storage bar** with a legend, from [SoundCloud](https://mobbin.com/screens/04ffeafa-4fd7-4553-b2ee-4e2695f67667) and the "% used" line in [Google Photos, Apps & devices](https://mobbin.com/screens/38594ba3-03e8-45f2-b7c4-9b661171cfd9).

### Words to avoid in the UI

The references use cloud wording and icons (cloud badge, "Syncing to iCloud", "uploaded", "restore", "download", "thumbnail"). `GLOSSARY.md` rules these out for Photoxpo. Use Archive Drive, Original, Preview, Backed up and Load, and do not show cloud glyphs.

## Gaps for a follow-up

- Android-native Google Photos and Samsung Gallery (Mobbin cannot show them). A look on a real Galaxy S24 Ultra would close this.
- How Samsung Gallery, Immich and Synology Photos mark remote-only photos, and iCloud's Optimize Storage flow.
- Any app that fetches a full-quality file from a local, removable drive.
