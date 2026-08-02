package com.slayerspeed.ui;

import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class StoredStatsDebugFormatterTest
{
	@Test
	public void includesExactAggregateAndRetainedRunFields()
	{
		TaskStatistics statistics = new TaskStatistics(
			"Araxytes", "Slayer Tower", "boss:araxxor", "Araxxor (boss)");
		statistics.addRun(new TaskRun(
			"run-debug",
			"Araxytes",
			"Slayer Tower",
			"boss:araxxor",
			"Araxxor (boss)",
			150,
			0,
			true,
			145,
			150,
			30_000,
			1_500,
			700,
			3_600_000L,
			120_000L,
			1_770_000_000_000L,
			1_770_003_600_000L,
			TaskRunStatus.COMPLETED), 50);

		String debug = StoredStatsDebugFormatter.format(Collections.singletonList(statistics));

		assertTrue(debug.contains("Storage key: araxytes|slayer tower|target:boss:araxxor"));
		assertTrue(debug.contains("Encounter profile ID: boss:araxxor"));
		assertTrue(debug.contains("Actual kills: 145"));
		assertTrue(debug.contains("Task progress units: 150"));
		assertTrue(debug.contains("Effective KPH: 150.0"));
		assertTrue(debug.contains("Cannonballs used: 700"));
		assertTrue(debug.contains("ID: run-debug"));
		assertTrue(debug.contains("Bonus Slayer XP: 1500"));
	}

	@Test
	public void clearlyReportsEmptyStorage()
	{
		String debug = StoredStatsDebugFormatter.format(Collections.emptyList());

		assertTrue(debug.contains("Exact task/location/encounter records: 0"));
		assertTrue(debug.contains("No stored task statistics."));
	}
}
