package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.SlayerSpeedData;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TaskHistoryRepositoryTest
{
	@Test
	public void codecRoundTripRetainsAggregatesAndCheckpoint()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		SlayerSpeedData data = new SlayerSpeedData();
		TaskStatistics statistics = new TaskStatistics("Gargoyles", "Slayer Tower");
		statistics.addRun(new TaskRun(
			"run-1",
			"Gargoyles",
			"Slayer Tower",
			100,
			0,
			true,
			100,
			100,
			10_500,
			0,
			250,
			3_600_000,
			0,
			1,
			2,
			TaskRunStatus.COMPLETED), 50);
		TaskKey key = new TaskKey("Gargoyles", "Slayer Tower");
		data.getStatisticsByTaskKey().put(key.asStorageKey(), statistics);
		data.setCheckpointedActiveTask(new ActiveTask("Dust devils", null, 150, 120, 3));

		SlayerSpeedData decoded = repository.decode(repository.encode(data));

		assertNotNull(decoded.getCheckpointedActiveTask());
		assertEquals("Dust devils", decoded.getCheckpointedActiveTask().getTaskName());
		assertEquals(1, decoded.getStatisticsByTaskKey().get(key.asStorageKey()).getCompletedTaskCount());
		assertEquals(10_500, decoded.getStatisticsByTaskKey().get(key.asStorageKey()).getTotalSlayerXp());
		assertEquals(250, decoded.getStatisticsByTaskKey().get(key.asStorageKey()).getTotalCannonballsUsed());
	}

	@Test
	public void locationGroupingIsAppliedWhenReadingRatherThanWriting()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		repository.saveRun(run("tower", "Slayer Tower", 100), true, 50);
		repository.saveRun(run("basement", "Slayer Tower Basement", 80), false, 50);

		TaskStatistics combined = repository.find(new TaskKey("Gargoyles", null), false);

		assertEquals(180, combined.getTotalTaskProgressUnits());
		assertEquals(2, repository.allStatistics(true).size());
		assertEquals(1, repository.allStatistics(false).size());
	}

	@Test
	public void unlocatedTaskRemainsSeparateWhenLocationGroupingIsEnabled()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		repository.saveRun(run("unlocated", null, 60), true, 50);
		repository.saveRun(run("tower", "Slayer Tower", 100), true, 50);

		TaskStatistics exact = repository.find(new TaskKey("Gargoyles", null), true);

		assertEquals(60, exact.getTotalTaskProgressUnits());
		repository.deleteTask(new TaskKey("Gargoyles", null), true);
		assertEquals(1, repository.allStatistics(true).size());
	}

	@Test
	public void versionOneDataMigratesWithoutLosingDurationHistory()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		SlayerSpeedData current = new SlayerSpeedData();
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		statistics.addRun(run("old", null, 100), 50);
		current.getStatisticsByTaskKey().put(new TaskKey("Gargoyles", null).asStorageKey(), statistics);

		String versionOneJson = repository.encode(current).replace("\"schemaVersion\":4", "\"schemaVersion\":1");
		SlayerSpeedData versionOne = repository.decode(versionOneJson);

		assertEquals(true, versionOne.migrateToCurrentSchema());
		TaskStatistics migrated = versionOne.getStatisticsByTaskKey().get("gargoyles");
		assertEquals(SlayerSpeedData.CURRENT_SCHEMA_VERSION, versionOne.getSchemaVersion());
		assertEquals(migrated.getTotalActiveMillis(), migrated.getTotalCompletedTaskMillis());
		assertEquals(true, migrated.getRecentRuns().get(0).isFullTaskObserved());
	}

	@Test
	public void versionTwoDataMigratesRunManagementMetadata()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		SlayerSpeedData current = new SlayerSpeedData();
		TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
		TaskRun run = run("old", null, 100);
		statistics.addRun(run, 50);
		current.getStatisticsByTaskKey().put("gargoyles", statistics);

		String versionTwoJson = repository.encode(current).replace("\"schemaVersion\":4", "\"schemaVersion\":2");
		SlayerSpeedData versionTwo = repository.decode(versionTwoJson);

		assertEquals(true, versionTwo.migrateToCurrentSchema());
		TaskStatistics migrated = versionTwo.getStatisticsByTaskKey().get("gargoyles");
		assertEquals(true, migrated.isPersonalBest(migrated.getRecentRuns().get(0)));
	}

	@Test
	public void versionThreeDataMigratesToAddCannonFields()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		SlayerSpeedData current = new SlayerSpeedData();
		String versionThreeJson = repository.encode(current)
			.replace("\"schemaVersion\":4", "\"schemaVersion\":3");

		SlayerSpeedData versionThree = repository.decode(versionThreeJson);

		assertEquals(true, versionThree.migrateToCurrentSchema());
		assertEquals(SlayerSpeedData.CURRENT_SCHEMA_VERSION, versionThree.getSchemaVersion());
	}

	private static TaskRun run(String id, String location, int amount)
	{
		return new TaskRun(
			id, "Gargoyles", location, amount, 0, amount, amount,
			amount * 100, 0, 3_600_000L, 0, 1, 2, TaskRunStatus.COMPLETED);
	}
}
