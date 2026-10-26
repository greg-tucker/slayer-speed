# Changelog

## Unreleased

- Replace reflective history-field discovery with an explicit numeric schema; simplify tests to use Guice injection and remove private RuneLite access and automated screenshot/layout assertions.

- Add separate Overlay and Infoboxes settings sections with independent enable switches, preserving existing display preferences. Independently toggle compact time-remaining, physical kills/hr and Slayer XP/hr infoboxes with shared auto-hide, task tooltips and separate positioning identities.

- Fix overlays remaining after disabling the plugin or switching RuneLite configuration profiles: run inventory initialization/cleanup on the client thread and remove the UI immediately on shutdown. Guard queued initialization against rapid disable/re-enable and stop rendering both overlays while disabled (issue #1).

- Cover plugin logout, account switching, restart and overlay visibility through lifecycle tests; require real dialog coverage under a virtual display in CI.
- Refresh the sidebar once after each dispatched panel action instead of repeating the same calculations.

- Mark checkpoint recordings with missed progress as partial so they cannot distort full-task durations or personal bests.
- Close the previous comparison window before opening another, keeping task/account cleanup effective.

- Hide the task overlay after two minutes without Slayer activity or sidebar interaction (configurable, 0 disables idle hiding); restore it on activity or opening the sidebar, and hide on logout or task completion. Task timing and saved history are unaffected.

- Reduce Simple-mode clutter: keep the current-task summary visible, disclose task options and history on demand, and reserve the finish clock for Detailed mode.

- Add a standalone synthetic UI preview, scenario PNG rendering and optional rebuild watcher.
- Add prominent ETA, sidebar mode buttons, independent overlay detail, recording-progress explanation and waiting-state context.

- Polish Simple mode: ETA-first layout with visible Slayer XP/hr, compact expandable history, current-task priority and concise source labels.

- Preserve unreadable or unsupported saved history and explain recovery in the sidebar.
- Show live first-task estimates without misleading completion requirements.
- Explain whether the estimate comes from lifetime history, this task, or both.
- Simplify the default display and label recording overrides explicitly.
- Add a read-only personal-history comparison view.
- Cache detached history snapshots and coalesce sidebar updates.
- Defer history import/export, merge, backup restore, checkpoint recovery and deletion Undo to `codex/history-transfer-recovery`. Preserve unreadable data and read-only access to its original contents.
- Add recent 5/10/25 estimate windows while preserving lifetime defaults.
- Defer experimental segmented timing and Pause/Resume to the `codex/experimental-segmented-timing` branch. Keep schema 5 for this release; back up and convert ordinary development history, preserving experimental history for recovery.
- Share history selection across the sidebar, preview and experimental Mortimer ranges.
- Retain completion results with copy/review/dismiss; add searchable and sortable history.
- Reject invalid numeric saved data and report failed saves while retaining in-memory tracking for retry.
- Detect replacement assignments before restoring checkpoint progress, preventing progress from two same-name assignments being combined after restart.
- Add automated build/tests for Java 11 and regression coverage.

Release validation, gameplay screenshots and Plugin Hub publishing are pending.
