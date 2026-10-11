# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Native Kotlin + Jetpack Compose, Room, foreground service, minimum SDK 34 (settled on the wayfinder map). The design prototype is a throwaway single static HTML file in a phone frame. The user said iOS may come later ("adaptive later"); the map still rules the iOS port out of scope for this effort.

## Users
Nandhu: non-technical, owns a Galaxy S24 Ultra whose 256 GB storage is full of photos and videos (~600 GB), plus a Seagate 2 TB portable HDD on a USB-C adapter. A relative builds the app and is the hands-on partner. The app must be dead simple. Works on any Android 14+ phone and any USB drive, one at a time.

## Product Purpose
Keeps a person's full-quality photos on a USB-C **Archive Drive** while leaving small viewable **Previews** on the phone, like a cloud photo library that is entirely local. Success: Nandhu can back up, see that it is **Backed up**, free space, browse Previews, and **Load** an **Original** back when he wants it, without understanding any of the machinery.

## Positioning
Local-only, no accounts, no cloud: the drive in his pocket is the library. A Preview tile is honest about where the Original lives (on the Archive Drive) and Load brings it back.

## Operating Context
Used at home, plugging the drive into the phone, opening the app, and waiting through long copy jobs (hours of HDD writes). The drive is often not attached, so the app is mostly used in a "drive not connected" state, browsing Previews.

## Capabilities and Constraints
- Terms from `GLOSSARY.md`: Archive Drive, Original, Preview, Backed up, Load. Avoid the terms listed under _Avoid_ there (cloud, thumbnail, synced, restore, and so on).
- Source is `DCIM/Camera` only, photos and videos, oldest first. One drive, user opens the app after plugging in.
- **Backed up** means the copy was read back from the drive and its hash matched.
- The delete / "Free up space" action exists in the product but is hidden in the first test build. It is never silent and always ends in the system delete confirmation.
- Previews are browsed in an in-app gallery, not written into Samsung Gallery. Load copies the Original into `Pictures/Photoxpo Loaded`.
- Failure states to design for: drive pulled mid-copy, verification failed.
- Undecided: final visual language and screen structure (this prototype's question).

## Evidence on Hand
UI/UX research from the Mobbin connector (iOS/web only, no Android or external-drive examples): see `research/ui-references` branch, `docs/research/ui-references.md`. No real screenshots of Nandhu's phone exist. Do not fabricate photos of real people; use abstract placeholders.

## Product Principles
1. One sentence of status, one count, one obvious button per screen.
2. Never claim a photo is safe until it is Backed up and verified.
3. A missing Archive Drive is a calm, normal state, not an error.
4. Say where the Original is, in plain words ("On your drive"), never in storage jargon.
5. Destructive actions are slow, explicit, and absent until trusted.

## Accessibility & Inclusion
Nandhu is non-technical and may be older or less comfortable with phones: large touch targets, high contrast, plain words, no reliance on colour alone, readable at large system font sizes. Specific age and vision needs not confirmed.
