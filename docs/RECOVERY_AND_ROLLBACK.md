# Saved-data protection and compatibility

This release stores schema 5, matching the existing task timing format. Schemas 1–4 migrate with their original payload backed up.
Experimental segmented timing is deferred to `codex/experimental-segmented-timing`.
History import/export, merge, backup restore, checkpoint recovery and deletion Undo are deferred to `codex/history-transfer-recovery`.

## Unreadable data or failed writes
Unreadable or unsupported data is preserved, and automatic saves are blocked. Tracking continues in memory only.
Use Help & data → View original data for recovery to inspect and copy the original contents before investigating the problem.
This release has no file import or restore interface. Do not erase the only copy of unreadable history.

A failed write shows a warning and retains the current task in memory. Saving retries at the next checkpoint.
Copy an unsaved completion result if you need to preserve it before closing the client.
Normal stored records can be inspected and copied through Help & data → View stored stats.
Individual runs can be excluded from averages; deletion and reset actions cannot be undone.

## Returning from the timing development build
Schema-6 history containing only ordinary timing is validated, backed up and saved as schema 5.
Completed totals and the active checkpoint are retained. A history containing experimental timing
is preserved unchanged and enters recovery mode; this release cannot safely reinterpret those samples.
Copy the original data and use the timing branch to access it. Keep a separate copy before changing builds.
The automatic pre-migration backup remains in account-specific storage; restoring it requires manual recovery.

## Verified locally
Automated tests exercise malformed, oversized and future-data preservation, account isolation,
schema migration backups, ordinary development-history conversion, experimental-history preservation
and failed-write retry. Manual recovery and live-client gameplay remain separate validation tasks.
