# Slayer Task Speed: implementation plan for UX, downloads and retention

Status: ready for implementation, with live-play validation gates identified. Prepared 5 September 2026 against source revision `c5062d7814ac33fbe0048014f24cdb68a7cf0c13`. No feature implementation is included in this document.

This plan implements [UX_AND_GROWTH_REVIEW.md](./UX_AND_GROWTH_REVIEW.md). For this initiative, use the ordering and acceptance criteria here. [IMPROVEMENT_PLAN.md](./IMPROVEMENT_PLAN.md) remains the engineering risk backlog; its broad refactors are not prerequisites for every small UX change. [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) and [PLUGIN_PLAN.md](./PLUGIN_PLAN.md) are historical initial-build notes.

## 1. Goals and scope

**Product promise:** estimate remaining Slayer task time from the player's own task progress, pace and history.

| Goal | Implementation contribution | Evidence of success |
| --- | --- | --- |
| G1: make installation more likely | Clear ETA-first description, real screenshots, short installation guide | New users can explain the benefit before installing; record reach separately from retention |
| G2: make the first session useful | Correct onboarding, visible estimate source, honest unavailable states | Users see and understand the first usable live estimate without completing a whole assignment first |
| G3: make continued use worthwhile | Readable display, dependable time semantics, adaptive estimates, recoverable history | Fewer unexplained estimates or lost records; returning beta users report continued usefulness |
| G4: preserve the core use case | Automatic personal ETA remains the main feature | Added UI and data work directly supports estimating, explaining or reviewing task time |

Preserve effective task progress as the ETA input, separate physical kill counts, encounter separation, account-profile isolation, and the absence of a plugin-operated server. Keep the current name and plugin identity. Retain detailed metrics for users who want them.

Out of scope: general Slayer optimization, profit dashboards, automatic gear recommendations, leaderboards, new accounts/cloud services, additional supply categories, unsolicited notifications and mandatory telemetry. The task preview is a later extension of the same ETA calculation, not an assignment recommendation engine.

## 2. Code audit: corrections and constraints incorporated

Java paths in this document are relative to `src/main/java/com/slayerspeed/`.

| Finding checked against the implementation | Planning consequence |
| --- | --- |
| `SlayerSpeedPlugin.createViewModel()` can fall back to live rates before `currentRateMinimumUnits`; that setting controls historical blending | Do not build an inaccurate “10 kills to unlock” countdown. Preserve numerical behavior in the first UX release |
| `SlayerSpeedPanel.update()` discards the no-history learning message; onboarding claims completion is required | Fix presentation and message routing, not a nonexistent lack of live estimates |
| `TaskStatistics.applyRunToAggregates()` includes completed partial observations in rates; full-task duration and PB eligibility are narrower | Count eligible rate samples separately from fully observed tasks. Never say “10 full tasks” when partial observations contribute |
| History is pooled lifetime data; pruning recent runs does not subtract aggregate contributions | A recent estimator must query raw eligible runs. It cannot use lifetime totals and merely change the label |
| Lifetime storage does not retain a total count of completed partial observations after pruning | Exact lifetime rate-sample count may be unknown. Do not calculate it from the shorter retained-run list |
| `Confidence.fromSample()` uses counts, not variance | Replace confidence adjectives with evidence about samples first. Statistical ranges require a separate validated method |
| The finish clock uses the selected historical/blended ETA plus the current clock time | Use “If you continue at this estimated pace,” not a promise based exclusively on current pace or future breaks |
| `ActiveTimeTracker.pause()` resets an activity timestamp; it is not a durable manual pause state | Manual pause needs an explicit policy and matched time/progress accounting; wiring a button to this method is insufficient |
| Manual encounter selection is checkpointed and is used by `ActiveTask.finish()` | Separate read-only comparison from a deliberate recording override |
| `TaskTrackerTest` already covers a same-name assignment after confirmed completion | Extend missing-boundary cases; do not assume this behavior has no coverage or rewrite it wholesale |
| `TaskHistoryRepository` can start empty after a bad load and subsequently save | Add a write-protected recovery state before migrations or stronger promotion |
| Existing Simple/Detailed modes, run exclusion, PBs, summaries and Mortimer estimates already exist | Enhance these features rather than implementing duplicates |

The existing `Schema 4 -> current` migration assigns `CURRENT_SCHEMA_VERSION` directly. Before introducing another schema, make migrations advance through explicit versions so old data cannot skip a newly added step.

## 3. Delivery sequence

Each work item is a separate reviewable change. Relative sizes include its targeted verification; they are not date estimates.

| ID | Work item | Depends on | Size | Goals |
| --- | --- | --- | --- | --- |
| P0 | Baseline checks and minimum data-load protection | None | Small–medium | G3, G4 |
| P1 | First-task states and truthful provenance | P0 baseline | Medium | G2, G3 |
| P2 | Compact presentation and safe encounter comparison | P1 | Medium | G2, G3, G4 |
| P3 | README, metadata and release presentation | P1, P2; P0 protection before release | Small | G1, G2 |
| P4 | Export/import, recovery and reversible deletion | P0 | Medium–large | G3 |
| P5 | Explicit timing states and manual pause | P1, P4 before schema changes | Medium–large | G3, G4 |
| P6 | Recent-performance estimates | P1, P4, P5 timing contract | Medium–large | G3, G4 |
| P7 | Completion results and searchable history | P2; P6 for new pace comparisons | Medium | G3 |
| P8 | Personal task preview and Mortimer consistency | P2, P6 | Medium | G3, G4 |
| P9 | Beta evaluation and promotion of new defaults | Relevant work items above | Ongoing release work | G1–G4 |

Release A: P0–P3, without a statistics schema migration or estimator formula change. Release B: P4. Release C: P5. Release D: P6 initially opt-in, then a default only if P9 supports it. P7 can ship independently where it does not depend on P6. P8 is optional until core validation passes. Promote the P6 default separately from timing and schema changes.

This ordering deliberately moves data protection before migration, and separates adaptive estimates from timing changes so regressions can be attributed and rolled back.

## 4. Work items and acceptance criteria

### P0 — Establish the baseline and prevent silent data replacement

Touch `TaskHistoryRepository`, `TaskHistoryStore`, the panel/view model, relevant persistence tests, and a small CI workflow.

1. Run the existing suite and record the resolved RuneLite version, Java version and source revision. Do not treat old reports or Gradle UP-TO-DATE output as fresh test execution.
2. Add load outcomes: empty/new profile, successfully loaded, and recovery required. Malformed or future-schema payloads must remain intact. Block automatic history/checkpoint writes and migrations while recovery is required.
3. Show a non-blocking “Saved history could not be loaded” state with a way to copy the original data. Distinguish “tracking in memory” from “saved.” A deliberate start-fresh action must offer preservation of the original payload first. Profile changes must not transfer recovery state or writes to another account.
4. Add CI for the existing Java 11 target and tests. Retain `build=standard`; no new runtime dependency is needed. Use targeted tracking regression fixtures when changing tracking; a comprehensive replay framework is not a prerequisite for copy fixes.

Acceptance: corrupt and unsupported payloads survive startup, checkpoint attempts and profile switches unchanged. Valid data still loads/saves. CI runs the current suite. Recovery is actionable without hiding the current task.

### P1 — Make first-task estimates understandable

Touch `SlayerSpeedPlugin.createViewModel`, `SlayerSpeedViewModel`, `SlayerSpeedPanel`, `SlayerSpeedOverlay`, configuration descriptions and calculation/panel tests.

1. Extract just enough estimate calculation into a pure `TaskEstimateService` to return numbers plus structured source information. Characterize the current formulas before extraction; do not combine the extraction with a changed averaging method.
2. Return availability/reason, source (`LIVE`, `HISTORY`, `BLENDED`), encounter selection origin, location scope, rate-sample count when known, fully observed task count, live contribution and window policy. Represent unknown sample counts explicitly. Keep these separate from eventual tracking/pause state. Render the same result in the panel and overlay.
3. Use “Waiting for task activity” before a usable rate, “Early estimate — this task only” for a live-only rate, and accurate history/blend descriptions thereafter. A valid live ETA requires positive eligible progress and duration and a finite positive rate. Do not introduce a new fixed warm-up delay in this release.
4. Replace “Finish one task to unlock estimates” with “A live estimate appears as you make progress. Completed tasks build your personal history.” Route learning text through the view model. Keep `showLearningProgress` for optional extra explanation, not essential missing-data reasons.
5. Show “History: N completed samples” only when the contributing count is known, distinguishing full and partial observations in details. For existing lifetime totals, use “Lifetime history · N fully observed tasks,” with “Total rate-sample count unavailable” where pruned partial observations prevent reconstruction. Remove unsupported Low/Medium/High certainty wording from user-facing estimates, including Mortimer if enabled. Before P6, label historical scope as lifetime, not recent.
6. Mark history-selected encounters as “Assumed until detected” before a confirming kill; do not label suggestions as detected. Preserve the current conservative treatment of old unknown encounter data.

Acceptance: first task, zero timing, live-only below/above blending threshold, history-only, blended, no matching profile, partial observation and old mixed data all show consistent text and numbers. Panel tests exercise actual service results rather than only manually assembled strings. Add numeric parity checks around extraction.

### P2 — Calm the display and separate preview from recording

Touch the panel, overlay, display configuration and encounter-selection handlers.

1. Keep existing serialized `SIMPLE` and `DETAILED` values. In Simple, emphasize task, remaining count, approximate ETA, short source/status and the configured finish clock. Keep detailed rate fields accessible in Detailed; retain existing show/hide preferences where relevant. Do not silently reset settings on upgrade.
2. Keep cannon information conditional and clarify “Cannonballs needed” as an estimate of consumption, not inventory stock. Label it as a historical cannon-use scenario until actual use is observed. Move the superior-XP explanation to details/tooltip.
3. Add a progress indicator only when the initial amount is known and valid. Show “Tracked since X remaining” for mid-task installation; do not infer full observation from a partially filled bar.
4. Group raw records and resets under Help & data. Keep data recovery prominent when action is needed. Use readable text, contrast and keyboard-accessible controls at narrow/high-DPI layouts.
5. Keep the main ETA associated with the recording encounter. Add a separate read-only “Compare estimates” view with its own ephemeral selected encounter. A comparison uses matching historical data only; it must not blend in live activity from another encounter or alter checkpoints.
6. Rename the existing mutating action to “Record this task as…” with a visible override and Return to Auto. Explain that it assigns the whole run to that profile; it does not retroactively separate a mixed run. Retain existing overrides on upgrade, but make them visible. Reset comparison state on assignment/profile changes.
7. Refresh immediately on relevant configuration changes. Use immutable snapshots from the client thread for Swing; keep file operations off both the client thread and EDT. Coalesce changed UI work without undertaking the entire architecture rewrite.

Acceptance: previewing Araxxor during an Araxyte run leaves the recording profile, current-run counters and checkpoint unchanged; an explicit override still works. Existing settings survive upgrade. Validate no-task, first-task, normal, boss, cannon, long-location-name and expanded-history layouts at 100%, 150% and 200% scaling.

### P3 — Explain the benefit before installation

Touch `README.md`, `runelite-plugin.properties`, `@PluginDescriptor`, `CHANGELOG.md` and screenshot assets under `docs/images/`.

1. Lead with “How long will this Slayer task take?” and use: “Estimate remaining Slayer task time from your own pace, with personal history, XP rates and cannonball estimates.” Synchronize metadata and in-code descriptions/tags.
2. Add concise Plugin Hub installation steps using the unchanged display name. Explain automatic tracking and first-task learning before development instructions.
3. Capture three real, post-change client states: compact ETA, an estimate with history source, and completion/history. Keep synthetic render fixtures for tests, clearly distinguished from promotional captures.
4. Mention that the estimate reflects tracked task activity, not a forecast of future breaks. Describe storage as RuneLite account-profile data and no plugin-operated server; avoid making claims about RuneLite configuration synchronization beyond the plugin's control.
5. Add troubleshooting for missing assignment data, early estimates, mixed encounters, pause behavior available in that release, and recovery. Prepare a short demo and release notes once the corresponding features work.

Acceptance: a new reader understands the main benefit and installation path in a brief reading; all screenshots/copy match the shipped release. Do not advertise later phases or guaranteed accuracy/growth. Metadata changes reach Plugin Hub through its normal commit update process; changing the local README alone is not a release. Follow the [official Plugin Hub guide](https://github.com/runelite/plugin-hub/blob/master/README.md).

### P4 — Make personal history portable and recoverable

Touch persistence, validation/codec helpers, data actions and persistence tests. Implement this while preserving current estimation behavior.

1. Add a versioned export envelope with complete stored history and declared schema. Offer a normal history export without active checkpoint and a clearly named full recovery backup when checkpoint preservation is intended. Ordinary import must not replace a live active task. Scope every operation to the selected RuneLite account profile without exporting account identifiers unnecessarily.
2. Validate imports before mutation: size limit, supported schema, required fields, meaningful non-negative values, valid statuses, unique IDs, key consistency and safe arithmetic. Validate legacy data according to its documented schema; do not reject it merely because old versions lacked today's provenance. Do not enforce equality between physical kills and task progress.
3. Export before reset/migration and retain a bounded last-good backup under a separate key. Do not overwrite the last-good backup with failed or already corrupt data. Define backup size limits and recovery behavior before writing.
4. Implement replace import with a concrete preview and backup first. Implement merge only when overlapping records can be reconciled without double counting: identical IDs/content are idempotent; conflicting IDs require an explicit resolution. Opaque lifetime aggregates cannot be safely added together when overlap is unknown. Restrict such imports to restore/replace or archive-only, and explain why.
5. Add immediate Undo for run deletion using a per-profile deleted-record snapshot and a defined expiry, such as 30 seconds. Preserve exclusion state and PB/aggregate contributions. Existing Include in averages already reverses exclusion. Keep reset recovery tied to the backup, not the short-lived Undo buffer.
6. Re-check profile and data revision before committing asynchronous import/reset work. If data changes after preview, revalidate instead of overwriting a new task or newer history.

Acceptance: export/reset/import restores history and equivalent estimates under the same settings; full backups additionally restore compatible checkpoint data through the recovery path. A regular import cannot replace the active assignment. Repeated merge is idempotent, conflicts cannot double totals, deletion/Undo restores the prior result, and malformed/oversized/future data causes no mutation. Test migration and restore failures and profile changes during dialogs.

### P5 — Define and implement timing behavior

Touch `ActiveTimeTracker`, `TaskTracker`, `ActiveTask`, `TaskRun`, schema migration, plugin lifecycle handlers and the estimate service. Begin with a timing-policy design/test change before implementing the UI control.

1. Add explicit states: tracking, waiting for activity, manually paused and disconnected/suspended. Preserve assignment tracking and remaining counts independently of the rate timer.
2. First expose the current capped-gap semantics honestly. Do not automatically discard banking time or treat a long boss kill as idle solely because no kill/XP event arrived. Automatic inactivity detection is a separately validated policy; it is not implied by a status label.
3. Define manual pause as “exclude this break until Resume or new confirmed task activity.” A confirmed activity can auto-resume with a visible explanation so continued combat cannot produce kills against a permanently frozen denominator. A manual Resume starts a new timing segment.
4. Use matched numerator/denominator samples for ETA and rate metrics. A first activity event after an unobserved gap anchors a segment; preserve its absolute task/kill/XP totals, but do not attribute untimed progress to a zero-duration rate segment. Group same-tick evidence so one kill cannot become two anchors or double-counted progress. Handle cannon/superior/completion evidence consistently. This also requires checking the existing first-activity timing bias.
5. Define which segment-derived duration contributes to whole-task averages and PBs. Runs with unobserved combat intervals remain useful history but must not earn full-observation PB/duration status by accident.
6. Persist timing-policy version and any necessary segment counters in the next schema version, with P4 backup and explicit migration steps. Keep aggregate buckets separate by timing policy from this release onward; adding new-policy samples to the old undifferentiated totals would lose the separation before P6 can use it. Preserve existing lifetime aggregates as the legacy bucket, including contributions from pruned runs. Tag retained old runs as legacy so exclusion/deletion adjusts the correct bucket. Existing records must not be recomputed as though they contain event intervals that were never stored. For an active pre-upgrade checkpoint, finish under its old policy or conservatively mark/rebaseline it; do not silently mix policies within one completed sample.
7. While paused, hide the projected clock finish or label it “If resumed now.” Rebaseline correctly across normal logout/login, hop, disconnect, plugin restart and profile switch. Validate every lifecycle path; the current event switch alone is not proof that every logout path is covered.

Acceptance: taking a break does not inflate active rate on return, progress cannot increase indefinitely against frozen timing, legitimate long kills remain accounted for, and the same event sequence yields the same sample. Compare the chosen time definition with live play. Keep a legacy fallback for legacy samples until new-policy history is sufficient, with provenance visible. Do not promise precise wall-clock finish time.

### P6 — Make estimates adapt to recent play

Touch the estimate service, raw-run query logic, configuration and calculator/repository tests. Preserve the policy-separated lifetime totals from P5; changing the default estimate window is a separate decision.

1. Add opt-in candidate windows such as recent 5, 10 and 25 eligible completed samples. Order by completion timestamp with a stable ID tie-breaker; filter excluded/incomplete/replaced runs, invalid/zero-duration samples, encounter, applicable location scope and timing policy before selecting N.
2. Completed partial observations remain eligible for rates when they contain valid matched samples. Full-task average duration and PBs retain stricter eligibility. Report both counts accurately.
3. For each compatible cohort compute `rate = sum(eligible task progress) / sum(eligible active hours)`. Continue using effective task progress for ETA. Do not average raw assignment durations across different task sizes or substitute physical kills.
4. Preserve the current live-blending threshold initially, using only compatible live samples from the selected recording encounter. Include that contribution once. Revisit live weighting only in a separate measured change.
5. Select recent N globally after location grouping, not N per location followed by a merge. Before sufficient compatible samples exist, use a labeled legacy-history fallback or live-only estimate; do not silently pool timing policies or unrelated encounters. An explicit legacy lifetime mode may remain available and must disclose that some underlying runs are no longer individually retained.
6. Reconcile estimation window with `maximumRecentRuns`: existing retention can be as low as 5. Preserve the user's setting and show “Requested 25; 5 eligible samples available.” Do not silently increase storage, claim missing samples exist, or claim every lifetime contribution can be reviewed. A separate unlimited archive is out of scope; retained history stays bounded and can be exported before pruning.
7. Apply the selected scope to cannon estimates too, using only cannon-using eligible samples and task-progress units for consumption estimates. Show their separate sample count when materially different; keep cannon history from non-cannon tasks out of the denominator.
8. Keep descriptive sample/source labels. Add a typical range only after a separate evaluation establishes its meaning and coverage; Mortimer's existing amount range is not a prediction interval.

Acceptance: a result labeled Recent contains no excluded or pruned contribution. If the service falls back to legacy lifetime data, its source explicitly changes to Legacy lifetime, rather than claiming to remain a recent estimate. Location/profile filtering, ties, partial samples, mixed timing policies, sparse history and current-run blending are deterministic. Switching windows changes neither raw history nor lifetime totals. Inspecting recent samples identifies every contribution. Promote a default only through P9.

### P7 — Make completion results and history worth returning to

Touch completion result formatting/model, panel/history components and tests.

1. Retain a structured last-completed result in the current profile's session, independent of whether a new assignment is active. Show it until dismissed or replaced; do not leak it across profiles. Respect the existing summary preference.
2. Show whether the result was saved, its observation coverage, comparable pace and an action to review/exclude it. If persistence is blocked, say “Not saved” instead of claiming the next estimate learned from it.
3. Add local search and a current-assignment filter, retaining expanded state where possible. Default sorting stays recently played; offer task name and typical time where comparable data exists. Keep rendering bounded and avoid rescanning/rebuilding all history every tick.
4. Keep existing PB logic available, clearly described as eligible task-progress rate. Distinguish “First recorded result” from beating an earlier PB. New trend claims compare compatible methods/policies and use rate or normalized duration, not raw task length.
5. Offer user-initiated copy of summary text. No automatic sharing or repetitive celebration prompts.

Acceptance: accepting another task does not erase the accessible last result; profile switching clears it; search/filter/deletion behave together; excluded/partial/legacy data cannot create misleading comparisons.

### P8 — Reuse the estimate for planning

Extend the read-only comparison view from P2 with assignment, positive remaining amount and encounter/location scope drawn from retained personal history. Show unavailable state when history is missing; do not manufacture generic rates or borrow another encounter's data.

Use the same estimate service for panel preview and Mortimer. Preserve Mortimer's current location-combined policy only with an explicit scope label; do not silently claim it obeys exact-location scope when the offer lacks that information. Clearly distinguish an assignment-size range from uncertainty in pace. Keep the integration experimental/default-off until its parsing and live interface checks pass.

Acceptance: identical amount/scope/history produce identical estimates across surfaces. Preview changes no tracking state. Unsupported Mortimer layouts fail without a misleading overlay. General task-time preview remains useful without enabling Mortimer.

## 5. Verification and release gates

### Automated and manual coverage

| Area | Required checks |
| --- | --- |
| First use | No task, detected task, no timed sample, early/live/history/blended ETA, partial installation, no matching encounter |
| Tracking | Normal kills, both bracelets, cannon multi-kills, superior XP, completion bonus, long boss kills, repeated same-task assignment with and without completion evidence |
| Timing | First event and resumed segment boundaries, break with/without activity, normal logout, hop, disconnect, restart and checkpoint recovery |
| History | Valid old schemas, corrupted/future schema, pruned totals, import overlap/conflicts, duplicate IDs, reset/restore, Undo, wrong-profile prevention |
| Estimation | Positive finite input, sample eligibility, recent-window ordering, location grouping, mixed-policy fallback, consistent source text and cannon denominators |
| UI | Narrow panel at multiple scales, long names, keyboard access, settings refresh, context menus, last result while another task is active |

Extend existing tests where possible; add behavioral tests at the service and event boundaries. Do not add tests merely duplicating static copy or asserting a screenshot file exists. Render checks need visual inspection as well as assertions about critical labels and visibility. Run the relevant tests per work item and the full suite before release.

Mandatory release gates: no known data overwrite path after failed load, no confirmed duplicate/merged runs in exercised cases, no profile leakage, all relevant tests pass, and real client smoke checks pass. A discovered tracking defect gets a reproducer and targeted fix before shipping affected behavior. Broad code-size targets, blanket coverage percentages and a complete event-engine rewrite are not goals of this UX release.

### P9 — Validate usefulness and default changes

Use a small consenting beta group; no telemetry is required. Proposed pilot targets below are decision aids, not existing performance claims.

- **Discovery/comprehension:** at least four of five unfamiliar readers can explain the benefit and installation path from the revised README/listing. Revise copy when they mistake it for a generic XP tracker.
- **Activation:** observe five first-use sessions. Users should identify what the first estimate is based on without being told they must finish a whole task. Log time/activity until first usable ETA, distinguishing slow boss tasks from fast kills.
- **Accuracy:** collect timestamped snapshots at approximately 25%, 50% and 75% progress on at least 20 fully observed representative tasks, with melee, cannon, burst and boss coverage. More samples are needed if results differ materially by method. Store this as voluntary diagnostic/beta data, not automatic production telemetry.
- **Fair comparison:** compare candidate estimators on the same tasks using only history available before each prediction. Never evaluate a task against its own final rate or future completed runs. Final task records alone cannot reconstruct interim ETA errors; capture the snapshots prospectively.
- **Metrics:** report median and upper-tail absolute error in minutes plus percentage error when actual remaining duration is meaningfully nonzero. Score against the declared active-time policy; report wall-clock error separately. Do not conflate a changed definition of active time with improved predictions.
- **Default decision:** keep the existing default if a recent window has no consistent improvement or produces unexplained subgroup regressions. Publish the pilot result and limits; do not lock in recent 10 or a 15% error promise before evidence exists.
- **Retention:** ask the same beta users after one and two weeks whether the plugin is still enabled and useful, and why they disabled it if applicable. Treat small-group results as qualitative evidence. Public installation metrics, where their definitions are verified, indicate reach and cannot establish individual retention or causality.

### Release and rollback

Record the version, source revision, resolved dependency version, test result and migration policy for each release. Capture screenshots after the behavior is final. Review current Plugin Hub requirements when preparing its commit update. Publishing or community outreach is a later release action, not part of writing this plan.

For P1–P3, rollback should not require a data migration. P4 must deliver tested recovery before P5 changes a schema. Before a schema release, document whether a downgrade is supported; never assume old code can read new data. Preserve the newer payload and restore a compatible backup through an explicit recovery procedure. Test feature/default reversal and backup restoration before promotion.

## 6. Alignment check

| Review recommendation | Covered by | Scope check |
| --- | --- | --- |
| First-task guidance | P1 | Faster understanding of the existing ETA |
| Clearer listing and screenshots | P3 | Discovery of the same plugin |
| Calmer default UI | P2 | Less distraction while using the ETA |
| Explain time and pauses | P1, P5 | More honest and dependable estimates |
| Recent-performance adaptation | P6, P9 | Estimates reflect current play |
| Safe encounter comparison | P2 | Protects accuracy and recording intent |
| History protection | P0, P4 | Preserves personal data value |
| Useful completion/history | P7 | Makes existing results easier to use |
| Personal task-time preview | P8 | Same estimator before combat; optional later work |

The plan is aligned with all four goals and the review's nine recommendations. Correctness-sensitive work is gated on executable tests and live evidence. Growth effects remain hypotheses; the plan does not claim a measured download or retention increase.

## 7. Verification record

- Re-read source for first-task fallback, panel text, partial-run aggregation, encounter persistence, timing, lifecycle tests and schema migration.
- Rechecked the official [Plugin Hub guide](https://github.com/runelite/plugin-hub/blob/master/README.md). Compared scoped UI patterns with [XP Tracker](https://github.com/runelite/runelite/wiki/XP-Tracker) and [Inventory Setups](https://github.com/dillydill123/inventory-setups); their features do not establish causal retention evidence.
- Fresh baseline: `.\gradlew.bat test --rerun-tasks --offline --console=plain` passed, with all five Gradle tasks executed. JUnit XML reports 77 tests across 17 suites, zero failures, zero errors and zero skipped tests. The earlier ordinary test command was UP-TO-DATE and is not the basis of this claim. The build emitted an existing unchecked-operation note in the development test launcher.
- Dependency verification resolved net.runelite:client:latest.release to 1.12.38; build.gradle targets Java 11. This is the tested dependency, not a guarantee about future latest.release resolution.
- No gameplay session or user beta was performed during planning. Existing Swing fixtures are not live-client verification.
