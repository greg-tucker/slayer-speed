# Slayer Task Speed

Slayer Task Speed is a RuneLite plugin that estimates how long your current Slayer task will take. It learns from your own completed tasks, so the estimates reflect your gear, route and play style rather than a generic rate.

## What it tracks

- **Kills/hr** — confirmed monster kills during the task.
- **Task units/hr** — how quickly the Slayer task counter goes down. This is the rate used for ETA.
- **Slayer XP/hr** — Slayer XP matched to task activity, including superior monsters.
- **Cannon use** — average cannonballs per kill and an estimate for the current assignment.

Kills and task units are kept separate because bracelets can make one kill remove zero, one or two points from the task counter.

## Features

- Personal averages for each task, with optional location separation.
- Separate rates for different ways of doing the same assignment, such as Araxytes and Araxxor.
- Automatic encounter detection with a manual selector when more than one estimate is available.
- Current ETA, expected finish time, pace comparison and completion summary.
- Cannonball tracking that counts ammunition fired rather than cannon reloads.
- Recent task history with controls to exclude or delete bad runs.
- A compact sidebar and optional in-game overlay.
- Experimental time estimates on Mortimer's task-choice screen. This is disabled by default.

Only completed tasks are included in saved averages. Incomplete and replaced tasks remain visible in recent history but do not affect the estimate.

## Data storage

Task history is stored in RuneLite's account-specific profile data. The plugin has no server and does not make network requests.

Saved data includes task names, locations, encounter types, kills, task progress, Slayer XP, cannonballs, active time and timestamps. The **View stored stats** window in the sidebar can be used to inspect or copy the raw records.

History from older plugin versions is kept as **Older mixed data** when it cannot be safely assigned to a particular monster or boss.

## Development

The project uses Java 11 and includes a Gradle wrapper.

Run the tests:

```powershell
.\gradlew.bat test
```

Launch the RuneLite development client:

```powershell
.\gradlew.bat run
```

If you use a Jagex Account, follow RuneLite's [development-client login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Notes

- Rates use active task time. Time before the first recorded activity is stored separately and is not included in KPH.
- A shared kill may be missed when it produces no task progress, Slayer XP or matching loot.
- If two different encounters are mixed in one assignment, Auto saves the run under the encounter with the most confirmed kills. A manual selection overrides this for the current task.
- Mortimer shows an assignment range before the task is accepted, so its experimental estimate is also shown as a range.

## Licence

BSD 2-Clause. See [LICENSE](./LICENSE).
