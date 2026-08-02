# Slayer Task Speed

Slayer Task Speed is a RuneLite Plugin Hub plugin that learns your personal Slayer task speeds and cannonball usage. It tracks KPH and Slayer XP per hour, then estimates task duration and the cannonballs needed for your current assignment size.

## Metrics

- **Monster KPH:** Confirmed physical task kills per hour.
- **Effective KPH:** Slayer task-counter progress per hour.
- **Slayer XP/hour:** Slayer XP correlated with task activity, including superior monsters and completion rewards.
- **ETA:** Remaining task amount divided by estimated effective KPH, with seconds shown for short estimates.
- **Cannonballs per Slayer kill:** Cannonballs consumed divided by confirmed physical task kills from cannon-using runs.
- **Estimated task cannonballs:** Assignment size multiplied by your historical cannonballs per effective task unit.

Effective KPH drives the ETA because bracelet effects can make one physical kill remove zero, one, or two task units.

## Features

- Tracks only while a Slayer assignment is active.
- Keeps literal kills, effective progress, and Slayer XP separate.
- Reconciles simultaneous multicombat deaths as a batch and shows confirmed kills beside task units.
- Tracks cannonball consumption without counting reloads, and estimates supplies for the current task size.
- Uses RuneLite's Slayer service to identify the current assignment and eligible targets.
- Excludes logged-out time and caps long idle gaps.
- Stores weighted task history in the active RuneScape profile.
- Separates location-specific assignments by default.
- Shows current rates, historical confidence, ETA, and task history in a sidebar panel.
- Separates live task performance from learned historical averages.
- Uses a compact default view with an optional detailed mode.
- Hides unavailable pace and irrelevant cannon rows instead of filling the panel with placeholders.
- Compares the current pace with your personal average and shows an estimated finish time.
- Shows a configurable completion summary with personal-best detection.
- Lets you inspect dated task runs with assignment size and kill coverage, then exclude, restore, or delete them from an actions menu.
- Explains when it is still learning a new task speed instead of showing a misleading estimate.
- Provides an optional in-game overlay.
- Retains incomplete runs without including them in completed-task averages.
- Requires confirmation before deleting saved history.

Configuration is grouped into Display, Estimates, Cannon, History, and Advanced sections. Cannon tracking and cannon metric display are separate options, and cannon rows appear only when relevant. The number of recent runs shown and the observation threshold used for live estimates are configurable.

## Data and privacy

SlayerSpeed has no server and makes no network requests. Task history is serialized through RuneLite's account-specific profile configuration. It contains task names, locations, kill and progress counts, XP totals, cannonballs used, active durations, timestamps, and completion status.

## Development

Requirements:

- Java 11-compatible source
- The Gradle wrapper included in this repository

Run the automated tests:

```powershell
.\gradlew.bat test
```

Launch the RuneLite development client:

```powershell
.\gradlew.bat run
```

Jagex Account users should follow RuneLite's [development-client login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Manual test checklist

Before release, verify with the player controlling the client:

1. Start a normal Slayer assignment and confirm it appears in the panel.
2. Complete several kills and compare the task counter, literal kills, and Slayer XP.
3. Test an expeditious bracelet activation: one literal kill should be separate from two effective units.
4. Test a slaughter bracelet activation: one literal kill should be recorded even when task progress is zero.
5. Log out, reconnect, and world hop without adding offline time or duplicating the run.
6. Skip or replace a task and confirm it does not enter completed-task averages.
7. Use an unrelated Slayer XP reward while a task is active and confirm it is not attributed without a matching target death.
8. Fire and reload a cannon during a task. Confirm only fired ammunition is counted and the per-kill and task-size estimates update.
9. Test thralls, multicombat, bursting, bosses, superiors, and simultaneous deaths.
10. Restart the client and confirm history and the active checkpoint survive.
11. Exercise current-task and all-history reset confirmations.

## Known limitation

Literal kill attribution reconciles simultaneous eligible deaths as a batch against Slayer task progress, then uses XP and loot evidence to recover kills whose progress was prevented by a bracelet. Shared kills that produce neither task progress, Slayer XP, nor matching loot remain intentionally uncounted. Effective KPH and ETA are tracked independently from literal attribution.

## Design documents

- [Overall plugin plan](./PLUGIN_PLAN.md)
- [Step-by-step implementation plan](./IMPLEMENTATION_PLAN.md)

## Licence

BSD 2-Clause. See [LICENSE](./LICENSE).
