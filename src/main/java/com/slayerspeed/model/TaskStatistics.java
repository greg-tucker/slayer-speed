package com.slayerspeed.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskStatistics
{
	private int timingPolicy;
    public int getTimingPolicy() { return timingPolicy; }

    private String taskName;
	private String taskLocation;
	private String encounterProfileId;
	private String encounterProfileName;
	private int totalActualKills;
	private int totalTaskProgressUnits;
	private int totalSlayerXp;
	private int totalCannonballsUsed;
	private int totalCannonRunActualKills;
	private int totalCannonRunTaskProgressUnits;
	private long totalActiveMillis;
	private long totalCompletedTaskMillis;
	private int completedTaskCount;
	private long lastUpdatedAtMillis;
	private String personalBestRunId;
	private double personalBestEffectiveKph;
	private List<TaskRun> recentRuns = new ArrayList<>();

	public TaskStatistics()
	{
		// Gson constructor.
	}

	public TaskStatistics(String taskName, String taskLocation)
	{
		this(taskName, taskLocation, EncounterProfile.UNKNOWN_ID, EncounterProfile.UNKNOWN_DISPLAY_NAME);
	}

	public TaskStatistics(
		String taskName,
		String taskLocation,
		String encounterProfileId,
		String encounterProfileName)
	{
        this(taskName, taskLocation, encounterProfileId, encounterProfileName, 0);
    }

    public TaskStatistics(String taskName, String taskLocation, String encounterProfileId,
        String encounterProfileName, int timingPolicy)
    {
        this.timingPolicy = timingPolicy;
        this.taskName = taskName;
		this.taskLocation = taskLocation;
		this.encounterProfileId = encounterProfileId;
		this.encounterProfileName = encounterProfileName;
	}

	public void addRun(TaskRun run, int maximumRecentRuns)
	{
        if (timingPolicy != run.getTimingPolicy()) { throw new IllegalArgumentException("Timing policies cannot be pooled"); }
		if (isIncludedCompletedRun(run))
		{
			applyRunToAggregates(run, 1);
		}

		if (recentRuns == null)
		{
			recentRuns = new ArrayList<>();
		}
		recentRuns.add(0, run);
		while (recentRuns.size() > Math.max(1, maximumRecentRuns))
		{
			recentRuns.remove(recentRuns.size() - 1);
		}
		lastUpdatedAtMillis = run.getCompletedAtMillis();
		considerPersonalBest(run);
	}

    public void merge(TaskStatistics other)
    {
        if (timingPolicy != other.timingPolicy) { throw new IllegalArgumentException("Timing policies cannot be pooled"); }
		totalActualKills += other.totalActualKills;
		totalTaskProgressUnits += other.totalTaskProgressUnits;
		totalSlayerXp += other.totalSlayerXp;
		totalCannonballsUsed += other.totalCannonballsUsed;
		totalCannonRunActualKills += other.totalCannonRunActualKills;
		totalCannonRunTaskProgressUnits += other.totalCannonRunTaskProgressUnits;
		totalActiveMillis += other.totalActiveMillis;
		totalCompletedTaskMillis += other.totalCompletedTaskMillis;
		completedTaskCount += other.completedTaskCount;
		lastUpdatedAtMillis = Math.max(lastUpdatedAtMillis, other.lastUpdatedAtMillis);
		if (other.recentRuns != null)
		{
			if (recentRuns == null)
			{
				recentRuns = new ArrayList<>();
			}
			recentRuns.addAll(other.recentRuns);
			recentRuns.sort(Comparator.comparingLong(TaskRun::getCompletedAtMillis).reversed());
		}
		considerPersonalBest(other.personalBestRunId, other.personalBestEffectiveKph);
	}

	void migrateFromV1()
	{
		// Version 1 treated every completed record as a full task. Preserve its aggregate
		// duration semantics because partial/full provenance cannot be reconstructed.
		totalCompletedTaskMillis = totalActiveMillis;
		if (recentRuns != null)
		{
			for (TaskRun run : recentRuns)
			{
				if (run.getStatus() == TaskRunStatus.COMPLETED)
				{
					run.setFullTaskObserved(true);
				}
			}
		}
	}

	void migrateFromV2()
	{
		recalculatePersonalBest();
	}

	void migrateFromV4()
	{
		if (encounterProfileId == null)
		{
			encounterProfileId = EncounterProfile.UNKNOWN_ID;
		}
		if (encounterProfileName == null || encounterProfileName.trim().isEmpty())
		{
			encounterProfileName = EncounterProfile.UNKNOWN_DISPLAY_NAME;
		}
		if (recentRuns != null)
		{
			for (TaskRun run : recentRuns)
			{
				run.migrateFromV4();
			}
		}
	}

	public boolean setRunExcluded(String runId, boolean excluded)
	{
		TaskRun run = findRecentRun(runId);
		if (run == null
			|| run.getStatus() != TaskRunStatus.COMPLETED
			|| run.isExcludedFromAverages() == excluded)
		{
			return false;
		}
		boolean wasPersonalBest = isPersonalBest(run);
		applyRunToAggregates(run, excluded ? -1 : 1);
		run.setExcludedFromAverages(excluded);
		if (excluded && wasPersonalBest)
		{
			recalculatePersonalBest();
		}
		else if (!excluded)
		{
			considerPersonalBest(run);
		}
		return true;
	}

	public boolean deleteRun(String runId)
	{
		if (recentRuns == null)
		{
			return false;
		}
		for (int index = 0; index < recentRuns.size(); index++)
		{
			TaskRun run = recentRuns.get(index);
			if (run.getId().equals(runId))
			{
				if (isIncludedCompletedRun(run))
				{
					applyRunToAggregates(run, -1);
				}
				recentRuns.remove(index);
				if (isPersonalBest(run))
				{
					recalculatePersonalBest();
				}
				return true;
			}
		}
		return false;
	}

	public boolean isPersonalBest(TaskRun run)
	{
		return run != null && run.getId() != null && run.getId().equals(personalBestRunId);
	}

	private TaskRun findRecentRun(String runId)
	{
		if (recentRuns == null || runId == null)
		{
			return null;
		}
		for (TaskRun run : recentRuns)
		{
			if (runId.equals(run.getId()))
			{
				return run;
			}
		}
		return null;
	}

	private void applyRunToAggregates(TaskRun run, int direction)
	{
		totalActualKills = Math.max(0, totalActualKills + direction * run.getRateActualKills());
		totalTaskProgressUnits = Math.max(0, totalTaskProgressUnits + direction * run.getRateTaskProgressUnits());
		totalSlayerXp = Math.max(0, totalSlayerXp + direction * run.getRateSlayerXp());
		if (run.getCannonballsUsed() > 0)
		{
			totalCannonballsUsed = Math.max(0,
				totalCannonballsUsed + direction * run.getCannonballsUsed());
			totalCannonRunActualKills = Math.max(0,
				totalCannonRunActualKills + direction * run.getActualKills());
			totalCannonRunTaskProgressUnits = Math.max(0,
				totalCannonRunTaskProgressUnits + direction * run.getTaskProgressUnits());
		}
		totalActiveMillis = Math.max(0L, totalActiveMillis + direction * run.getActiveMillis());
		if (run.isFullTaskObserved())
		{
			totalCompletedTaskMillis = Math.max(0L,
				totalCompletedTaskMillis + direction * run.getActiveMillis());
			completedTaskCount = Math.max(0, completedTaskCount + direction);
		}
	}

	private void recalculatePersonalBest()
	{
		personalBestRunId = null;
		personalBestEffectiveKph = 0.0;
		if (recentRuns == null)
		{
			return;
		}
		for (TaskRun run : recentRuns)
		{
			if (isIncludedCompletedRun(run)
				&& run.isFullTaskObserved()
				&& run.getActiveMillis() > 0L)
			{
				considerPersonalBest(run);
			}
		}
	}

	private void considerPersonalBest(TaskRun run)
	{
		if (isIncludedCompletedRun(run) && run.isFullTaskObserved())
		{
			double effectiveKph = run.getActiveMillis() <= 0L
				? 0.0
				: run.getRateTaskProgressUnits() * 3_600_000.0 / run.getActiveMillis();
			considerPersonalBest(run.getId(), effectiveKph);
		}
	}

	private void considerPersonalBest(String runId, double effectiveKph)
	{
		if (runId != null && Double.isFinite(effectiveKph) && effectiveKph > 0.0
			&& (personalBestRunId == null || effectiveKph > personalBestEffectiveKph))
		{
			personalBestRunId = runId;
			personalBestEffectiveKph = effectiveKph;
		}
	}

	private static boolean isIncludedCompletedRun(TaskRun run)
	{
		return run.getStatus() == TaskRunStatus.COMPLETED && !run.isExcludedFromAverages();
	}

	public String getTaskName()
	{
		return taskName;
	}

	public String getTaskLocation()
	{
		return taskLocation;
	}

	public String getEncounterProfileId()
	{
		return encounterProfileId == null ? EncounterProfile.UNKNOWN_ID : encounterProfileId;
	}

	public String getEncounterProfileName()
	{
		return encounterProfileName == null || encounterProfileName.trim().isEmpty()
			? EncounterProfile.UNKNOWN_DISPLAY_NAME
			: encounterProfileName;
	}

	public int getTotalActualKills()
	{
		return totalActualKills;
	}

	public int getTotalTaskProgressUnits()
	{
		return totalTaskProgressUnits;
	}

	public int getTotalSlayerXp()
	{
		return totalSlayerXp;
	}

	public int getTotalCannonballsUsed()
	{
		return totalCannonballsUsed;
	}

	public int getTotalCannonRunActualKills()
	{
		return totalCannonRunActualKills;
	}

	public int getTotalCannonRunTaskProgressUnits()
	{
		return totalCannonRunTaskProgressUnits;
	}

	public long getTotalActiveMillis()
	{
		return totalActiveMillis;
	}

	public long getTotalCompletedTaskMillis()
	{
		return totalCompletedTaskMillis;
	}

	public int getCompletedTaskCount()
	{
		return completedTaskCount;
	}

	public long getLastUpdatedAtMillis()
	{
		return lastUpdatedAtMillis;
	}

	public List<TaskRun> getRecentRuns()
	{
		return recentRuns == null ? Collections.emptyList() : Collections.unmodifiableList(recentRuns);
	}
}
