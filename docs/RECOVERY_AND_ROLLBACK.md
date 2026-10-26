# Recovery and rollback

This release stores schema 5, matching the existing task timing format. Schemas 1–4 migrate with their original payload backed up.
Experimental segmented timing is deferred to `codex/experimental-segmented-timing`.

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

## Returning from the timing development build
Schema-6 history containing only ordinary timing is validated, backed up and saved as schema 5.
Completed totals and the active checkpoint are retained. A history containing experimental timing
is preserved unchanged and enters recovery mode; this release cannot safely reinterpret those samples.
Export the original data and use the timing branch to access it, or restore a compatible pre-experiment backup.
Keep a separate export before changing builds: the automatic recovery backup can be replaced by later data actions.

## Verified locally
Automated tests exercise malformed/future preservation, stale profile rejection, import checkpoint protection, duplicate/conflicting
merge IDs, pruned-total rejection, Undo, schema migration backups, ordinary development-history conversion,
experimental-history preservation and failed-write retry.
File chooser workflows and lifecycle recovery still need live-client verification.
