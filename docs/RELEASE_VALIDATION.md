# Release validation worksheet

Status: not yet executed in a live RuneLite session. Do not mark complete from synthetic tests.

## Captures needed
- Compact ETA during a real task, with its actual source label.
- Learned encounter estimate and relevant history.
- Last completed result while another assignment is active.
- Optional 15–30 second demo showing task progress.
Use the final build and remove account-identifying details before sharing.

## Gameplay matrix
Record build revision, RuneLite version, method, assignment, initial count and enabled settings.
Exercise melee, cannon, burst and boss tasks; both bracelets; superior and completion XP.
Check initial activity, long kills, breaks, normal logout, world hop, disconnect, restart,
mid-task installation, same-name replacement and consecutive completed assignments.
Check real sidebar at 100%, 150% and 200% scaling and long location names.

## Accuracy pilot
Capture the prediction, elapsed tracked time, task units, remaining count and source at 25%, 50%,
and 75% progress. Pair actual remaining tracked time and wall-clock time separately after completion.
Use only prior history available at each prediction. Do not infer interim predictions from final rates.
Collect at least 20 representative full tasks; evaluate method-specific errors before choosing defaults.

## User feedback
Ask five unfamiliar users to explain the promise and installation steps.
Observe five first-use sessions. Ask the same beta users after one and two weeks whether the plugin
is still useful/enabled and why. No telemetry is required. Installation counts are not retention.

## Release gates
- Relevant tests and full suite pass; inspect regenerated UI fixtures.
- Corrupt/future data remains preserved; backup/restore checked.
- No observed cross-account mutation or duplicate/merged tasks.
- Document tested migration/downgrade path and keep compatible backup.
- Update screenshots, README and changelog to exactly match released behavior.
- Keep experimental estimation options off until evidence supports promotion.
- Use Plugin Hub's normal reviewed commit update; publishing has not been performed.

## Local beta worksheets
Use beta-predictions.csv for predictions captured prospectively and beta-retention.csv for consented follow-up notes.
Use aliases; omit account names and identifying details. These are empty templates, not collected results.
For each method/policy/window report median absolute error and median absolute percentage error where actual remaining time is positive.
Keep tracked-time and wall-time results separate; report unavailable estimates and sample counts as well as errors.
Do not backfill predictions from completed rates or promote defaults using synthetic fixtures.
