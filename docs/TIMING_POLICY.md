# Timing policy contract

Legacy (0) remains the default. Existing checkpoints finish under their stored policy.
Segmented (1) is opt-in for newly started assignments and has its own storage keys.

Segmented time measures intervals between task activity ticks, capped by the configured gap limit.
All evidence in the first untimed tick anchors the sample; it remains in absolute totals but not
rate numerators. Later same-tick signals can contribute counters without duplicating elapsed time.
Pause excludes a break; Resume starts a timed segment, and task activity also resumes automatically.
No kill is allowed to add forever against a frozen denominator.

Because the plugin does not observe a precise combat-start boundary, segmented runs with untimed
anchor intervals are partial for whole-task duration/PB purposes. They can still contribute valid
matched rate samples. This is deliberately conservative pending live tests. No legacy samples are
reinterpreted as matched intervals.

Schema 6 adds timing policy and matched counters. Legacy policy-zero storage keys stay unchanged.
Schema 4 migrates explicitly to 5 and then 6. A backup is saved before migration.
Old plugin versions do not understand schema 6; export it first and restore a pre-migration backup
before a deliberate downgrade. Never let old code open the only copy of schema 6 data.

No live validation or default promotion has been performed.
