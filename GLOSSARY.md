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

**Load**:
Fetching an Original from the Archive Drive back onto the phone so it can be used.
_Avoid_: Restore, download, pull

**Backup run**:
One pass of copying new Originals to the Archive Drive, recorded on the drive with when it ran, how many files and bytes, and whether it was checked.
_Avoid_: Sync, job, session

**Manifest**:
The Archive Drive's own record of which Originals are Backed up, kept as one file per Backup run, so a new phone can rebuild from the drive alone.
_Avoid_: Index, catalog, database
