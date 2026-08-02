# SlayerSpeed Step-by-Step Implementation Plan

This plan converts the overall design in [PLUGIN_PLAN.md](./PLUGIN_PLAN.md) into an ordered development sequence. Complete and verify each step before moving to the next one.

## Target package and initial structure

Use a package such as `com.slayerspeed` and build toward this structure:

```text
src/main/java/com/slayerspeed/
|-- SlayerSpeedPlugin.java
|-- SlayerSpeedConfig.java
|-- tracking/
|   |-- TaskTracker.java
|   |-- ActiveTimeTracker.java
|   |-- KillAttributionService.java
|   `-- SlayerXpTracker.java
|-- calculation/
|   `-- KphCalculator.java
|-- persistence/
|   `-- TaskHistoryRepository.java
|-- model/
|   |-- ActiveTask.java
|   |-- CandidateDeath.java
|   |-- TaskRun.java
|   |-- TaskStatistics.java
|   |-- TaskKey.java
|   `-- TaskRunStatus.java
`-- ui/
    |-- SlayerSpeedPanel.java
    `-- SlayerSpeedOverlay.java

src/test/java/com/slayerspeed/
|-- tracking/
|-- calculation/
|-- persistence/
`-- model/
```

## Milestone 1: Scaffold the RuneLite plugin

### Step 1. Create the plugin project

1. Generate a repository from the official RuneLite Plugin Hub template.
2. Configure the project to use RuneLite `latest.release`.
3. Rename the example package and classes to `SlayerSpeedPlugin`, `SlayerSpeedConfig`, and `SlayerSpeedPluginTest`.
4. Set `pluginMainClass` to the fully qualified `SlayerSpeedPlugin` class.
5. Add `runelite-plugin.properties`:

```properties
displayName=SlayerSpeed
author=YOUR_NAME
description=Tracks Slayer task KPH, Slayer XP per hour, and estimated completion time
tags=slayer,kph,xp,task,eta,tracker
plugins=com.slayerspeed.SlayerSpeedPlugin
```

6. Add a BSD 2-Clause licence and a basic README.

### Step 2. Prove the development client works

1. Run the Gradle `test` task.
2. Run the development client.
3. Confirm SlayerSpeed appears in the plugin list.
4. Enable and disable it without exceptions.
5. Add a temporary startup log message and confirm it appears once.

### Milestone completion check

- The project compiles from a clean checkout.
- The development client launches.
- The empty plugin can be toggled safely.
- The initial test suite passes.

## Milestone 2: Build the domain model and calculations

### Step 3. Implement `TaskKey`

Store normalized task identity independently from display text.

Fields:

```text
taskName
taskLocation
```

Rules:

1. Trim whitespace.
2. Convert to a consistent case for equality and storage.
3. Treat a null or blank location as absent.
4. Keep the original display name separately in `TaskRun`.
5. Implement stable `equals` and `hashCode` behaviour.

Tests:

- Differently capitalized versions of the same task produce the same key.
- Blank and null locations are equivalent.
- The same task at two specified locations produces different keys.

### Step 4. Implement task records

Create `TaskRunStatus`:

```text
ACTIVE
COMPLETED
SKIPPED
CANCELLED
REPLACED
UNKNOWN_INCOMPLETE
```

Create `ActiveTask` with mutable in-progress state:

```text
id
taskKey
displayTaskName
displayLocation
initialAmount
lastRemainingAmount
actualKills
taskProgressUnits
killSlayerXp
bonusSlayerXp
activeSeconds
setupSeconds
startedAt
lastActivityAt
lastSavedAt
```

Create immutable or effectively immutable `TaskRun` for stored history.

Create `TaskStatistics` for aggregates and recent runs.

### Step 5. Implement `KphCalculator`

Add pure calculation methods:

```text
literalKph(actualKills, activeSeconds)
effectiveKph(progressUnits, activeSeconds)
slayerXpPerHour(slayerXp, activeSeconds)
xpPerKill(slayerXp, actualKills)
xpPerTaskUnit(slayerXp, progressUnits)
etaSeconds(remainingAmount, effectiveKph)
weightedAggregate(totalUnits, totalSeconds)
```

Rules:

- Return an empty result rather than infinity when time or sample count is zero.
- Perform calculations with `double`.
- Round only when formatting for the UI.
- Clamp negative input to invalid or reject it explicitly.
- Keep calculation code independent from RuneLite APIs.

Tests:

- Normal values.
- Zero time and zero observations.
- Partial hours.
- Multi-unit task progress.
- Weighted aggregation differs correctly from averaging individual rates.
- ETA formatting across seconds, minutes, and hours.

### Milestone completion check

- All domain and calculator tests pass without starting RuneLite.
- A sample run can produce all three rates and an ETA.

## Milestone 3: Add account-specific persistence

### Step 6. Implement the storage schema

Create a versioned root object:

```text
SlayerSpeedData
- schemaVersion
- statisticsByTaskKey
- checkpointedActiveTask
```

For each task key, store:

```text
totalActualKills
totalTaskProgressUnits
totalSlayerXp
totalCannonballsUsed
totalCannonRunActualKills
totalCannonRunTaskProgressUnits
totalActiveSeconds
completedTaskCount
recentRuns
lastUpdatedAt
```

Keep at most 50 recent runs per task by default.

### Step 7. Implement `TaskHistoryRepository`

Use `ConfigManager` and RS-profile configuration.

Required operations:

```text
load()
saveCompletedRun(TaskRun)
saveIncompleteRun(TaskRun)
saveCheckpoint(ActiveTask)
loadCheckpoint()
clearCheckpoint()
deleteTask(TaskKey)
deleteAll()
```

Implementation rules:

1. Serialize through RuneLite's existing Gson dependency.
2. Keep a schema version in the saved data.
3. Debounce routine checkpoints.
4. Save completed tasks immediately.
5. Exclude incomplete runs from aggregates while retaining them in recent history.
6. Recover with empty data when stored JSON is corrupt, while logging the problem.
7. Never mix data from different RuneScape profiles.

Tests:

- Empty configuration loads successfully.
- Save/load round trip retains every field.
- Completed runs update aggregates.
- Incomplete runs do not update aggregates.
- Run history is capped.
- Corrupt JSON does not crash the plugin.
- Older schema data is migrated or rejected safely.

### Milestone completion check

- Test data survives repository recreation.
- Aggregate totals remain mathematically consistent with completed runs.
- Reset operations affect only their intended scope.

### Step 7a. Track optional cannonball consumption

1. Baseline RuneLite's loaded-cannon player variable on login and profile changes.
2. While a Slayer assignment is active, accumulate decreases as ammunition consumed.
3. Treat increases as reloads and never as usage.
4. Confirm the cannon is still placed after the tick's state changes before committing a decrease.
5. Persist `cannonballsUsed` in active checkpoints and completed runs.
6. Aggregate cannonball totals with only the literal kills and effective units from cannon-using runs.
7. Show cannonballs used, weighted cannonballs per Slayer kill, and assignment-size estimate.
8. Keep tracking and display independently configurable.

Tests:

- Firing decreases usage.
- Reloading does not increase usage.
- Cannon pickup does not look like fired ammunition.
- Disabled or taskless tracking rebaselines without recording.
- Checkpoint and completed-run persistence retain usage.
- Excluding or deleting a run reverses its cannon aggregates.
- Task-size estimates use effective progress units and round supplies up.

## Milestone 4: Track effective task progress

### Step 8. Integrate `SlayerPluginService`

Inject these dependencies into `SlayerSpeedPlugin`:

```text
Client
SlayerPluginService
ConfigManager
Gson
ClientToolbar
OverlayManager
```

On `GameTick`, take a snapshot containing:

```text
taskName
taskLocation
initialAmount
remainingAmount
gameTick
timestamp
```

Do not directly mutate statistics in the event subscriber. Pass the snapshot to `TaskTracker`.

### Step 9. Implement `TaskTracker`

Implement these transitions:

```text
NO_TASK -> ACTIVE_TASK
ACTIVE_TASK -> SAME_TASK_PROGRESS
ACTIVE_TASK -> SAME_TASK_ADJUSTMENT
ACTIVE_TASK -> DIFFERENT_TASK
ACTIVE_TASK -> NO_TASK
```

Behaviour:

1. Start a run when a nonblank task appears.
2. If the plugin starts midway through a task, use the current remainder as the baseline and do not invent previous progress.
3. For a positive decrease, add the complete delta to `taskProgressUnits`.
4. For an increase, update the baseline without counting progress.
5. If the task key changes, close the old run as `REPLACED` unless completion was already confirmed.
6. If the task disappears with completion evidence, close it as `COMPLETED`.
7. Otherwise close it as `UNKNOWN_INCOMPLETE`.
8. Use a short completion grace period so final counter, death, and XP events can settle.

Expose domain events or callbacks such as:

```text
onTaskStarted
onTaskProgressed
onTaskCompleted
onTaskInterrupted
```

### Step 10. Implement `ActiveTimeTracker`

Track timestamps from confirmed task activity.

Rules:

1. First activity establishes the timing baseline.
2. Later activity adds `min(gap, idleTimeout)` to active time.
3. Logout and connection loss pause timing.
4. World hopping must not close the run.
5. A configurable idle timeout defaults to five minutes.
6. Do not use assignment-to-first-kill time in KPH.

### Step 11. Produce the first working metrics

Using only task-counter progress, display in logs or a temporary overlay:

```text
Current task
Remaining amount
Current effective KPH
Historical effective KPH
ETA
```

At this stage, literal KPH and Slayer XP/hour may display as unavailable.

Tests:

- Normal decrement of one.
- Decrement of two.
- No decrement.
- Task adjustment upward.
- Task replacement.
- Logout and resume.
- Long idle gap.
- Plugin enabled midway through a task.

### Milestone completion check

- A real task produces effective KPH and a changing ETA.
- Expeditious-style double progress records two units.
- No off-task activity is recorded.
- The run resumes safely after a world hop.

## Milestone 5: Build the initial sidebar and overlay

### Step 12. Implement `SlayerSpeedPanel`

Create a RuneLite navigation button and Swing panel.

Initial current-task view:

```text
Task name and location
Remaining amount
Current effective KPH
Historical effective KPH
ETA
Sample size and confidence
```

Use an explicit unavailable state such as `Collecting data` rather than displaying zero KPH or zero ETA.

### Step 13. Implement `SlayerSpeedOverlay`

Add an optional compact overlay containing configurable fields:

- Remaining amount
- Effective KPH
- ETA
- Later: literal KPH and Slayer XP/hour

Register it during plugin startup and remove it during shutdown.

### Step 14. Add initial configuration

Add settings for:

- Show sidebar
- Show overlay
- Overlay fields
- Idle timeout
- Separate history by location
- Current-rate blending threshold
- Maximum recent runs

### Milestone completion check

- The sidebar updates without reopening it.
- The overlay can be toggled without restarting.
- Missing history is presented clearly.
- UI updates occur safely on the Swing event thread.

## Milestone 6: Track Slayer XP

### Step 15. Implement `SlayerXpTracker`

Subscribe to `StatChanged` and filter for `Skill.SLAYER`.

On each event:

1. Calculate `currentXp - previousXp`.
2. Ignore the initial observation.
3. Ignore zero or negative changes.
4. Record the tick and positive delta as a pending XP event.
5. Pass it to `KillAttributionService` rather than immediately assigning it.

Keep the previous XP baseline updated even when no task is active so that later deltas are calculated correctly.

### Step 16. Protect against unrelated XP

An XP delta is task-attributable only when it occurs near:

- A candidate target death;
- A task progress event; or
- Confirmed task completion.

Do not include an isolated Slayer XP change merely because a task exists. Add a configurable development constant for the correlation window, initially one or two game ticks.

Tests:

- First XP observation creates only a baseline.
- Positive correlated XP is assigned.
- XP lamp use during a task is rejected.
- Final-kill XP inside the grace window is assigned.
- XP outside an active task is rejected.

### Milestone completion check

- Correlated Slayer XP appears on the active task.
- Unrelated Slayer XP does not alter the task record.
- Slayer XP/hour becomes available in the UI.

## Milestone 7: Attribute literal kills

### Step 17. Capture candidate deaths

Subscribe to `ActorDeath`.

Create a `CandidateDeath` only when:

1. The actor is an NPC.
2. A task is active.
3. The NPC is present in `SlayerPluginService.getTargets()`.
4. The candidate has not already been seen.

Store:

```text
NPC index or stable in-session identity
NPC ID and name
death tick
world location
recent interaction evidence
matched signals
expiry tick
```

Expire unmatched candidates after a small number of ticks.

### Step 18. Add supporting signals

Feed these events into `KillAttributionService`:

- Task progress deltas from `TaskTracker`
- Pending Slayer XP deltas from `SlayerXpTracker`
- `NpcLootReceived` events
- Available local-player interaction state

Deduplicate every event before modifying a task.

### Step 19. Implement attribution rules

Start conservatively. Count a literal kill when a candidate target death has strong local-player evidence, for example:

```text
candidate death + Slayer XP
candidate death + task progress + recent interaction
candidate death + matching loot
```

Important bracelet behaviour:

| Situation | Literal kills | Progress units | Slayer XP |
|---|---:|---:|---:|
| Normal task kill | 1 | 1 | Monster XP |
| Expeditious activation | 1 | 2 | Monster XP |
| Slaughter activation | 1 | 0 | Monster XP |

Do not assume that the progress delta is the number of literal kills.

For simultaneous deaths, match candidates and signals as a batch for the tick rather than assuming a strict one-event order.

### Step 20. Add diagnostic mode

During development, add debug logging that records:

- Candidate creation and expiry
- XP and progress signals
- Attribution decisions
- Rejected candidates and reasons
- Duplicate suppression

Do not expose excessive chat messages to normal users. Remove sensitive or noisy diagnostic output before release, or keep it behind developer mode.

Tests:

- Target killed by the player.
- Same target type killed by another player.
- Kill with no task decrement.
- One death with a decrement of two.
- Multiple deaths in one tick.
- Loot signal arriving before or after XP.
- Duplicate events.
- Candidate expiration.

### Milestone completion check

- Literal KPH works in ordinary single combat.
- Bracelet activations keep literal kills separate from progress units.
- Nearby players' kills are rejected in the tested scenarios.
- Attribution uncertainty does not corrupt effective KPH or ETA.

## Milestone 8: Complete statistics and history UI

### Step 21. Add all current-task statistics

Display:

```text
Monster KPH
Effective KPH
Slayer XP/hour
Estimated time remaining
Current observations
Historical sample size
Confidence
```

Use effective KPH exclusively for the ETA.

### Step 22. Implement historical-rate blending

1. Use historical effective KPH when the current run has insufficient data.
2. After the configured minimum, blend historical totals with the current run.
3. Show current and historical values separately so the estimate is explainable.
4. Never claim high confidence from a small sample.

### Step 23. Add the history view

Columns:

```text
Task
Location
Monster KPH
Effective KPH
Slayer XP/hour
Average duration
Completed tasks
Last updated
```

Add a detailed recent-runs view and controls to reset one task or all data. Require confirmation before deletion.

### Milestone completion check

- Aggregated UI values match calculator tests and stored totals.
- Incomplete runs are visible but excluded from averages.
- The user can understand why an ETA has low or high confidence.

## Milestone 9: Handle lifecycle and edge cases

### Step 24. Complete checkpoint recovery

On startup or login:

1. Load the account-specific checkpoint.
2. Read the live Slayer task.
3. Resume only if its task key and plausible state match.
4. Otherwise archive the checkpoint as incomplete.
5. Never count progress that occurred while the plugin was unavailable.

### Step 25. Verify task endings

Test and refine classification for:

- Normal completion
- Skip
- Cancel
- Block
- Replacement
- Task extension or count adjustment
- Plugin shutdown during the last kill
- Completion signals arriving in different event orders

Prefer task service state and numeric transitions. Use chat-message parsing only as a narrowly scoped fallback if a transition cannot otherwise be distinguished.

### Step 26. Verify difficult combat cases

Manually collect diagnostic evidence for:

- Cannons
- Thralls
- Multicombat
- Burst and barrage stacks
- Superiors
- Slayer bosses
- Shared damage
- Empty drops
- Instanced encounters
- Both Slayer bracelets

If literal attribution remains uncertain for a specific case, omit that literal kill rather than guessing. Effective KPH and ETA must remain independent and reliable.

### Milestone completion check

- No known lifecycle transition duplicates or loses completed runs.
- Unsupported literal-kill cases fail conservatively.
- Effective KPH remains correct when literal attribution is unavailable.

## Milestone 10: Final verification and release

### Step 27. Run automated quality checks

1. Run all unit tests.
2. Run formatting and checkstyle tasks.
3. Build the plugin from a clean checkout.
4. Test against RuneLite `latest.release`.
5. Remove unused dependencies, temporary code, and uncontrolled debug output.

### Step 28. Conduct a real-world beta

Record multiple complete tasks across different combat styles. For every run, compare:

- Observed physical kills against literal kills.
- Starting and ending task counts against progress units.
- RuneLite XP tracker results against stored Slayer XP.
- Stopwatch time against active-time rules.
- Actual completion time against the predicted ETA.

Record defects as reproducible scenarios and add regression tests before changing attribution logic.

### Step 29. Prepare Plugin Hub material

Add:

- Finished README with screenshots and metric definitions
- Plugin icon within Plugin Hub limits
- BSD 2-Clause licence
- Configuration documentation
- Local-storage and privacy explanation
- Known limitations for literal kill attribution

### Step 30. Submit to Plugin Hub

1. Push the public plugin repository.
2. Fork `runelite/plugin-hub`.
3. Add the plugin marker containing the repository URL and full commit hash.
4. Open the Plugin Hub pull request.
5. Resolve build checks and reviewer feedback.
6. Retest every RuneLite API change requested during review.

## Suggested implementation checkpoints

Keep changes reviewable with checkpoints similar to:

1. `Scaffold SlayerSpeed plugin`
2. `Add task models and KPH calculations`
3. `Add profile-scoped task history storage`
4. `Track effective task progress and active time`
5. `Add current-task panel and ETA overlay`
6. `Track attributable Slayer XP`
7. `Add literal kill attribution`
8. `Add task history and confidence display`
9. `Handle task lifecycle edge cases`
10. `Complete release documentation and tests`

## Definition of done

Implementation is complete when:

- Literal kills, effective progress, Slayer XP, and active time are stored independently.
- Effective KPH drives a stable task ETA.
- Literal KPH and Slayer XP/hour are displayed when attribution is reliable.
- Other players' kills and unrelated XP are rejected.
- Bracelet effects are represented correctly.
- Logout, hopping, AFK time, task replacement, and plugin restarts are handled.
- Completed history survives client restarts and remains account-specific.
- Automated tests and the real-world test matrix pass.
- Plugin Hub documentation and submission material are ready.
