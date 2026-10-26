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
    private String undoStatistics;
    private String undoKey;
    private String undoProfile;
    private long undoExpires;
    private long undoRevision;

    public enum ImportMode { REPLACE, MERGE, RECOVER_CHECKPOINT }

    public static final class ImportPreview
    {
        private final String profile;
        private final long revision;
        private final String encoded;
        private final ImportMode mode;
        private final String summary;
        private ImportPreview(String profile, long revision, String encoded, ImportMode mode, String summary)
        {
            this.profile = profile; this.revision = revision; this.encoded = encoded;
            this.mode = mode; this.summary = summary;
        }
        public String getSummary() { return summary; }
    }

    public String exportHistory(boolean checkpoint)
    {
        if (!isLoadedProfileCurrent()) { throw new IllegalStateException("No current account profile"); }
        if (loadState == LoadState.RECOVERY_REQUIRED)
        {
            return recoveryPayload;
        }
        return HistoryTransfer.exportData(data, gson, checkpoint);
    }

    public String getBackup()
    {
        if (!isLoadedProfileCurrent()) { throw new IllegalStateException("Account changed"); }
        String backup = storage.read("historyBackup");
        if (backup == null) { throw new IllegalStateException("No recovery backup is available"); }
        return backup;
    }

    public ImportPreview previewImport(String text, ImportMode mode, int limit)
    {
        if (!isLoadedProfileCurrent()) { throw new IllegalStateException("No current account profile"); }
        SlayerSpeedData imported = HistoryTransfer.parse(text, gson);
        if (mode == ImportMode.RECOVER_CHECKPOINT && data.getCheckpointedActiveTask() != null)
        {
            throw new IllegalStateException("Checkpoint recovery requires no active task");
        }
        if (mode == ImportMode.MERGE)
        {
            if (!isPersistenceAvailable()) { throw new IllegalStateException("Recover history before merging"); }
            imported = HistoryTransfer.merge(data, imported, gson, limit);
        }
        if (mode != ImportMode.RECOVER_CHECKPOINT) { imported.setCheckpointedActiveTask(null); }
        int count = imported.getStatisticsByTaskKey().values().stream().mapToInt(stats -> stats.getRecentRuns().size()).sum();
        String summary = mode + ": " + imported.getStatisticsByTaskKey().size() + " task profiles, " + count
            + " retained runs. Existing data will be backed up."
            + (mode == ImportMode.RECOVER_CHECKPOINT ? " Includes a compatible saved checkpoint if present."
                : " Current active assignment is preserved.");
        return new ImportPreview(loadedProfileKey, historyRevision, encode(imported), mode, summary);
    }

    public void applyImport(ImportPreview preview)
    {
        if (!isLoadedProfileCurrent() || !java.util.Objects.equals(preview.profile, loadedProfileKey)
            || preview.revision != historyRevision)
        {
            throw new IllegalStateException("Account or history changed. Preview the import again.");
        }
        if (preview.mode == ImportMode.RECOVER_CHECKPOINT && data.getCheckpointedActiveTask() != null)
        {
            throw new IllegalStateException("A task started since preview. Checkpoint was not replaced.");
        }
        SlayerSpeedData replacement = HistoryValidator.decode(preview.encoded, gson);
        if (preview.mode != ImportMode.RECOVER_CHECKPOINT)
        {
            replacement.setCheckpointedActiveTask(data.getCheckpointedActiveTask());
        }
        backupCurrent();
        String serialized = encode(replacement);
        storage.write(DATA_KEY, serialized); // Commit succeeds before replacing live repository state.
        data = replacement;
        lastSaveFailed = false;
        recoveryPayload = null;
        loadState = LoadState.LOADED;
        historyRevision++;
        undoStatistics = null;
    }

    private void backupCurrent()
    {
        if (!isLoadedProfileCurrent()) { throw new IllegalStateException("Account changed"); }
        String original = storage.read(DATA_KEY);
        if (original != null)
        {
            // Even an oversized unreadable original must remain recoverable. If storage refuses
            // the backup write, the transaction fails before replacing the original data.
            // Preserve unreadable data separately, never overwriting the last-good backup.
            storage.write(loadState == LoadState.RECOVERY_REQUIRED ? "historyUnreadableBackup" : "historyBackup", original);
        }
    }

    public boolean canUndoDeletion()
    {
        return undoStatistics != null && isPersistenceAvailable()
            && java.util.Objects.equals(undoProfile, loadedProfileKey)
            && undoRevision == historyRevision && System.currentTimeMillis() < undoExpires;
    }

    public void undoDeletion()
    {
        if (!canUndoDeletion()) { throw new IllegalStateException("Undo expired or history/account changed"); }
        data.getStatisticsByTaskKey().put(undoKey, gson.fromJson(undoStatistics, TaskStatistics.class));
        historyRevision++;
        undoStatistics = null;
        save();
    }
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
            return "Saved history could not be loaded. Tracking in memory only; open Help & data to recover.";
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
        undoStatistics = null;
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
				run.getEncounterProfileName(), run.getTimingPolicy()));
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
				&& statisticsKey.getEncounterProfileId().equals(key.getEncounterProfileId())
                && statisticsKey.getTimingPolicy() == key.getTimingPolicy())
			{
				if (combined == null)
				{
					combined = new TaskStatistics(
						statistics.getTaskName(),
						null,
						statistics.getEncounterProfileId(),
						statistics.getEncounterProfileName(), statistics.getTimingPolicy());
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
						statistics.getTaskName(), null, statistics.getEncounterProfileId(), statistics.getTimingPolicy());
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
							statistics.getEncounterProfileId(), statistics.getEncounterProfileName(), statistics.getTimingPolicy());
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
						statistics.getEncounterProfileId(), statistics.getEncounterProfileName(), statistics.getTimingPolicy()));
				combined.merge(statistics);
			}
			result = new ArrayList<>(combinedByTask.values());
		}
		result.sort(Comparator.comparingLong(TaskStatistics::getLastUpdatedAtMillis).reversed());
		return result;
	}

	public void deleteTask(TaskKey key, boolean separateByLocation)
    {
        if (isPersistenceAvailable()) { backupCurrent(); }
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
			statistics.getEncounterProfileId(), statistics.getTimingPolicy());
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
        String before = statistics == null ? null : gson.toJson(statistics);
        if (statistics != null && statistics.deleteRun(run.getId()))
		{
            undoStatistics = before;
            undoKey = key.asStorageKey();
            undoProfile = loadedProfileKey;
            undoRevision = historyRevision;
            undoExpires = System.currentTimeMillis() + 30000;
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
        backupCurrent();
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
