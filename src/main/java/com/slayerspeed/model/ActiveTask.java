package com.slayerspeed.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ActiveTask
{
	private int timingPolicy;
    private int rateUnits;
    private int rateKills;
    private int rateXp;
    private int measuredUnits;
    private int measuredKills;
    private int measuredXp;
    private long segmentAnchorMillis;
    private boolean manualPaused;
    private boolean suspended;
    private boolean timingPartial;

    public int getTimingPolicy() { return timingPolicy; }
    public void setTimingPolicy(int policy) { timingPolicy = policy; }
    public int getRateTaskProgressUnits() { return timingPolicy == 0 ? taskProgressUnits : rateUnits; }
    public int getRateActualKills() { return timingPolicy == 0 ? actualKills : rateKills; }
    public int getRateSlayerXp() { return timingPolicy == 0 ? getTotalSlayerXp() : rateXp; }
    public boolean isManualPaused() { return manualPaused; }
    public boolean isSuspended() { return suspended; }
    public void markLegacyActivity() { suspended = false; }

    public void suspendTiming()
    {
        suspended = true;
        lastActivityAtMillis = 0;
    }

    public void pauseManually()
    {
        if (timingPolicy == 0) { return; }
        manualPaused = true;
        lastActivityAtMillis = 0;
        timingPartial = true;
    }

    public void resumeManually(long now)
    {
        if (timingPolicy == 0) { return; }
        manualPaused = false;
        suspended = false;
        anchorSegment(now);
    }

    private void anchorSegment(long now)
    {
        segmentAnchorMillis = now;
        lastActivityAtMillis = now;
        measuredUnits = taskProgressUnits;
        measuredKills = actualKills;
        measuredXp = getTotalSlayerXp();
    }

    public void recordSegmentActivity(long now, int idleTimeoutMinutes)
    {
        if (lastActivityAtMillis <= 0 || manualPaused || suspended)
        {
            if (setupMillis == 0) { setupMillis = Math.max(0, now - startedAtMillis); }
            manualPaused = false;
            suspended = false;
            // No combat-start evidence exists for the first interval. Do not claim a full duration/PB.
            timingPartial = true;
            anchorSegment(now);
            return;
        }
        if (now == segmentAnchorMillis)
        {
            // All evidence belonging to the untimed anchor tick is excluded from rate numerators.
            anchorSegment(now);
            return;
        }
        activeMillis += Math.min(Math.max(0, now - lastActivityAtMillis), Math.max(1, idleTimeoutMinutes) * 60000L);
        rateUnits += Math.max(0, taskProgressUnits - measuredUnits);
        rateKills += Math.max(0, actualKills - measuredKills);
        rateXp += Math.max(0, getTotalSlayerXp() - measuredXp);
        measuredUnits = taskProgressUnits;
        measuredKills = actualKills;
        measuredXp = getTotalSlayerXp();
        lastActivityAtMillis = now;
    }

    private String id;
	private String taskName;
	private String taskLocation;
	private int initialAmount;
	private int observedStartAmount;
	private boolean fullTaskObserved;
	private int lastRemainingAmount;
	private int actualKills;
	private int taskProgressUnits;
	private int killSlayerXp;
	private int bonusSlayerXp;
	private int cannonballsUsed;
	private long activeMillis;
	private long setupMillis;
	private long startedAtMillis;
	private long lastActivityAtMillis;
	private long lastCheckpointAtMillis;
	private long completionConfirmedAtMillis;
	private String manualEncounterProfileId;
	private String manualEncounterProfileName;
	private String detectedEncounterProfileId;
	private String detectedEncounterProfileName;
	private Map<String, Integer> encounterKillsByProfile = new LinkedHashMap<>();
	private Map<String, String> encounterNamesByProfile = new LinkedHashMap<>();

	public ActiveTask()
	{
		// Gson constructor.
	}

	public ActiveTask(String taskName, String taskLocation, int initialAmount, int remainingAmount, long startedAtMillis)
	{
		this.id = UUID.randomUUID().toString();
		this.taskName = taskName;
		this.taskLocation = taskLocation;
		this.initialAmount = initialAmount;
		this.observedStartAmount = remainingAmount;
		this.fullTaskObserved = initialAmount > 0 && remainingAmount == initialAmount;
		this.lastRemainingAmount = remainingAmount;
		this.startedAtMillis = startedAtMillis;
	}

	public TaskKey taskKey(boolean separateByLocation)
	{
		return new TaskKey(taskName, separateByLocation ? taskLocation : null, null, timingPolicy);
	}

	public void addProgressUnits(int units)
	{
		if (units > 0)
		{
			taskProgressUnits += units;
		}
	}

	public void addActualKills(int kills)
	{
		if (kills > 0)
		{
			actualKills += kills;
		}
	}

	public void observeEncounter(EncounterProfile profile)
	{
		if (profile == null || !profile.isKnown())
		{
			return;
		}
		if (encounterKillsByProfile == null)
		{
			encounterKillsByProfile = new LinkedHashMap<>();
		}
		if (encounterNamesByProfile == null)
		{
			encounterNamesByProfile = new LinkedHashMap<>();
		}
		int count = encounterKillsByProfile.getOrDefault(profile.getId(), 0) + 1;
		encounterKillsByProfile.put(profile.getId(), count);
		encounterNamesByProfile.put(profile.getId(), profile.getDisplayName());
		int selectedCount = detectedEncounterProfileId == null
			? 0
			: encounterKillsByProfile.getOrDefault(detectedEncounterProfileId, 0);
		if (detectedEncounterProfileId == null || count >= selectedCount)
		{
			detectedEncounterProfileId = profile.getId();
			detectedEncounterProfileName = profile.getDisplayName();
		}
	}

	public void setManualEncounterProfile(EncounterProfile profile)
	{
		manualEncounterProfileId = profile == null || !profile.isKnown() ? null : profile.getId();
		manualEncounterProfileName = profile == null || !profile.isKnown() ? null : profile.getDisplayName();
	}

	public boolean hasManualEncounterProfile()
	{
		return manualEncounterProfileId != null && !manualEncounterProfileId.isEmpty();
	}

	public String getManualEncounterProfileId()
	{
		return manualEncounterProfileId;
	}

	public String getManualEncounterProfileName()
	{
		return manualEncounterProfileName;
	}

	public String getDetectedEncounterProfileId()
	{
		return detectedEncounterProfileId;
	}

	public String getDetectedEncounterProfileName()
	{
		return detectedEncounterProfileName;
	}

	public String getRecordingEncounterProfileId()
	{
		return hasManualEncounterProfile()
			? manualEncounterProfileId
			: detectedEncounterProfileId;
	}

	public String getRecordingEncounterProfileName()
	{
		return hasManualEncounterProfile()
			? manualEncounterProfileName
			: detectedEncounterProfileName;
	}

	public void addKillSlayerXp(int xp)
	{
		if (xp > 0)
		{
			killSlayerXp += xp;
		}
	}

	public void addBonusSlayerXp(int xp)
	{
		if (xp > 0)
		{
			bonusSlayerXp += xp;
		}
	}

	public void addCannonballsUsed(int cannonballs)
	{
		if (cannonballs > 0)
		{
			cannonballsUsed += cannonballs;
		}
	}

	public void addActiveMillis(long millis)
	{
		if (millis > 0)
		{
			activeMillis += millis;
		}
	}

	public TaskRun finish(TaskRunStatus status, long completedAtMillis)
	{
        TaskRun result = new TaskRun(
            id,
			taskName,
			taskLocation,
			getRecordingEncounterProfileId(),
			getRecordingEncounterProfileName(),
			initialAmount,
			lastRemainingAmount,
			fullTaskObserved,
			actualKills,
			taskProgressUnits,
			killSlayerXp,
			bonusSlayerXp,
			cannonballsUsed,
			activeMillis,
			setupMillis,
			startedAtMillis,
			completedAtMillis,
            status);
        result.setTimingSample(timingPolicy, rateUnits, rateKills, rateXp, timingPartial);
        return result;
    }

	public String getId()
	{
		return id;
	}

	public String getTaskName()
	{
		return taskName;
	}

	public String getTaskLocation()
	{
		return taskLocation;
	}

	public int getInitialAmount()
	{
		return initialAmount;
	}

	public int getObservedStartAmount()
	{
		return observedStartAmount;
	}

	public boolean isFullTaskObserved()
	{
		return fullTaskObserved;
	}

	public int getLastRemainingAmount()
	{
		return lastRemainingAmount;
	}

	public void setLastRemainingAmount(int lastRemainingAmount)
	{
		this.lastRemainingAmount = lastRemainingAmount;
	}

	public int getActualKills()
	{
		return actualKills;
	}

	public int getTaskProgressUnits()
	{
		return taskProgressUnits;
	}

	public int getKillSlayerXp()
	{
		return killSlayerXp;
	}

	public int getBonusSlayerXp()
	{
		return bonusSlayerXp;
	}

	public int getTotalSlayerXp()
	{
		return killSlayerXp + bonusSlayerXp;
	}

	public int getCannonballsUsed()
	{
		return cannonballsUsed;
	}

	public long getActiveMillis()
	{
		return activeMillis;
	}

	public long getSetupMillis()
	{
		return setupMillis;
	}

	public void setSetupMillis(long setupMillis)
	{
		this.setupMillis = Math.max(0L, setupMillis);
	}

	public long getStartedAtMillis()
	{
		return startedAtMillis;
	}

	public long getLastActivityAtMillis()
	{
		return lastActivityAtMillis;
	}

	public void setLastActivityAtMillis(long lastActivityAtMillis)
	{
		this.lastActivityAtMillis = lastActivityAtMillis;
	}

	public long getLastCheckpointAtMillis()
	{
		return lastCheckpointAtMillis;
	}

	public void setLastCheckpointAtMillis(long lastCheckpointAtMillis)
	{
		this.lastCheckpointAtMillis = lastCheckpointAtMillis;
	}

	public long getCompletionConfirmedAtMillis()
	{
		return completionConfirmedAtMillis;
	}

	public void setCompletionConfirmedAtMillis(long completionConfirmedAtMillis)
	{
		this.completionConfirmedAtMillis = completionConfirmedAtMillis;
	}

	void migrateFromV1()
	{
		fullTaskObserved = initialAmount > 0 && observedStartAmount == initialAmount;
	}

	void migrateFromV4()
	{
		if (encounterKillsByProfile == null)
		{
			encounterKillsByProfile = new LinkedHashMap<>();
		}
		if (encounterNamesByProfile == null)
		{
			encounterNamesByProfile = new LinkedHashMap<>();
		}
	}
}
