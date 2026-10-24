# Changelog

## Unreleased

- Hide the task overlay after two minutes without Slayer activity or sidebar interaction (configurable, 0 disables idle hiding); restore it on activity or opening the sidebar, and hide on logout or task completion. Task timing and saved history are unaffected.

- Reduce Simple-mode clutter: keep the current-task summary visible, disclose task options and history on demand, and reserve the finish clock for Detailed mode.

- Add a standalone synthetic UI preview, scenario PNG rendering and optional rebuild watcher.
- Add prominent ETA, sidebar mode buttons, independent overlay detail, recording-progress explanation, waiting-state context and inline deletion Undo.

- Polish Simple mode: ETA-first layout with visible Slayer XP/hr, compact expandable history, current-task priority and concise source labels.

- Preserve unreadable or unsupported saved history and explain recovery in the sidebar.
- Show live first-task estimates without misleading completion requirements.
- Explain whether the estimate comes from lifetime history, this task, or both.
- Simplify the default display and label recording overrides explicitly.
- Add a read-only personal-history comparison view.
- Cache detached history snapshots and coalesce sidebar updates.
- Add portable history export/import, guarded merge, backups, checkpoint recovery and deletion Undo.
- Add opt-in segmented timing with Pause/Resume, schema-6 migration backups and separate legacy cohorts.
- Add recent 5/10/25 estimate windows; preserve lifetime defaults and label legacy fallback.
- Share history selection across the sidebar, preview and experimental Mortimer ranges.
- Retain completion results with copy/review/dismiss; add searchable and sortable history.
- Reject invalid numeric imports and report failed saves while retaining in-memory tracking for retry.
- Add automated build/tests for Java 11 and regression coverage.

Release validation, gameplay screenshots and Plugin Hub publishing are pending.
