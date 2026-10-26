package com.slayerspeed.persistence;

import com.google.gson.Gson;
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
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class TaskHistoryRepository implements TaskHistoryStore
{
	static final String DATA_KEY = "taskHistoryV1";

	private final ProfileStorage storage;
    private String recoveryPayload;
    private boolean lastSaveFailed;
    private LoadState loadState = LoadState.EMPTY;

    public enum LoadState { EMPTY, LOADED, RECOVERY_REQUIRED }

    public LoadState getLoadState() { return loadState; }
    public String getRecoveryPayload() { return recoveryPayload; }
    public boolean isPersistenceAvailable()
    {
        return isLoadedProfileCurrent() && loadState != LoadState.RECOVERY_REQUIRED && !lastSaveFailed;
    }

    public String getStorageMessage()
    {
        if (loadState == LoadState.RECOVERY_REQUIRED)
        {
            return "Saved history could not be loaded. Tracking in memory only; open Help & data to copy the original records.";
        }
        if (lastSaveFailed) { return "History could not be saved. Tracking in memory; saving will retry at the next checkpoint."; }
        return isLoadedProfileCurrent() ? "" : "Tracking in memory only until an account profile is available.";
    }
	private final Gson gson;
	private SlayerSpeedData data = new SlayerSpeedData();
	private String loadedProfileKey;
    private long historyRevision;
    private long snapshotRevision = -1;
    private Collection<TaskStatistics> snapshot = java.util.Collections.emptyList();

    public long getHistoryRevision() { return historyRevision; }
    public String getProfileIdentity() { return isLoadedProfileCurrent() ? loadedProfileKey : null; }

    public Collection<TaskStatistics> snapshotStatistics()
    {
        if (snapshotRevision != historyRevision)
        {
            ArrayList<TaskStatistics> copy = new ArrayList<>();
            for (TaskStatistics stats : allStatistics(true))
            {
                copy.add(gson.fromJson(gson.toJson(stats), TaskStatistics.class));
            }
            snapshot = java.util.Collections.unmodifiableList(copy);
            snapshotRevision = historyRevision;
        }
        return snapshot;
    }

	@Inject
	public TaskHistoryRepository(ConfigManager configManager, Gson gson)
	{
        this(new ProfileStorage()
        {
            public String profileKey() { return configManager == null ? null : configManager.getRSProfileKey(); }
            public String read(String key) { return configManager.getRSProfileConfiguration(SlayerSpeedConfig.GROUP, key); }
            public void write(String key, String value) { configManager.setRSProfileConfiguration(SlayerSpeedConfig.GROUP, key, value); }
            public void remove(String key) { configManager.unsetRSProfileConfiguration(SlayerSpeedConfig.GROUP, key); }
        }, gson, true);
	}

    private TaskHistoryRepository(ProfileStorage storage, Gson gson, boolean ignored)
    {
        this.storage = storage;
        this.gson = gson;
    }

    static TaskHistoryRepository forStorage(ProfileStorage storage, Gson gson)
    {
        return new TaskHistoryRepository(storage, gson, true);
    }

    public void loadProfile()
    {
        historyRevision++;
        data = new SlayerSpeedData();
        recoveryPayload = null;
        loadState = LoadState.EMPTY;
        lastSaveFailed = false;
        loadedProfileKey = currentProfileKey();
        if (loadedProfileKey == null) { return; }
        String json = storage.read(DATA_KEY);
        if (json == null || json.trim().isEmpty()) { return; }
        try
        {
            SlayerSpeedData loaded = HistoryValidator.decode(json, gson);
            boolean migrated = loaded.getSchemaVersion() != SlayerSpeedData.CURRENT_SCHEMA_VERSION;
            if (migrated && !loaded.migrateToCurrentSchema())
            {
                throw new IllegalArgumentException("Unsupported history schema");
            }
            data = loaded;
            loadState = LoadState.LOADED;
            if (migrated)
            {
                storage.write("historyBackup", json);
                save();
            }
        }
        catch (RuntimeException ex)
        {
            data = new SlayerSpeedData();
            loadState = LoadState.RECOVERY_REQUIRED;
            recoveryPayload = json;
            log.warn("Slayer Task Speed history needs recovery; original data preserved");
        }
    }

	public void saveRun(TaskRun run, boolean separateByLocation, int maximumRecentRuns)
	{
        historyRevision++;
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
        historyRevision++;
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
        historyRevision++;
		TaskStatistics statistics = data.getStatisticsByTaskKey().get(run.taskKey(true).asStorageKey());
		if (statistics != null && statistics.setRunExcluded(run.getId(), excluded))
		{
			save();
		}
	}

	public void deleteRun(TaskRun run)
	{
        historyRevision++;
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
        historyRevision++;
        if (!isPersistenceAvailable()) { return; }
        if (isLoadedProfileCurrent())
        {
            try
            {
                storage.remove(DATA_KEY);
                data = new SlayerSpeedData();
            }
            catch (RuntimeException ex)
            {
                lastSaveFailed = true;
                log.warn("Slayer Task Speed reset failed; existing history retained");
            }
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
        String key = storage.profileKey();
        return key == null || key.isEmpty() ? null : key;
    }

	private boolean isLoadedProfileCurrent()
	{
		return loadedProfileKey != null && loadedProfileKey.equals(currentProfileKey());
	}

	private void save()
	{
        if (isLoadedProfileCurrent() && loadState != LoadState.RECOVERY_REQUIRED)
        {
            try
            {
                storage.write(DATA_KEY, encode(data));
                lastSaveFailed = false;
            }
            catch (RuntimeException ex)
            {
                lastSaveFailed = true;
                log.warn("Slayer Task Speed history write failed; retained in memory for retry");
            }
        }
	}
}
