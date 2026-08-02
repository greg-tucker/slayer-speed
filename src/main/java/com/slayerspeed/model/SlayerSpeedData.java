package com.slayerspeed.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class SlayerSpeedData
{
	public static final int CURRENT_SCHEMA_VERSION = 5;

	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	private Map<String, TaskStatistics> statisticsByTaskKey = new LinkedHashMap<>();
	private ActiveTask checkpointedActiveTask;

	public int getSchemaVersion()
	{
		return schemaVersion;
	}

	public boolean migrateToCurrentSchema()
	{
		if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION)
		{
			return false;
		}
		boolean migrated = false;
		if (schemaVersion == 1)
		{
			for (TaskStatistics statistics : getStatisticsByTaskKey().values())
			{
				statistics.migrateFromV1();
			}
			if (checkpointedActiveTask != null)
			{
				checkpointedActiveTask.migrateFromV1();
			}
			schemaVersion = 2;
			migrated = true;
		}
		if (schemaVersion == 2)
		{
			for (TaskStatistics statistics : getStatisticsByTaskKey().values())
			{
				statistics.migrateFromV2();
			}
			schemaVersion = 3;
			migrated = true;
		}
		if (schemaVersion == 3)
		{
			// Cannon fields are additive and safely default to zero for existing histories.
			schemaVersion = 4;
			migrated = true;
		}
		if (schemaVersion == 4)
		{
			for (TaskStatistics statistics : getStatisticsByTaskKey().values())
			{
				statistics.migrateFromV4();
			}
			if (checkpointedActiveTask != null)
			{
				checkpointedActiveTask.migrateFromV4();
			}
			schemaVersion = CURRENT_SCHEMA_VERSION;
			migrated = true;
		}
		return migrated;
	}

	public Map<String, TaskStatistics> getStatisticsByTaskKey()
	{
		if (statisticsByTaskKey == null)
		{
			statisticsByTaskKey = new LinkedHashMap<>();
		}
		return statisticsByTaskKey;
	}

	public ActiveTask getCheckpointedActiveTask()
	{
		return checkpointedActiveTask;
	}

	public void setCheckpointedActiveTask(ActiveTask checkpointedActiveTask)
	{
		this.checkpointedActiveTask = checkpointedActiveTask;
	}
}
