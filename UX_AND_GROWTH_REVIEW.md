# Slayer Task Speed: UX, discovery and retention review

Reviewed 5 September 2026. Based on the local source, README, metadata, existing improvement plan, and saved Swing panel renders from 2 September. This is a product assessment, not a live gameplay test or an analysis of measured uninstall behavior. No plugin code was changed. Expected effects on downloads and retention are hypotheses to validate.

The product should remain a personal Slayer task time estimator. Its strongest promise is: **Know roughly when your Slayer task will finish, based on how you actually play.** KPH, XP and cannonball statistics support that promise.

## What is already worth keeping

- Automatic tracking, personal history and an in-game ETA.
- Separate literal kills and task-counter progress, so bracelets do not undermine the ETA model.
- Encounter-specific estimates, including regular monsters versus bosses.
- Simple/detailed modes, contextual cannon information and collapsible run history.
- Completion summaries and personal bests: these already exist.
- Account-specific storage without a plugin-operated server.

The opportunity is to make these benefits easier to discover and trust. Adding many more metrics would dilute the main reason to install.

## Prioritized opportunities

| Priority | Enhancement | Main benefit | Relative effort |
| --- | --- | --- | --- |
| 1 | Correct first-task onboarding and learning states | Users see value during their first session | Small–medium |
| 2 | Lead the listing and README with the ETA benefit and screenshots | Clearer reason to install | Small |
| 3 | Make the default panel and overlay calmer | Lower ongoing screen clutter | Small–medium |
| 4 | Clarify active time, pauses and finish-time assumptions | More credible everyday estimates | Medium |
| 5 | Use recent performance and explain estimate provenance | Estimates stay useful as the player improves | Medium–large |
| 6 | Separate encounter preview from recording overrides | Avoid accidental history contamination | Medium |
| 7 | Add history backup, recovery and reversible deletion | Protect accumulated personal value | Medium–large |
| 8 | Make completion summaries and history more useful | Give users reasons to return | Medium |
| 9 | Add a small personal task-time preview | Extend the same use case to planning | Medium |

Effort includes meaningful verification; these are relative sizes, not delivery commitments.

## 1. Fix the first-task experience

**Verified problem:** `SlayerSpeedPanel.createHistoryOnboarding()` says “Finish one task to unlock estimates.” However, `SlayerSpeedPlugin.createViewModel()` falls back to a current-task rate when no historical rate exists. A live ETA can appear before the configured 10-unit blending threshold. The saved simple-panel fixture visibly shows both an ETA and the claim that estimates are still locked.

There is a second issue: the plugin builds a learning-progress message, but the panel replaces it with generic completion guidance whenever historical data is absent. The configuration's learning-progress setting therefore does not produce the intended explanation there.

Recommended behavior:

- No task: “Get a Slayer task. Tracking starts automatically.”
- Task detected, no usable timing: “Tracking started — waiting for enough activity to estimate.”
- Early live rate: “Early estimate — based on this task.”
- Enough history: “Based on your recent tasks,” with the sample scope available.
- Completed: explain that this run will inform future estimates for this task and encounter.

Keep an early ETA available, but label it provisional. If adding a warm-up threshold, consider both elapsed time and progress; a fixed kill count alone behaves very differently for bosses and fast multi-kills. Any progress indicator must match the actual calculation policy.

**Acceptance:** a first-time user sees honest state transitions during the first task, with no simultaneous “locked” and available estimate messages. Include a mid-task installation case.

## 2. Make the installation decision easy

The metadata description currently leads with KPH, XP and supplies, placing completion time at the end. The README has no screenshots or player installation steps, although it does explain the mechanics and developer setup.

Suggested description:

> Estimate when your Slayer task will finish using your own pace, with personal task history, XP rates and cannonball estimates.

Suggested README opening:

> **How long will this Slayer task take?**
>
> Slayer Task Speed estimates your remaining task time from your own pace and completed tasks. See a live ETA while you play, then build personal estimates for future assignments.

Follow with an authentic in-game screenshot, three benefits, and brief Plugin Hub installation steps using the exact display name. Keep the name “Slayer Task Speed”; test clearer copy before considering a rename.

Prepare three visuals: the compact ETA, a learned estimate with its source, and the completion/history view. A short demonstration should show a task progressing and the estimate responding. Use live captures for promotion; the existing renders use synthetic fixture data and are useful for layout review only.

Make the active-time limitation visible near the promise. Describe storage accurately as RuneLite account-profile storage with no plugin-operated server; avoid suggesting RuneLite itself cannot synchronize configuration.

Keep relevant search terms, but do not assume adding more tags improves ranking. The current tags already cover many useful queries. Once the improved release is available, a single focused community demonstration and a concise changelog are reasonable promotion experiments.

## 3. Reduce default visual noise

Simple mode already exists, but the overlay can still show remaining count, ETA, finish time, physical KPH, task-unit rate, XP rate, pace and cannon statistics. The sidebar repeats several of these, keeps “Superior XP included” visible, and places raw stored-stat controls in the main journey.

Use a compact default focused on task, remaining count and **about X minutes remaining**. Offer finish time as an option. Put pace, XP and the full explanation of task-counter rates under details. Keep cannon information contextual, and rename “Est. balls left” to “Cannonballs needed” to avoid confusion with inventory stock.

For the sidebar, emphasize ETA visually, use a small progress indicator, collapse secondary sections, and group raw records under “Help & data.” Explain once that bracelet effects make task progress differ from physical kills. Show detection failures or unavailable estimates in plain language instead of an unexplained dash.

Test the narrow sidebar and high-DPI layouts with long assignment/location names. The reviewed images are existing 242-pixel Swing fixtures, not evidence of live client appearance at all scales.

## 4. Make the clock trustworthy

`ActiveTimeTracker.recordActivity()` adds the smaller of the gap between activity events and the idle timeout. With the default five-minute cap, a ten-minute gap can add five minutes at the next event unless another lifecycle event paused the tracker. “Active time” therefore does not mean exclusively time attacking.

The finish clock is calculated as the current time plus the active-time ETA. It does not independently forecast future travel, banking or breaks.

Add visible states such as Tracking, Waiting for activity and Paused, with a manual pause/resume control. State “At your current pace” next to the finish-time estimate. Specify the treatment of banking, long kills, logout and world hops before changing the time model; blindly shortening the timeout could undercount legitimate boss fights.

Validate representative event sequences and live tasks before promising more accurate wall-clock completion. Preserve the core active-task estimator for the first release.

## 5. Let the estimate improve with the player

The current ETA pools historical task units and active time, then adds the current sample when eligible. Old runs remain in aggregate totals even after their individual records are pruned. Consequently, an established history can outweigh a faster current method and cannot all be inspected or excluded.

Introduce a recent completed-run estimate window, with the archive kept separately. Compare candidate windows on recorded tasks before choosing a default; “recent 10” is a candidate, not an established optimum. Preserve old aggregate-only data separately rather than pretending its missing individual samples can be reconstructed.

Show compact provenance such as “Recent 10 Araxyte tasks + this run.” The existing confidence labels use task count and progress only; five inconsistent tasks can receive High. Initially rename this to “History sample” and expose sample counts. Later add an empirically checked stability indicator or typical range; do not label an arbitrary spread a statistical confidence interval.

Evaluate forecasts at consistent points in a task and compare predicted remaining active time with actual remaining active time. This avoids claiming failure simply because someone took an unrelated break.

## 6. Separate comparing an encounter from recording one

The selector tooltip mentions previewing, but `selectEncounterProfile()` writes a manual profile into the active task and checkpoints it. `ActiveTask` uses that override when resolving the profile saved for the run.

Keep automatic detection as the normal behavior. Add a separate “Compare estimates” view that does not modify recording. If a player deliberately overrides recording, label it “Record this task as…” and keep the override visible, with a clear return to Auto.

**Acceptance:** browsing a boss estimate cannot change the encounter under which a regular-monster run is saved.

## 7. Protect the history users invest in

Exclusion and deletion already exist, and exclusion can be reversed through Include in averages. Reset uses confirmation. However, viewing/copying raw statistics is not an import/export workflow, and reset explicitly cannot be undone. A failed history load starts from empty in memory without a recovery message in the panel.

Add versioned export/import, a backup before migrations or destructive resets, and an actionable recovery state. Preserve unreadable data rather than overwriting it at the next save. Add immediate Undo for deletion where practical. An import preview should explain merge behavior and duplicates.

This protects the plugin's accumulated usefulness. It should not require accounts, a hosted service or a new cloud feature.

## 8. Improve the existing completion and history loop

Completion summaries and PB detection are already implemented. The summary is supplied to the no-task view and is absent from active-task views, so immediately taking another assignment removes it from that view.

Keep the last completed result accessible until dismissed or replaced by a newer result. Explain whether it was saved, show its duration and pace, and make correction/exclusion easy. Add optional copyable summary text for voluntary sharing.

Add history search, a current-assignment filter and sorting by last played or typical duration. Highlight comparable recent pace changes. Avoid celebrating shorter raw duration as an improvement when assignment sizes differ. Keep PBs optional and explain that the current comparison is based on task-progress rate.

## 9. Add a small planning view after the fundamentals

Let players choose a previously observed assignment, amount and encounter to preview personal task time and cannonball need. This answers the same question before combat and gives history value between assignments.

Mortimer estimates already exist experimentally; do not count them as a new feature. Harden that integration and clarify its source/range rather than making it the main installation promise. A generic sidebar preview is less dependent on one task-choice interface.

Defer broad gear optimization, profit dashboards, remote leaderboards and expanded supply tracking. Simple optional method labels may be useful later if recent windows and existing encounter separation still mix materially different play styles.

## Patterns worth borrowing

- **XP Tracker:** selectable information and inactivity/logout pause controls provide familiar expectations for rate displays. Apply that familiarity to Slayer ETA states and an optional compact overlay. [Official XP Tracker documentation](https://github.com/runelite/runelite/wiki/XP-Tracker).
- **Inventory Setups:** compact views, search, favorites and import/export make accumulated personal data manageable. Apply a small subset to task history. [Maintainer documentation](https://github.com/dillydill123/inventory-setups).
- **Plugin Hub:** use its documented packaging/presentation workflow when preparing metadata and screenshots for release. [Official contribution guide](https://github.com/runelite/plugin-hub/blob/master/README.md).

These are verified feature patterns, not evidence that copying them causes higher retention. No competitor retention or download ranking was established in this review.

## Suggested delivery and validation

**First release:** correct onboarding and learning text, expose estimate source, simplify the default display, clarify preview/override behavior, and update the listing/README with real captures. Ship concrete correctness fixes as they are verified. These improvements need not wait for the entire architectural refactor in `IMPROVEMENT_PLAN.md`.

**Next release:** pause/time semantics, recovery and export, recent-sample estimation, and tests for the relevant tracking/persistence changes. Avoid combining all data migration and estimator changes in one release.

**Later:** searchable history, improved completion comparisons, personal task-time preview and better-tested Mortimer support.

Recruit a small voluntary beta group across melee, cannon, burst and boss tasks. Observe whether they understand the first estimate, can explain its source and pause behavior, and still choose to use it after a week. Record ETA error at consistent task-progress checkpoints and ask about reasons for disabling it. Public installation counts can indicate reach if their definitions are known; they cannot establish individual retention or prove which change caused growth. No telemetry is required for this initial validation.

## Source map

All paths are relative to this repository.

- `SlayerSpeedPlugin.java:619–640, 682–701`: current-rate fallback and generated learning states.
- `ui/SlayerSpeedPanel.java:294–304, 436–462`: overwritten learning explanation and onboarding claim.
- `ui/SlayerSpeedOverlay.java:38–103`: simple-mode metric selection.
- `tracking/ActiveTimeTracker.java:7–27`; `SlayerSpeedPlugin.java:791–799`: timing policy and finish clock.
- `model/TaskStatistics.java:53–72`; `calculation/Confidence.java:24–40`: retained runs versus aggregates and confidence thresholds.
- `SlayerSpeedPlugin.java:399–416`; `model/ActiveTask.java:99–141`: persisted encounter override.
- `persistence/TaskHistoryRepository.java:35–75`; `ui/SlayerSpeedPanel.java:844–891`: load recovery and data controls.
- `SlayerSpeedPlugin.java:729–764`: active-view summary omission and existing completion/PB summary.
- `README.md`; `runelite-plugin.properties`: current installation proposition and metadata.

Java paths above are under `src/main/java/com/slayerspeed/`. The existing engineering roadmap remains unchanged; this review complements it with product and acquisition priorities.
