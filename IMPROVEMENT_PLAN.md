# Slayer Task Speed Improvement Plan

> Current-state review: 2 September 2026. This roadmap supersedes the delivery
> phases in `PLUGIN_PLAN.md` and `IMPLEMENTATION_PLAN.md`, which remain useful as
> historical design notes.

## Objective

Make Slayer Task Speed a trustworthy, low-maintenance RuneLite Plugin Hub plugin:

- Task ETAs remain correct across event-ordering and lifecycle edge cases.
- Players can understand, inspect, and recover the data behind an estimate.
- The plugin stays responsive as history grows.
- RuneLite updates are caught by automation before they reach users.

The first release from this plan should improve confidence in the existing product
before adding more tracked supplies, gear profiles, or charts.

## Verified baseline

- The project builds against RuneLite `latest.release` on Java 11.
- All 77 tests pass from a clean build.
- The plugin is active in the Plugin Hub and uses its standard build mode.
- Runtime data stays in RuneLite's account-specific profile configuration and the
  plugin has no network integration or third-party runtime dependency.
- The core separation between literal kills, effective task progress, Slayer XP,
  and cannonballs is sound.

The main risks found during review are:

1. **Tracking accuracy is hard to verify.** The most important behavior depends on
   several events arriving within fixed two- and four-tick windows, but there is no
   replayable event trace or opt-in diagnostic report.
2. **Task lifecycle identification is too coarse.** A session is identified by task
   name and location. Consecutive assignments with the same name can be merged if
   completion evidence is missed, and location changes can look like replacement.
3. **Persistence fails closed without recovery.** Invalid or unsupported JSON is
   ignored, and there is no backup/export/import path or validation of deserialized
   values before the data reaches calculations and the UI.
4. **Averages become permanently historical.** Aggregate totals include runs that
   have aged out of the retained recent-run list. Old play styles can dominate an
   estimate, while those old samples can no longer be reviewed or excluded.
5. **The orchestration and UI are expensive to change.** `SlayerSpeedPlugin` is 870
   lines and `SlayerSpeedPanel` is 1,024 lines. The full view model and merged
   history are regenerated every game tick, followed by an EDT update.
6. **Release assurance is local only.** There is no repository CI, coverage report,
   static analysis, manual beta worksheet, changelog, or issue template.
7. **Mortimer support is intentionally brittle.** It relies on one compatibility
   widget ID and layout/text heuristics, so it needs an explicit fallback and test
   fixtures whenever the interface changes.

## Product and engineering principles

- Effective task progress remains the authoritative ETA signal.
- Literal kills and XP fail conservatively when evidence is ambiguous.
- No telemetry or remote service is required. Diagnostics are opt-in and copied or
  exported by the player.
- Persist raw completed runs needed by the estimator; derive aggregates from those
  runs where possible.
- Keep `build=standard` and avoid new runtime dependencies unless they create clear
  user value and pass Plugin Hub review.
- Every production tracking fix starts with a failing replay or unit test.

## Phase 1 — Make behavior observable and reproducible (P0)

**Goal:** turn field reports into deterministic regression tests.

### Work

- Introduce a small, bounded `TrackingDiagnosticBuffer` containing sanitized event
  type, client tick, task snapshot, candidate identity token, and attribution
  decision. Do not record player names, chat contents, loot contents, or coordinates.
- Add an advanced, default-off **Tracking diagnostics** option.
- Add **Copy diagnostic report** beside **View stored stats**. Include plugin version,
  RuneLite version when available, configuration values relevant to tracking, and
  the latest bounded trace.
- Define a compact replay fixture format and a `TrackingReplayTest` harness that
  feeds task snapshots, deaths, XP changes, loot evidence, cannon changes, logout,
  and completion signals into the tracking services in a specified order.
- Create initial fixtures for normal kills, both Slayer bracelets, cannon multi-kills,
  superior XP, boss tasks, empty drops, world hops, reconnects, plugin restart, task
  cancellation, and consecutive same-name assignments.

### Exit criteria

- A player can provide one diagnostic report without exposing account identity.
- A reported ordering defect can be reproduced without launching the game client.
- Diagnostics add no work when disabled and are capped in memory when enabled.

## Phase 2 — Harden tracking and lifecycle correctness (P0)

**Goal:** prevent silent corruption of task runs and their estimates.

### Work

- Extract a deterministic `TaskSessionEngine` from the plugin class. Give it an
  injected clock/tick source and typed signals instead of direct `System.currentTimeMillis()`
  calls spread across subscribers.
- Represent lifecycle states explicitly: `NO_TASK`, `ACTIVE`, `COMPLETION_PENDING`,
  `PAUSED`, and `ENDING`. Document legal transitions.
- Detect assignment boundaries using name, location, initial amount, remaining-count
  resets, completion messages, and a short stability window. Cover a new assignment
  with the same task name and location.
- Treat temporary null/empty Slayer service snapshots as uncertain until the grace
  period expires. Preserve the final progress, XP, and cannon events during that
  period.
- Replace “consume one pending XP event” behavior with deterministic matching of all
  eligible XP/candidate signals in a tick window. Keep task progress independent so
  ambiguous literal kills never damage ETA.
- Define explicit policies for multi-combat, cannon, thralls, shared kills, empty
  drops, superior monsters, and completion-bonus XP. Encode each policy as a replay.
- Move encounter mappings and Mortimer task aliases into reviewed, data-driven tables
  with validation for duplicate NPC/task aliases.
- Re-check the Mortimer widget constant on each RuneLite update; hide the overlay and
  emit one diagnostic reason when the interface cannot be parsed.

### Exit criteria

- The replay matrix passes for every documented lifecycle and attribution policy.
- No tested event ordering duplicates a run, merges consecutive runs, or loses the
  final effective-progress update.
- Unsupported literal-kill cases undercount with an explanation; they never overcount.

## Phase 3 — Make history safe, portable, and statistically useful (P0/P1)

**Goal:** protect the player's data and make estimates adapt to current play.

### Work

- Add strict post-deserialization validation: schema, required strings, finite and
  non-negative counters, timestamp ordering, unique run IDs, valid status values,
  and aggregate/run consistency.
- Before a schema migration, retain the previous payload under a bounded backup key.
  If loading fails, keep that payload available and show a non-blocking recovery
  message rather than silently replacing it on the next checkpoint.
- Add versioned **Export history** and **Import history** actions with a preview:
  profile count, run count, schema version, conflicts, and whether the import merges
  or replaces. Replacement requires confirmation.
- Measure serialized payload size and enforce both per-profile and global retention
  limits. Surface pruning in the UI instead of doing it invisibly.
- Make retained runs the source of truth for new statistics, or retain sufficient
  bucketed summaries to recalculate them. Ensure every sample affecting an estimate
  can still be excluded or aged out.
- Offer estimate windows such as **recent 10**, **recent 25**, and **all**, defaulting
  to a documented recent window after comparison against beta data.
- Replace sample-count-only confidence with a stability measure that includes sample
  size and variation. Show a typical ETA plus an expected range when enough complete
  runs exist. Prefer robust statistics such as median and interquartile range for
  run duration; keep pooled effective KPH available for comparison.
- Migrate schema version 5 data with round-trip, rollback, oversized-history, null
  collection, duplicate-ID, and corrupted-payload tests.

### Exit criteria

- Export → reset → import reproduces the same estimates and visible history.
- A failed migration cannot destroy the last readable payload.
- Every displayed confidence label and ETA range has a tested mathematical definition.
- Recent performance can influence estimates without deleting the full archive.

## Phase 4 — Simplify architecture and UI updates (P1)

**Goal:** reduce regression risk and game/EDT work while preserving behavior.

### Work

- Keep `SlayerSpeedPlugin` focused on lifecycle and event subscriptions. Extract:
  `SlayerSpeedPresenter`, `EncounterSelectionService`, `CompletionSummaryFormatter`,
  and `CannonInventoryReader`.
- Split `SlayerSpeedPanel` into current-task, averages, cannon, history, and data
  management components with small view models.
- Replace the long `SlayerSpeedViewModel` constructor with immutable nested models or
  a builder so fields cannot be swapped accidentally.
- Refresh derived history only when task data or relevant configuration changes.
  Coalesce pending EDT updates and update lightweight current metrics at a bounded
  cadence.
- Add a `ConfigChanged` subscriber so display/history changes render immediately
  without relying on the next game tick.
- Introduce named duration/tick policies for checkpointing and correlation windows;
  test boundaries without sleeping.
- Enable compiler linting, Checkstyle using RuneLite conventions, and JaCoCo reporting.
  Set an initial coverage floor around the deterministic domain and tracking packages,
  not Swing rendering boilerplate.

### Exit criteria

- Neither the plugin coordinator nor any panel component exceeds an agreed reviewable
  size (target: roughly 350 lines).
- One game tick schedules at most one EDT update, and unchanged history is not merged
  or rebuilt.
- Existing snapshots/render tests pass with no behavior loss.

## Phase 5 — Improve the player experience (P1)

**Goal:** make the estimate understandable at a glance and easier to correct.

### Work

- Add a first-run explanation of **ETA**, **task units/hr**, and why it may differ from
  literal kills/hr. Keep detailed metrics behind the existing detail control.
- Show estimate provenance inline: profile, location scope, history window, completed
  runs, and whether the current run is blended in.
- Replace generic confidence text with actionable states such as “Learning: 1 more
  full run” or “Wide range: recent runs vary substantially.”
- Add an immediate **Undo** for run exclusion/deletion where practical, and make the
  backup/export path visible before **Reset all**.
- Mark incomplete/replaced runs with the reason and allow the player to promote a
  genuinely complete misclassified run after reviewing its ending amount.
- Add accessible names/tooltips, keyboard navigation, high-DPI checks, and tests for
  the narrow RuneLite sidebar at 100%, 150%, and 200% scaling.
- Add two README screenshots, a concise troubleshooting section, a data-recovery
  section, and a table explaining defaults.

### Exit criteria

- A new user can explain which rate drives ETA after reading the panel once.
- Every estimate exposes its sample scope and uncertainty.
- Destructive data actions are recoverable during the documented recovery window.

## Phase 6 — Automate release confidence (P0/P1)

**Goal:** detect RuneLite and persistence regressions before publishing a Plugin Hub
commit.

### Work

- Add GitHub Actions for Java 11 that runs `clean test`, lint, and the replay suite on
  every pull request and on a scheduled build against RuneLite `latest.release`.
- Cache Gradle safely and upload test/coverage reports on failure. Keep the Gradle
  wrapper checksum validation already present.
- Add a manual beta worksheet covering the scenarios in Phase 2. For each run compare
  actual remaining count, stopwatch duration, XP tracker, physical kills, cannonballs,
  and predicted versus actual finish time.
- Record aggregate beta accuracy: median absolute ETA error at 75%, 50%, and 25%
  remaining; run-classification error rate; literal-kill error rate; and crash count.
- Add `CHANGELOG.md`, bug-report and feature-request templates, and a release checklist
  that includes clean build, migration round trip, screenshots, Plugin Hub checks,
  and rollback instructions.
- Keep metadata and the in-code `PluginDescriptor` description/tags synchronized with
  an automated test.

### Exit criteria

- Pull requests cannot merge with a build, replay, migration, lint, or metadata failure.
- The scheduled compatibility build provides early notice of RuneLite API changes.
- A release has a completed beta worksheet and meets the quality gates below.

## Quality gates for the first improved release

- 100% pass rate for unit, replay, migration, and panel smoke tests.
- Zero known duplicate or merged task runs in the beta matrix.
- Zero unrecoverable history loss in corruption and migration tests.
- No overcount in conservative literal-kill fixtures.
- Median absolute ETA error at 50% remaining is measured and documented; use a target
  of 15% after at least 20 representative completed beta tasks, then revise the target
  using observed variance.
- No new runtime dependencies and no network access.
- Plugin Hub standard-build verification passes.

## Recommended delivery slices

1. **1.1 Diagnostics and replay harness** — Phase 1 only; no estimation changes.
2. **1.2 Lifecycle and attribution hardening** — Phase 2 plus regression fixtures.
3. **1.3 Safe history** — validation, backup, export/import, and schema migration.
4. **1.4 Maintainability and CI** — coordinator/panel extraction and automation.
5. **1.5 Better estimates and UX** — recent windows, ETA ranges, provenance, and
   accessibility.
6. **1.6 Mortimer hardening** — promote from experimental only after its beta matrix
   and graceful-fallback checks pass.

Each slice should be independently releasable. Do not combine persistence migration,
tracking-policy changes, and a major UI rewrite in one Plugin Hub update.

## Deferred ideas

Revisit these after the quality gates are met:

- Gear/loadout or combat-method profiles.
- Supply tracking beyond cannonballs.
- Trend charts and personal records.
- Full-task ETA including travel/setup time.
- Optional synchronization. This would change the current privacy model and require a
  separate product decision and Plugin Hub security review.

## Reference material

- [RuneLite Plugin Hub contribution and build requirements](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [RuneLite developer guide](https://github.com/runelite/runelite/wiki/Developer-Guide)
- [Official example plugin](https://github.com/runelite/example-plugin)

