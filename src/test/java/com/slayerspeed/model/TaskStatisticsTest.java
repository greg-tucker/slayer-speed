package com.slayerspeed.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TaskStatisticsTest
{
	@Test
	public void aggregatesOnlyCompletedRunsAndCapsHistory()
	{
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		statistics.addRun(run("one", TaskRunStatus.COMPLETED, 100), 2);
		statistics.addRun(run("two", TaskRunStatus.SKIPPED, 20), 2);
		statistics.addRun(run("three", TaskRunStatus.COMPLETED, 80), 2);

		assertEquals(2, statistics.getCompletedTaskCount());
		assertEquals(180, statistics.getTotalActualKills());
		assertEquals(180, statistics.getTotalTaskProgressUnits());
		assertEquals(18_000, statistics.getTotalSlayerXp());
		assertEquals(7_200_000L, statistics.getTotalActiveMillis());
		assertEquals(2, statistics.getRecentRuns().size());
		assertEquals("three", statistics.getRecentRuns().get(0).getId());
	}

	@Test
	public void partialObservationDoesNotCountAsAFullTaskDuration()
	{
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		statistics.addRun(run("full", TaskRunStatus.COMPLETED, 100), 50);
		statistics.addRun(new TaskRun(
			"partial", "Gargoyles", null, 100, 0, false,
			50, 50, 5_000, 0, 1_800_000L, 0, 0, 1, TaskRunStatus.COMPLETED), 50);

		assertEquals(1, statistics.getCompletedTaskCount());
		assertEquals(5_400_000L, statistics.getTotalActiveMillis());
		assertEquals(3_600_000L, statistics.getTotalCompletedTaskMillis());
	}

	@Test
	public void excludedRunsCanBeRestoredOrDeletedWithoutCorruptingTotals()
	{
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		TaskRun slow = runWithMillis("slow", 100, 4_000_000L);
		TaskRun fast = runWithMillis("fast", 80, 2_000_000L);
		statistics.addRun(slow, 50);
		statistics.addRun(fast, 50);

		assertEquals(true, statistics.isPersonalBest(fast));
		assertEquals(true, statistics.setRunExcluded("fast", true));
		assertEquals(1, statistics.getCompletedTaskCount());
		assertEquals(100, statistics.getTotalTaskProgressUnits());
		assertEquals(true, statistics.isPersonalBest(slow));

		assertEquals(true, statistics.setRunExcluded("fast", false));
		assertEquals(2, statistics.getCompletedTaskCount());
		assertEquals(180, statistics.getTotalTaskProgressUnits());
		assertEquals(true, statistics.isPersonalBest(fast));

		assertEquals(true, statistics.deleteRun("fast"));
		assertEquals(1, statistics.getCompletedTaskCount());
		assertEquals(100, statistics.getTotalTaskProgressUnits());
		assertEquals(true, statistics.isPersonalBest(slow));
	}

	@Test
	public void personalBestUsesEffectiveKphRatherThanShortestAssignment()
	{
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		TaskRun shortButSlow = runWithMillis("short", 10, 60_000L);
		TaskRun longButFast = runWithMillis("fast", 100, 300_000L);
		statistics.addRun(shortButSlow, 50);
		statistics.addRun(longButFast, 50);

		assertEquals(true, statistics.isPersonalBest(longButFast));
	}

	@Test
	public void cannonAveragesUseOnlyRunsThatConsumedCannonballs()
	{
		TaskStatistics statistics = new TaskStatistics("Dagannoth", null);
		statistics.addRun(runWithCannon("cannon", 100, 80, 240), 50);
		statistics.addRun(run("melee", TaskRunStatus.COMPLETED, 100), 50);

		assertEquals(240, statistics.getTotalCannonballsUsed());
		assertEquals(80, statistics.getTotalCannonRunActualKills());
		assertEquals(100, statistics.getTotalCannonRunTaskProgressUnits());

		statistics.setRunExcluded("cannon", true);
		assertEquals(0, statistics.getTotalCannonballsUsed());
		assertEquals(0, statistics.getTotalCannonRunActualKills());
	}

	private static TaskRun run(String id, TaskRunStatus status, int amount)
	{
		return new TaskRun(
			id,
			"Gargoyles",
			null,
			amount,
			0,
			amount,
			amount,
			amount * 100,
			0,
			3_600_000L,
			0,
			0,
			1,
			status);
	}

	private static TaskRun runWithMillis(String id, int amount, long activeMillis)
	{
		return new TaskRun(
			id,
			"Gargoyles",
			null,
			amount,
			0,
			amount,
			amount,
			amount * 100,
			0,
			activeMillis,
			0,
			0,
			1,
			TaskRunStatus.COMPLETED);
	}

	private static TaskRun runWithCannon(String id, int progress, int kills, int cannonballs)
	{
		return new TaskRun(
			id, "Dagannoth", null, progress, 0, true, kills, progress,
			progress * 100, 0, cannonballs, 3_600_000L, 0, 0, 1,
			TaskRunStatus.COMPLETED);
	}
}
