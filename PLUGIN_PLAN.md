# SlayerSpeed RuneLite Plugin Plan

## 1. Product goal

Build a RuneLite Plugin Hub plugin that records a player's performance only while they are completing an active Slayer assignment. The plugin will retain per-task history and use it to estimate how long future assignments will take.

Each recorded task will track three complementary measurements:

- **Literal kills:** Monsters actually killed and credited to the local player.
- **Effective task progress:** The amount removed from the Slayer task counter.
- **Slayer XP:** Slayer experience attributed to eligible task kills and task-completion rewards.

The plugin will derive literal KPH, effective KPH, Slayer XP per hour, and estimated time remaining from these measurements.

## 2. Confirmed metric definitions

### Literal KPH

Literal KPH measures physical monster kills:

```text
literal KPH = actual kills * 3600 / active seconds
```

This represents combat output but is not always an accurate predictor of when a task will end.

### Effective KPH

Effective KPH measures task-counter progress:

```text
effective KPH = task progress units * 3600 / active seconds
```

This is the primary ETA metric because Slayer bracelet effects can make one physical kill remove zero, one, or more task units.

### Slayer XP per hour

```text
Slayer XP/hour = attributed Slayer XP * 3600 / active seconds
```

The plugin can also calculate:

```text
XP per monster = attributed Slayer XP / actual kills
XP per task unit = attributed Slayer XP / task progress units
```

### ETA

```text
ETA seconds = remaining task amount * 3600 / estimated effective KPH
```

Effective KPH will drive the ETA. Literal KPH and Slayer XP/hour will be displayed as supporting performance statistics.

## 3. Agreed MVP behaviour

- Store literal kills, task progress, Slayer XP, and active time.
- Use effective KPH for task-duration estimates.
- Include short travel, looting, and banking gaps in active time.
- Pause timing while logged out.
- Exclude long idle gaps using a configurable threshold, initially five minutes.
- Keep task averages separate by task name and assigned location.
- Keep materially different eligible monsters and bosses in separate encounter profiles without splitting identical NPC names by combat level.
- Let the player select a profile before combat, while default Auto mode switches to the profile inferred from confirmed kills.
- Save incomplete, cancelled, or skipped tasks in recent history but exclude them from historical averages.
- Keep aggregate statistics and up to the latest 50 runs per task.
- Keep all data local and account-specific; the MVP will not require a remote server.

## 4. RuneLite integration

RuneLite plugins generally contain a main plugin class, configuration, event subscribers, overlays, and optionally a sidebar panel. See the [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide).

Inject `SlayerPluginService` as the authoritative source for:

- Current assignment name
- Assignment location
- Initial amount
- Remaining amount
- NPCs matching the current assignment

The current interface is documented in [`SlayerPluginService`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/slayer/SlayerPluginService.java).

Use these RuneLite events:

- `GameTick`: Observe stable task state after game packets have been processed.
- `ActorDeath`: Capture candidate deaths of task-eligible NPCs.
- `StatChanged`: Capture changes to Slayer XP.
- `GameStateChanged`: Pause, checkpoint, and resume around login, logout, connection loss, and world hopping.
- `NpcLootReceived`: Add supporting evidence that a candidate NPC kill belongs to the local player when loot exists.

Relevant events are listed in the [RuneLite API event documentation](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/events/package-summary.html).

## 5. Proposed architecture

```text
SlayerSpeedPlugin
|-- SlayerSpeedConfig
|-- TaskTracker
|-- KillAttributionService
|-- EncounterProfileResolver
|-- SlayerXpTracker
|-- ActiveTimeTracker
|-- TaskHistoryRepository
|-- KphCalculator
|-- SlayerSpeedPanel
|-- SlayerSpeedOverlay
|-- MortimerTaskChoiceParser
|-- MortimerEstimateService
|-- MortimerChoiceOverlay
`-- model
    |-- ActiveTask
    |-- CandidateDeath
    |-- TaskRun
    |-- TaskStatistics
    `-- TaskKey
```

### Responsibilities

- `SlayerSpeedPlugin`: Plugin lifecycle, dependency injection, event subscriptions, and UI registration.
- `TaskTracker`: Assignment state machine, task progress, task completion, and cancellation handling.
- `KillAttributionService`: Correlate target deaths, XP changes, counter changes, and loot signals.
- `EncounterProfileResolver`: Convert confirmed NPC names into stable task-scoped estimate profiles and group multi-form encounters.
- `SlayerXpTracker`: Calculate positive Slayer XP deltas and attribute eligible XP to the active task.
- `ActiveTimeTracker`: Accumulate active time while excluding logout and long idle periods.
- `TaskHistoryRepository`: Load, migrate, save, reset, and cap account-specific history.
- `KphCalculator`: Produce rates, ETA, averages, and confidence indicators.
- `SlayerSpeedPanel`: Current task, history, sample size, and data-management controls.
- `StoredStatsDebugFormatter`: Produce a copyable, read-only dump of exact persisted task/profile records and retained runs.
- `SlayerSpeedOverlay`: Optional compact in-game statistics.
- `MortimerTaskChoiceParser`: Read task names, base amount ranges, and flat quantity modifiers from Mortimer's dedicated choice interface.
- `MortimerEstimateService`: Convert each offered amount range into personal effective-KPH time ranges without mixing encounter profiles.
- `MortimerChoiceOverlay`: Experimental, read-only annotations on Mortimer's offer rows.

## 6. Task state machine

On each `GameTick`, read the current task through `SlayerPluginService` and compare it with the last observed state.

### No task to active task

1. Create an `ActiveTask`.
2. Record its normalized task key, initial amount, current remainder, and location.
3. Capture the current Slayer XP as a baseline.
4. Do not infer progress that occurred before the plugin started.

### Existing task decreases

```text
progress delta = previous remainder - current remainder
```

When the delta is positive:

1. Add it to `taskProgressUnits`.
2. Record a progress timestamp.
3. Use the signal to help confirm recent candidate deaths.
4. Recalculate current effective KPH and ETA.

A decrease of two counts as two effective units even if it came from one physical kill.

### Existing task increases

Treat increases as task adjustments, not kills. Update the baseline without adding progress.

### Task changes or disappears

- Mark a task complete when completion can be confirmed.
- Mark it incomplete when it is skipped, cancelled, blocked, or replaced.
- Include only completed tasks in historical averages by default.
- Allow the final kill and XP events to arrive within a short grace window before closing the record.

### Logout or world hop

- Checkpoint the active task.
- Pause active timing.
- Resume if the same task is present after login.
- Do not create a duplicate run after an ordinary world hop.

## 7. Literal kill attribution

`ActorDeath` alone is insufficient because it can report nearby deaths caused by other players. Each eligible target death should therefore become a short-lived `CandidateDeath` rather than immediately incrementing the kill count.

### Candidate creation

Create a candidate when:

- The dead actor is an NPC.
- A current Slayer task exists.
- The NPC appears in `SlayerPluginService.getTargets()`.
- The death has not already been processed.

Store the NPC identity, name, game tick, location, and available interaction information.

### Confirmation signals

Confirm a candidate as a local-player kill using a combination of:

- A Slayer XP increase within a small tick window.
- A task-counter decrease within a small tick window.
- A matching `NpcLootReceived` event when the NPC produced loot.
- Recent local-player interaction with the NPC.

No single supporting signal is universally reliable:

- Loot events may not exist for empty drops.
- A slaughter-bracelet activation produces no task-counter decrease.
- An expeditious-bracelet activation can produce a decrease of two for one death.
- Interaction alone is unreliable with cannons, thralls, and multicombat.

The implementation should retain confidence/debug information during development so attribution rules can be tested without silently overcounting.

## 8. Slayer XP attribution

Subscribe to `StatChanged` and process only `Skill.SLAYER`.

```text
XP delta = current Slayer XP - previous Slayer XP
```

Add a positive delta to the active task only when it can be correlated with:

- A recent eligible candidate death;
- Recent task progress; or
- A just-completed task inside the completion grace window.

This prevents unrelated sources, such as an XP lamp used while a task is active, from contaminating the task rate.

Where possible, keep these fields distinct:

- `killSlayerXp`: XP associated with confirmed monster kills.
- `bonusSlayerXp`: Any confirmed task-completion bonus.
- `totalSlayerXp`: Sum used for the displayed Slayer XP/hour.

## 9. Active-time measurement

Do not use uninterrupted wall-clock time from assignment to completion. A task could be assigned and then left untouched for hours or days.

Recommended MVP behaviour:

1. Start the kill-phase timer on the first confirmed task activity.
2. Measure gaps between confirmed progress or kill events.
3. Add at most the configured idle threshold for each gap.
4. Pause on logout or connection loss.
5. Resume on the next valid activity event.

```text
active seconds += min(seconds since previous activity, idle threshold)
```

Track assignment-to-first-activity separately as optional `setupSeconds`. It should not affect KPH unless the player enables a future setting to include setup time.

## 10. Data model

### Task run

```text
TaskRun
- schemaVersion
- id
- taskName
- taskLocation
- initialAmount
- endingAmount
- actualKills
- taskProgressUnits
- killSlayerXp
- bonusSlayerXp
- totalSlayerXp
- cannonballsUsed
- activeSeconds
- setupSeconds
- startedAt
- completedAt
- status
```

Suggested statuses:

```text
ACTIVE
COMPLETED
SKIPPED
CANCELLED
REPLACED
UNKNOWN_INCOMPLETE
```

### Aggregate statistics

```text
TaskStatistics
- taskKey
- totalActualKills
- totalTaskProgressUnits
- totalSlayerXp
- totalCannonballsUsed
- totalCannonRunActualKills
- totalCannonRunTaskProgressUnits
- totalActiveSeconds
- completedTaskCount
- recentRuns
- fastestCompletedRun
- lastUpdatedAt
```

### Task key

For the MVP:

```text
normalized task name + normalized assigned location
```

Locationless assignments use only the normalized task name. A future version could optionally separate statistics by combat method, such as melee, cannon, burst, barrage, or bossing.

### Cannonball usage

When enabled, read the player's loaded-cannon count and accumulate only decreases while an assignment is active. Ammo increases are reloads and must not be counted. Commit decreases after placement state has settled for the tick so picking up a loaded cannon is not mistaken for firing it.

```text
cannonballs per Slayer kill =
    total cannonballs used / confirmed literal kills in cannon-using runs

cannonballs per effective task unit =
    total cannonballs used / task progress units in cannon-using runs

estimated cannonballs for assignment =
    assignment size * historical cannonballs per effective task unit
```

The displayed per-kill figure is a supply-use metric, not an attempt to identify which individual monster received the cannon's killing blow. Use effective task units for the assignment-size estimate so bracelet and multi-unit progress effects remain consistent with the Slayer counter.

## 11. Persistence

Use RuneLite's account-specific profile configuration through `ConfigManager`.

Store:

- Compact aggregate statistics for each task key.
- The latest 50 runs per task.
- Any checkpointed active task.
- A schema version for migrations.

Use RuneLite's existing Gson dependency for JSON serialization. Avoid new third-party dependencies and avoid a remote server in the MVP.

Persistence requirements:

- Debounce ordinary saves rather than writing every game tick.
- Save immediately on task completion and plugin shutdown.
- Recover safely from corrupt or outdated data.
- Keep different RuneScape profiles separate.
- Provide reset controls with confirmation.
- Consider JSON export/import as a later feature.

## 12. Statistics and estimation

Use weighted aggregate rates rather than averaging the KPH value of each run:

```text
historical literal KPH = total actual kills * 3600 / total active seconds

historical effective KPH =
    total task progress units * 3600 / total active seconds

historical Slayer XP/hour =
    total attributed Slayer XP * 3600 / total active seconds
```

### Current versus historical estimates

- Before the current task has enough observations, use historical effective KPH.
- After approximately ten progress units, blend the current rate with historical data.
- Do not show a precise ETA when no usable sample exists.
- Always show the number of completed tasks and recorded units behind an estimate.

### Confidence

Initial suggested levels:

- **No data:** No completed history.
- **Low:** Fewer than two completed tasks or 30 progress units.
- **Medium:** Two to four completed tasks with a reasonable sample.
- **High:** Five or more completed tasks with consistent results.

Confidence thresholds can be refined after real-world testing.

## 13. User interface

### Current task panel

```text
Abyssal demons - 148 remaining

Monster KPH:       112
Effective KPH:     119
Slayer XP/hour:    16,850
Estimated time:    1h 15m

Based on: 6 completed tasks
Confidence: High
```

Add tooltips explaining that effective KPH measures task-counter progress and is therefore used for ETA.

### History table

| Task | Monster KPH | Effective KPH | Slayer XP/h | Avg duration | Tasks |
|---|---:|---:|---:|---:|---:|
| Gargoyles | 91 | 94 | 14,300 | 1h 42m | 5 |
| Abyssal demons | 112 | 119 | 16,850 | 1h 17m | 6 |
| Dust devils | 232 | 246 | 41,200 | 38m | 7 |

### Controls

- Current task view
- Historical task list
- Recent individual runs
- Idle-time threshold
- Separate statistics by assigned location
- Overlay visibility and contents
- Reset selected task
- Reset all history
- View and copy exact stored statistics for debugging

Destructive reset actions must require confirmation.

## 14. Configuration

Suggested MVP settings:

- Show sidebar panel
- Show in-game overlay
- Overlay fields: literal KPH, effective KPH, Slayer XP/hour, ETA
- Idle timeout in minutes, default five
- Separate history by assigned location, default enabled
- Minimum sample before current-rate blending, default ten units
- Keep incomplete runs in history, default enabled
- Maximum recent runs per task, default 50
- Experimental Mortimer task-choice estimates, default disabled

### Experimental Mortimer task choices

When enabled, read Mortimer's dedicated Slayer task-choice interface and annotate each visible offer without changing its text, actions, or click behaviour. Parse the displayed minimum and maximum amount, then apply a flat `+/- Assigned` quantity Mortifier before calculating the preview.

Use historical effective KPH because the offered quantities are task-counter units. Mortimer does not expose a location before selection, so aggregate saved locations for this preview only. Continue to keep materially different encounter profiles separate: an Araxyte offer can show independent regular Araxyte and Araxxor ranges. Show a clear no-data state when no completed history is available.

The final assignment amount is rolled only after selection. The choice interface must therefore show a time range; after selection, the normal active-task overlay supplies the exact ETA.

## 15. Testing plan

### Unit tests

- Literal, effective, and XP/hour calculations
- Weighted historical aggregation
- ETA calculation and duration formatting
- Idle-gap capping
- Task-key normalization
- Assignment start, progress, completion, and replacement
- Remaining amount decreasing by zero, one, or multiple units
- Logout and world-hop resume behaviour
- XP attribution windows
- Candidate-death deduplication and expiry
- Completed versus incomplete average inclusion
- History caps and schema migrations
- Mortimer choice parsing, quantity adjustments, task-name aliases, and separate encounter estimates
- Corrupt persistence recovery

### Manual tests

- Normal single-combat task
- Cannon or multicombat task
- Burst or barrage task
- Boss assignment
- Location-restricted assignment
- Superior creature
- Empty or unusual drop
- Another player killing the same target type nearby
- Shared damage or kill ownership
- Thrall-assisted kill
- Expeditious bracelet activation
- Bracelet of slaughter activation
- Several deaths on one game tick
- Logout, reconnect, and world hop during a task
- Skip, cancel, block, extend, and replace a task
- Disable and re-enable the plugin midway through a task
- Use an XP lamp while a task is active and verify it is excluded

### Acceptance criteria

The MVP is ready when:

- Off-task kills and XP never enter task statistics.
- Other players' kills are not counted as literal kills.
- Bracelet effects produce the expected difference between literal and effective KPH.
- Slayer XP is attributed without including unrelated XP sources.
- Logout and long AFK periods do not inflate task time.
- Skipped and cancelled tasks do not affect averages.
- Data survives client restarts and remains profile-specific.
- Returning players receive an ETA when historical data exists.
- The displayed ETA updates as the current task progresses.
- The UI clearly explains all three rates and their sample size.

## 16. Delivery phases

### Phase 1: Project foundation

- Check the current Plugin Hub for overlapping plugins.
- Generate a repository from the official template.
- Rename the example classes and configure plugin metadata.
- Add the model, repository, and calculator foundations.

### Phase 2: Effective KPH and ETA

- Integrate `SlayerPluginService`.
- Implement the task state machine.
- Track progress units and active time.
- Persist completed and checkpointed tasks.
- Calculate historical effective KPH and ETA.

### Phase 3: Literal kills and Slayer XP

- Capture candidate target deaths.
- Track Slayer XP deltas.
- Implement multi-signal kill attribution.
- Store actual kills and attributed XP.
- Add literal KPH and Slayer XP/hour calculations.

### Phase 4: Interface

- Build the sidebar panel.
- Add current-task and history views.
- Add the optional overlay and settings.
- Add confidence and sample-size indicators.

### Phase 5: Hardening

- Complete automated tests.
- Run the manual bracelet, cannon, multicombat, and lifecycle test matrix.
- Add persistence migrations and recovery.
- Refine attribution rules from debug evidence.

### Phase 6: Plugin Hub release

- Add an icon, README, licence, and storage/privacy explanation.
- Test against RuneLite `latest.release`.
- Submit the repository and pinned commit to the Plugin Hub.
- Resolve automated checks and review feedback.

Follow the official [Plugin Hub setup and submission instructions](https://github.com/runelite/plugin-hub/blob/master/README.md).

## 17. Future enhancements

- Manual or automatic combat-method profiles
- Per-gear or per-style comparisons
- Median and percentile task-duration estimates
- Trend charts across recent tasks
- Estimated XP remaining on the current assignment
- Import and export of history
- Optional opt-in synchronization, subject to Plugin Hub review
- Separate combat-only ETA and full-task ETA including setup time
