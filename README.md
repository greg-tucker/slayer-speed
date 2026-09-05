# Slayer Task Speed

**How long will this Slayer task take?**

Estimate remaining Slayer task time from your own pace. Get a live estimate during your first task, then build personal history for future assignments.

- See your approximate remaining time and optional local finish time.
- Compare your own monster and boss estimates without changing the current recording.
- Review completed tasks, XP rates and cannonball estimates in one sidebar.

The estimate uses tracked task activity. It does not predict future banking, travel or breaks.

## Install and start

1. Open RuneLite's configuration sidebar and **Plugin Hub**.
2. Search for **Slayer Task Speed** and install it.
3. Open its sidebar icon and start a Slayer task. Tracking is automatic.

The source on this branch may contain improvements awaiting a Plugin Hub release.

No completed history is required for the first live estimate: it appears once timed task progress is available. **Blend live rate after** controls when live progress joins an existing historical estimate; it is not an unlock threshold.

## Display and estimates

**Simple** emphasizes remaining count, ETA and the selected pace source. **Detailed** adds kill, task-progress and XP rates. Existing display settings remain configurable.

ETA follows the Slayer counter's progress, not physical kills. Bracelets can make one kill reduce that counter by zero, one or two units. Completed partial observations can inform rates; fully observed task durations are tracked separately. Lifetime history may contain older samples whose individual records have been pruned, so an exact lifetime sample count is not always available.

**Compare estimates** opens a read-only personal-history preview. **Record this task as** is an explicit whole-run encounter override; return to **Auto** to follow confirmed kills. Auto uses the encounter with the most confirmed kills when an assignment mixes encounters.

Cannonball estimates describe ammunition expected to be fired. An **If using a cannon** estimate is a historical scenario, not an inventory stock count. Superior Slayer XP is included in XP rates.

**Estimate history** offers Lifetime (the default), Recent 5, Recent 10 and Recent 25. Recent windows use only eligible retained runs after encounter, location and timing selection. The source shows how many are available; **Show estimate samples** in Compare lists the contributing records. Changing this setting does not change saved totals.

**Experimental segmented timing** is off by default and applies to new assignments. It adds Pause/Resume and keeps matched timed samples separate from legacy history. Task activity resumes a pause automatically. When matching new-policy history is unavailable, a labelled **Legacy lifetime history (fallback)** estimate can appear; the two timing methods are never blended. See [the timing contract](./docs/TIMING_POLICY.md) for details.

The last completion result stays available during your next task. Copy, dismiss or review it locally. Search history by assignment, encounter or location, or show only the current task.

Mortimer's task-choice estimates remain experimental and disabled by default. Their range reflects assignment sizes, not a statistical confidence interval.

## Your data

History is stored in RuneLite's account-specific profile data. This plugin has no operated server and makes no network requests of its own.

**Help & data** offers JSON export, full backups, reviewed imports, backup restore and Undo for a deleted run. Normal import preserves the active assignment; checkpoint recovery is a separate action. Merge is available only when retained records can reconstruct all totals, so old pruned history cannot be accidentally counted twice. Undo lasts 30 seconds and ends when history changes.

Schema upgrades keep a pre-migration backup. Export important history before changing versions; older builds cannot read schema 6. See [recovery and rollback](./docs/RECOVERY_AND_ROLLBACK.md).

**Help & data** also contains stored records and recovery controls. If history cannot be read, the original payload is preserved and the sidebar explains that new tracking is in memory only. Copy the original data before attempting recovery; changing accounts does not transfer that history.

## Troubleshooting

- **No active task:** make sure RuneLite has detected your current Slayer assignment; the plugin depends on RuneLite's Slayer plugin.
- **Waiting for timed activity:** more than an assignment counter is needed to calculate a rate. Continue the task until timed activity is observed.
- **An early estimate changes quickly:** it is based on a short live sample. The source label distinguishes this from saved history.
- **Wrong encounter:** use the explicit recording selector only if you want to override the whole run. Use Compare for a hypothetical alternative.
- **Unexpected task duration:** tracked time includes capped gaps between activity events. The historical default caps each such gap at five minutes. The finish clock assumes you continue at the estimated pace.
- **Bad run:** expand its history and exclude it from averages. Include in averages reverses exclusion.

## Development and validation

Java 11 compilation target, Gradle wrapper, and RuneLite dependencies.

~~~powershell
.\gradlew.bat test
.\gradlew.bat run
~~~

Jagex Account users should follow RuneLite's [development-client login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

See [implementation progress](./IMPLEMENTATION_PROGRESS.md) for the resumable work log and [implementation plan](./UX_IMPLEMENTATION_PLAN.md) for acceptance criteria. Synthetic UI fixtures are generated under build/ux-review; they are not gameplay screenshots. Real release captures and beta checks are tracked separately.

## Licence

BSD 2-Clause. See [LICENSE](./LICENSE).
