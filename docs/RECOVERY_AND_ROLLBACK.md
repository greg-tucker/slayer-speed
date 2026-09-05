# Recovery and rollback

This unreleased branch stores schema 6. Schemas 1–5 migrate with their original payload backed up.
The retained legacy totals and old checkpoint timing remain legacy; they are not reconstructed as segmented data.

## Normal portable history
1. Use Help & data → Export history to save a JSON file.
2. Full backup additionally includes the active checkpoint.
3. Import and replace previews the replacement before applying it. The currently active assignment is preserved.
4. Merge only accepts histories whose retained records reconstruct their aggregates. Identical IDs are idempotent; conflicts fail.
5. Recover checkpoint is separate and refuses to replace an active assignment.

## Unreadable data or failed writes
Unreadable/future data is preserved and automatic saves are blocked. Export the original before using recovery.
A failed write leaves tracking in memory and shows a status; checkpoint writes retry. Copy an unsaved completion result.
Restore a known-good backup through the reviewed import. Do not erase the only copy of the unreadable payload.

## Downgrade
Downgrade is not an in-place schema conversion. Old plugin builds do not understand schema 6.
Export a full schema-6 backup first and keep it outside RuneLite. Also preserve the original pre-migration backup.
Disabling experimental timing is safe on this build: the current task finishes under its stored policy, future tasks use legacy.
For an actual old-build downgrade, stop recording and restore the compatible pre-migration payload to the correct account profile
while RuneLite is closed, using an explicit manual recovery procedure. Keep both originals. Do not launch an old build against
the only copy of schema 6. The new import path migrates old data forward; it does not prepare an old-build configuration.
A real old-client downgrade rehearsal remains a release gate; no such rehearsal has been performed.

## Verified locally
Automated tests exercise malformed/future preservation, stale profile rejection, import checkpoint protection, duplicate/conflicting
merge IDs, pruned-total rejection, Undo, schema migration backups, separated timing buckets and failed-write retry.
File chooser workflows and lifecycle recovery still need live-client verification.
