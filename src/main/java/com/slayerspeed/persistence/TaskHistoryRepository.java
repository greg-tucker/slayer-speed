package com.slayerspeed.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.SlayerSpeedData;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskStatistics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class TaskHistoryRepository implements TaskHistoryStore
{
	private static final String DATA_KEY = "taskHistoryV1";

	private final ConfigManager configManager;
	private final Gson gson;
	private SlayerSpeedData data = new SlayerSpeedData();
	private String loadedProfileKey;

	@Inject
	public TaskHistoryRepository(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void loadProfile()
	{
		data = new SlayerSpeedData();
		loadedProfileKey = currentProfileKey();
		if (loadedProfileKey == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(SlayerSpeedConfig.GROUP, DATA_KEY);
		if (json == null || json.trim().isEmpty())
		{
			return;
		}

		try
		{
			SlayerSpeedData loaded = decode(json);
			if (loaded != null && loaded.getSchemaVersion() == SlayerSpeedData.CURRENT_SCHEMA_VERSION)
			{
				data = loaded;
			}
			else if (loaded != null && loaded.migrateToCurrentSchema())
			{
				data = loaded;
				save();
			}
			else
			{
				log.warn("Ignoring unsupported SlayerSpeed data schema");
			}
		}
		catch (JsonParseException | IllegalStateException ex)
		{
			log.warn("Unable to read SlayerSpeed task history; starting with empty history", ex);
		}
	}

	public void saveRun(TaskRun run, boolean separateByLocation, int maximumRecentRuns)
	{
		// Always retain the most specific key. Whether locations are combined is a read-time choice,
		// so changing the display setting cannot orphan previously recorded samples.
		TaskKey key = run.taskKey(true);
		TaskStatistics statistics = data.getStatisticsByTaskKey().computeIfAbsent(
			key.asStorageKey(),
			ignored -> new TaskStatistics(
				run.getTaskName(),
				run.getTaskLocation(),
				run.getEncounterProfileId(),
				run.getEncounterProfileName()));
		statistics.addRun(run, maximumRecentRuns);
		data.setCheckpointedActiveTask(null);
		save();
	}

	public void saveCheckpoint(ActiveTask activeTask)
	{
		data.setCheckpointedActiveTask(activeTask);
		save();
	}

	public ActiveTask getCheckpoint()
	{
		return data.getCheckpointedActiveTask();
	}

	public void clearCheckpoint()
	{
		if (data.getCheckpointedActiveTask() != null)
		{
			data.setCheckpointedActiveTask(null);
			save();
		}
	}

	public TaskStatistics find(TaskKey key, boolean separateByLocation)
	{
		if (separateByLocation)
		{
			return data.getStatisticsByTaskKey().get(key.asStorageKey());
		}

		TaskStatistics combined = null;
		for (TaskStatistics statistics : data.getStatisticsByTaskKey().values())
		{
			TaskKey statisticsKey = taskKey(statistics, false);
			if (statisticsKey.getTaskName().equals(key.getTaskName())
				&& statisticsKey.getEncounterProfileId().equals(key.getEncounterProfileId()))
			{
				if (combined == null)
				{
					combined = new TaskStatistics(
						statistics.getTaskName(),
						null,
						statistics.getEncounterProfileId(),
						statistics.getEncounterProfileName());
				}
				combined.merge(statistics);
			}
		}
		return combined;
	}

	public Collection<TaskStatistics> profilesForTask(TaskKey assignment, boolean separateByLocation)
	{
		ArrayList<TaskStatistics> result = new ArrayList<>();
		for (TaskStatistics statistics : data.getStatisticsByTaskKey().values())
		{
			TaskKey statisticsKey = taskKey(statistics, separateByLocation);
			if (statisticsKey.getTaskName().equals(assignment.getTaskName())
				&& (!separateByLocation
					|| statisticsKey.getTaskLocation().equals(assignment.getTaskLocation())))
			{
				if (separateByLocation)
				{
					result.add(statistics);
				}
				else
				{
					TaskKey profileKey = new TaskKey(
						statistics.getTaskName(), null, statistics.getEncounterProfileId());
					TaskStatistics combined = null;
					for (TaskStatistics existing : result)
					{
						if (taskKey(existing, false).equals(profileKey))
						{
							combined = existing;
							break;
						}
					}
					if (combined == null)
					{
						combined = new TaskStatistics(
							statistics.getTaskName(), null,
							statistics.getEncounterProfileId(), statistics.getEncounterProfileName());
						result.add(combined);
					}
					combined.merge(statistics);
				}
			}
		}
		result.sort(Comparator.comparingLong(TaskStatistics::getLastUpdatedAtMillis).reversed());
		return result;
	}

	public Collection<TaskStatistics> allStatistics(boolean separateByLocation)
	{
		Collection<TaskStatistics> values = data.getStatisticsByTaskKey().values();
		ArrayList<TaskStatistics> result;
		if (separateByLocation)
		{
			result = new ArrayList<>(values);
		}
		else
		{
			Map<String, TaskStatistics> combinedByTask = new LinkedHashMap<>();
			for (TaskStatistics statistics : values)
			{
				TaskKey taskKey = taskKey(statistics, false);
				TaskStatistics combined = combinedByTask.computeIfAbsent(
					taskKey.asStorageKey(), ignored -> new TaskStatistics(
						statistics.getTaskName(), null,
						statistics.getEncounterProfileId(), statistics.getEncounterProfileName()));
				combined.merge(statistics);
			}
			result = new ArrayList<>(combinedByTask.values());
		}
		result.sort(Comparator.comparingLong(TaskStatistics::getLastUpdatedAtMillis).reversed());
		return result;
	}

	public void deleteTask(TaskKey key, boolean separateByLocation)
	{
		boolean changed;
		if (!separateByLocation)
		{
			changed = data.getStatisticsByTaskKey().entrySet().removeIf(entry ->
				taskKey(entry.getValue(), false).getTaskName().equals(key.getTaskName())
					&& (!key.hasEncounterProfile()
						|| taskKey(entry.getValue(), false).getEncounterProfileId()
							.equals(key.getEncounterProfileId())));
		}
		else if (!key.hasEncounterProfile())
		{
			changed = data.getStatisticsByTaskKey().entrySet().removeIf(entry ->
			{
				TaskKey statisticsKey = taskKey(entry.getValue(), true);
				return statisticsKey.getTaskName().equals(key.getTaskName())
					&& statisticsKey.getTaskLocation().equals(key.getTaskLocation());
			});
		}
		else
		{
			changed = data.getStatisticsByTaskKey().remove(key.asStorageKey()) != null;
		}
		if (changed)
		{
			save();
		}
	}

	private static TaskKey taskKey(TaskStatistics statistics, boolean separateByLocation)
	{
		return new TaskKey(
			statistics.getTaskName(),
			separateByLocation ? statistics.getTaskLocation() : null,
			statistics.getEncounterProfileId());
	}

	public void setRunExcluded(TaskRun run, boolean excluded)
	{
		TaskStatistics statistics = data.getStatisticsByTaskKey().get(run.taskKey(true).asStorageKey());
		if (statistics != null && statistics.setRunExcluded(run.getId(), excluded))
		{
			save();
		}
	}

	public void deleteRun(TaskRun run)
	{
		TaskKey key = run.taskKey(true);
		TaskStatistics statistics = data.getStatisticsByTaskKey().get(key.asStorageKey());
		if (statistics != null && statistics.deleteRun(run.getId()))
		{
			if (statistics.getRecentRuns().isEmpty()
				&& statistics.getTotalTaskProgressUnits() == 0
				&& statistics.getCompletedTaskCount() == 0)
			{
				data.getStatisticsByTaskKey().remove(key.asStorageKey());
			}
			save();
		}
	}

	public void deleteAll()
	{
		data = new SlayerSpeedData();
		if (isLoadedProfileCurrent())
		{
			configManager.unsetRSProfileConfiguration(SlayerSpeedConfig.GROUP, DATA_KEY);
		}
	}

	String encode(SlayerSpeedData value)
	{
		return gson.toJson(value);
	}

	SlayerSpeedData decode(String json)
	{
		return gson.fromJson(json, SlayerSpeedData.class);
	}

	private String currentProfileKey()
	{
		if (configManager == null)
		{
			return null;
		}
		String profileKey = configManager.getRSProfileKey();
		return profileKey == null || profileKey.isEmpty() ? null : profileKey;
	}

	private boolean isLoadedProfileCurrent()
	{
		return loadedProfileKey != null && loadedProfileKey.equals(currentProfileKey());
	}

	private void save()
	{
		if (isLoadedProfileCurrent())
		{
			configManager.setRSProfileConfiguration(SlayerSpeedConfig.GROUP, DATA_KEY, encode(data));
		}
	}
}
