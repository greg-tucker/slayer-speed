# Implementation progress

Updated: 6 September 2026. Resume from this file and UX_IMPLEMENTATION_PLAN.md.
Baseline: c5062d7814ac33fbe0048014f24cdb68a7cf0c13. No commits or publishing requested.

## Current checkpoint
P0–P8 code is implemented locally. Standalone preview harness and native mockup implementation are complete. Latest full suite passed: 110 tests, zero failures/errors/skips, including mode switching, inline Undo, progress accounting and independent overlay detail.
Next resume point: iterate with scripts/preview-ui.ps1 -Watch (see docs/UI_PREVIEW.md), then execute the live-client checklist in docs/RELEASE_VALIDATION.md, capture real screenshots and run the consented beta.
No publishing, default promotion, real gameplay captures or beta study has been performed.

## Status
| Phase | State |
| --- | --- |
| P0 baseline / data-load protection | Implemented and tested locally; hosted CI not run |
| P1 first-task states / provenance | Implemented; numerical parity and early estimates tested |
| P2 compact UI / safe comparison | Implemented; synthetic compact/history fixtures inspected; live/high-DPI checks pending |
| P3 README / metadata | Updated for this unreleased branch; real screenshots and reader checks pending |
| P4 portable history / Undo | Implemented; transactions, account switches, malformed data and retry tested |
| P5 explicit timing / pause | Implemented, opt-in; timing/migration/boundary tests pass; live lifecycle validation pending |
| P6 opt-in recent estimates | Implemented; eligibility/order/partial/pruned/fallback tests pass; lifetime remains default |
| P7 completion / history | Implemented; retained result, copy/review/dismiss, search/current task/sort; local tests pass |
| P8 personal preview / Mortimer | Shared selection/calculation, inspectable samples and labelled location/timing source; tests pass |
| P9 beta / default promotion | Worksheets prepared; real accuracy, retention and default decisions not executed |

## Verified baseline
77 tests passed freshly during planning with RuneLite 1.12.38 and Java 11 compilation target. Subsequent P0/P1 source changes passed the full suite before interruption.

## Decisions and constraints
- Preserve unknown historical sample counts; never invent missing samples.
- Recovery before migrations; never overwrite malformed/future data automatically.
- Timing policy cohorts must remain separate.
- Live client screenshots and human retention/accuracy studies cannot be replaced by synthetic tests.
- Local modifications only. Prior review/plan documents were already untracked; preserve them.
- Windows sandbox process creation fails with deny-read ACL errors. Use authorized elevated shell for workspace reads/edits; Python scripts via PowerShell stdin are working. apply_patch creates files but can fail reading existing files.

## Validation journal
- Planning baseline: 77 tests, zero failures/errors/skips.

- P0 implemented recovery-required state, original payload access, blocked saves/resets, account-bound storage adapter and CI. First compile exposed older Gson API; changed parser call to supported API. Tests rerun with P1 next.
- P1 extracted pure TaskEstimateService, immutable source result, fixed first-task messages and removed certainty adjectives from displayed estimates. Added numerical parity/early-estimate cases.
- Build runtime: Gradle 8.10 on Oracle JDK 23.0.2, compiling release 11.

- P0/P1 full suite passed before interruption (82 tests expected: 77 baseline + 5 new). P2 adds a snapshot-only preview, explicit recording label, compact metrics, progress bar, immediate settings updates and detached cached history/EDT coalescing.

- P2 full suite passed. Visual inspection found clipped onboarding text; shortened/wrapped it. P3 docs/metadata aligned with implemented behavior and created docs/RELEASE_VALIDATION.md for real captures, gameplay and beta gates. Next: P4 transactions, backup and file actions.

- P4 UI now offers file export/import, checkpoint recovery, backup restore and Undo. File reads/writes run off UI/client threads; import commit rechecks profile/history revision. Merge rejects opaque pruned totals and conflicting IDs. Added transaction tests; validation pending.

- P5 verification found a malformed encounter comparison introduced during cohort changes; corrected it. Added matched-numerator, same-tick, manual pause/resume, logout-resume and cohort isolation regression tests. Existing defaults remain unchanged.

- P5 full suite passed with the new timing regression tests. P6 now has an opt-in recent 5/10/25 selector shared by the main view and comparison; eligibility precedes global sorting/truncation and lifetime remains the default. P6 tests and Mortimer integration next.

- P6 and shared Mortimer estimator passed the full suite. P7 now retains a structured completion result across the next assignment, distinguishes first full task from a PB, offers copy/dismiss, and adds history search/current-task filtering/sorting. UI mutations are bound to the displayed account/task context; history rendering now reuses immutable snapshots. Verification pending.

- P7 full suite passed. Final alignment review added explicit legacy lifetime fallback for sparse segmented history without cross-policy blending, recent-sample inspection, and a review action on the retained completion. Corrected clipped onboarding/search layout, strengthened numeric import checks, and added failed-save status. Final regression/render pass pending.

## Implementation map for resuming
- Estimate calculations: calculation/TaskEstimateService, HistoryEstimateService, EstimateWindow.
- Timing/cohorts: model/ActiveTask, TaskRun, TaskKey, TaskStatistics; tracking/ActiveTimeTracker and TaskTracker.
- Data transactions: persistence/TaskHistoryRepository, HistoryValidator, HistoryTransfer, ProfileStorage.
- User flows: ui/SlayerSpeedPanel, CompletionResult, TaskPreviewPanel, HistoryFileActions; MortimerEstimateService.
- Policy/rollback: docs/TIMING_POLICY.md and docs/RECOVERY_AND_ROLLBACK.md.
- Release/beta: docs/RELEASE_VALIDATION.md, beta-predictions.csv and beta-retention.csv.

## Deliberate implementation choices
- Experimental segmented timing remains off. Missing compatible history may use explicitly labelled legacy lifetime fallback; live counters never blend into that fallback.
- Recent windows are opt-in and only use retained eligible records. Lifetime sample counts remain unknown where older partial records were pruned.
- Segmented untimed anchors conservatively make whole-task duration/PB partial. Rate estimates remain available from matched intervals.
- History sorting offers recent activity, task name and pace; it does not compare raw task durations across different assignment sizes.
- All phase changes currently share an uncommitted working tree. The planned staged releases still need review/separation before shipping; do not publish this whole branch by implication.
- Actual high-DPI/client lifecycle behavior, import dialogs, old-client downgrade and growth/retention outcomes remain unverified. Synthetic fixtures are developer checks only.

- Final verification: Gradle offline full suite BUILD SUCCESSFUL (25 seconds), 101 tests, zero failures/errors/skips. Java 11 target; local JDK 23.0.2 and cached RuneLite 1.12.38. git diff --check reports no whitespace errors. Compact and history fixtures inspected; search-label contrast corrected. No commits or publishing performed.

- 6 September live screenshot review: fixed descriptive overlay text occupying RuneLite's narrow right-value column; encounter/source now use full-width text. Wrapped sidebar timing/profile text, removed duplicate search caption, separated history heading, and replaced empty metric boxes with an explicit unavailable state. Added a RuneLite-font overlay render regression at 180/220 px. Full suite passed: 102 tests, zero failures/errors/skips. Corrected fixtures inspected; live-client recheck pending.

- 6 September Simple-mode polish: ETA/XP summary now precedes recording controls; Simple keeps live and historical Slayer XP/hr. Collapsed history retains XP and duration/sample summary, with other rates/timing details expandable. Current assignment sorts first by default; all-time/recent/older-fallback labels stay distinct and full provenance remains in tooltips/Detailed mode. Suspended display reads Waiting for task activity. Full suite passed: 105 tests, zero failures/errors/skips. Compact/expanded/overlay fixtures inspected; removed reserved spacing for hidden Detailed sections. Live-client recheck remains pending.

- 6 September second live screenshot review: Detailed overlay counts and pace now use full-width rows, avoiding the narrow right-value wrapping. Detailed history omits unavailable task-unit rate rows. Added narrow-width/larger-font Detailed overlay regression. Full suite passed: 106 tests, zero failures/errors/skips; large-font fixture inspected. Live-client recheck pending.

- 6 September preview/mockup work started: added standalone previewUi and headless renderUi tasks using real sidebar/overlay classes and synthetic-only actions. Scenario/font/width controls and PNG export implemented; no Client/ConfigManager/credential loading. First compile pending, then mockup integration.

- Preview first build/render succeeded with RuneLiteLAF. Mockup integrated into production UI: prominent ETA, synced Simple/Detailed buttons, independent overlay mode (Follow sidebar by default), current-task badge, labelled recording gap, typed waiting explanations and inline Undo. Watch/restart launcher and docs/UI_PREVIEW.md added. Interaction regressions and full scenario renders are being verified.

- Preview/mockup completion: previewUi, renderUi and the Windows rebuild watcher work with synthetic data only. The watcher was launched successfully and recovered from a compile failure while preserving its previous preview. Actual PNG review covered active, waiting, deleted, long-name and narrow/larger-font states; this exposed and corrected summary alignment and text clipping. Full suite: 110 tests, no failures/errors. Live RuneLite docking, scaling and account lifecycle checks remain release gates. No commits or publishing performed.

- Final harness hardening: watcher compilation now uses build/preview-harness to avoid competing with test/client compilation. Verified isolated watcher launch alongside the final test/render build (BUILD SUCCESSFUL), and inspected final normal/large-font PNGs. The standalone preview is open; close its window to stop the watcher.
