# Photoxpo

An Android app that keeps a person's full-quality photos on a USB-C external drive while leaving small viewable copies on the phone, like a cloud photo library that is entirely local.

## Language

**Archive Drive**:
The external USB-C drive that holds the full-quality files.
_Avoid_: Cloud, backup disk, external storage, HDD

**Original**:
The full-quality photo or video file as the camera produced it.
_Avoid_: Full-res, high-quality copy, master

**Preview**:
The small, low-quality copy of an Original kept on the phone so it can be seen without the Archive Drive attached.
_Avoid_: Thumbnail, low-quality copy, proxy

**Backed up**:
The state of an Original that has been copied to the Archive Drive and checked to be identical to the source.
_Avoid_: Synced, uploaded, saved

**New**:
An Original that is on the phone but not yet Backed up. Home counts them so a person knows what is waiting.
_Avoid_: Pending, unsynced, queued

**Load**:
Fetching an Original from the Archive Drive back onto the phone so it can be used.
_Avoid_: Restore, download, pull

**Backup run**:
One pass of copying new Originals to the Archive Drive, recorded on the drive with when it ran, how many files and bytes, and whether it was checked.
_Avoid_: Sync, job, session

**Manifest**:
The Archive Drive's own record of which Originals are Backed up, kept as one file per Backup run, so a new phone can rebuild from the drive alone.
_Avoid_: Index, catalog, database

**Free up space**:
Removing an Original from the phone after it is Backed up, leaving its Preview behind.
_Avoid_: Clean up, delete, offload

**Plus**:
The one-time purchase that unlocks the extras beyond what every person gets for free. Backing up, the Backed up check, Load and browsing Previews are never part of it.
_Avoid_: Pro, Premium, Subscription

**Free allowance**:
The amount of space a person can Free up without Plus, enough to see the whole journey work.
_Avoid_: Trial, quota, limit
