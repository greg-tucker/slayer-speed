package com.slayerspeed.model;

public class TaskRun
{
	private int timingPolicy;
    private int rateUnits;
    private int rateKills;
    private int rateXp;

    public int getTimingPolicy() { return timingPolicy; }
    public int getRateTaskProgressUnits() { return timingPolicy == 0 ? taskProgressUnits : rateUnits; }
    public int getRateActualKills() { return timingPolicy == 0 ? actualKills : rateKills; }
    public int getRateSlayerXp() { return timingPolicy == 0 ? getTotalSlayerXp() : rateXp; }
    void setTimingSample(int policy, int units, int kills, int xp, boolean partial)
    {
        timingPolicy = policy; rateUnits = units; rateKills = kills; rateXp = xp;
        if (policy != 0 && partial) { fullTaskObserved = false; }
    }

    private String id;
	private String taskName;
	private String taskLocation;
	private String encounterProfileId;
	private String encounterProfileName;
	private int initialAmount;
	private int endingAmount;
	private boolean fullTaskObserved;
	private int actualKills;
	private int taskProgressUnits;
	private int killSlayerXp;
	private int bonusSlayerXp;
	private int cannonballsUsed;
	private long activeMillis;
	private long setupMillis;
	private long startedAtMillis;
	private long completedAtMillis;
	private TaskRunStatus status;
	private boolean excludedFromAverages;

	public TaskRun()
	{
		// Gson constructor.
	}

	public TaskRun(
		String id,
		String taskName,
		String taskLocation,
		String encounterProfileId,
		String encounterProfileName,
		int initialAmount,
		int endingAmount,
		boolean fullTaskObserved,
		int actualKills,
		int taskProgressUnits,
		int killSlayerXp,
		int bonusSlayerXp,
		int cannonballsUsed,
		long activeMillis,
		long setupMillis,
		long startedAtMillis,
		long completedAtMillis,
		TaskRunStatus status)
	{
		this.id = id;
		this.taskName = taskName;
		this.taskLocation = taskLocation;
		this.encounterProfileId = encounterProfileId;
		this.encounterProfileName = encounterProfileName;
		this.initialAmount = initialAmount;
		this.endingAmount = endingAmount;
		this.fullTaskObserved = fullTaskObserved;
		this.actualKills = actualKills;
		this.taskProgressUnits = taskProgressUnits;
		this.killSlayerXp = killSlayerXp;
		this.bonusSlayerXp = bonusSlayerXp;
		this.cannonballsUsed = cannonballsUsed;
		this.activeMillis = activeMillis;
		this.setupMillis = setupMillis;
		this.startedAtMillis = startedAtMillis;
		this.completedAtMillis = completedAtMillis;
		this.status = status;
	}

	public TaskRun(
		String id,
		String taskName,
		String taskLocation,
		int initialAmount,
		int endingAmount,
		boolean fullTaskObserved,
		int actualKills,
		int taskProgressUnits,
		int killSlayerXp,
		int bonusSlayerXp,
		int cannonballsUsed,
		long activeMillis,
		long setupMillis,
		long startedAtMillis,
		long completedAtMillis,
		TaskRunStatus status)
	{
		this(id, taskName, taskLocation, EncounterProfile.UNKNOWN_ID,
			EncounterProfile.UNKNOWN_DISPLAY_NAME, initialAmount, endingAmount, fullTaskObserved,
			actualKills, taskProgressUnits, killSlayerXp, bonusSlayerXp, cannonballsUsed,
			activeMillis, setupMillis, startedAtMillis, completedAtMillis, status);
	}

	public TaskRun(
		String id,
		String taskName,
		String taskLocation,
		int initialAmount,
		int endingAmount,
		boolean fullTaskObserved,
		int actualKills,
		int taskProgressUnits,
		int killSlayerXp,
		int bonusSlayerXp,
		long activeMillis,
		long setupMillis,
		long startedAtMillis,
		long completedAtMillis,
		TaskRunStatus status)
	{
		this(id, taskName, taskLocation, initialAmount, endingAmount, fullTaskObserved,
			actualKills, taskProgressUnits, killSlayerXp, bonusSlayerXp, 0, activeMillis,
			setupMillis, startedAtMillis, completedAtMillis, status);
	}

	public TaskRun(
		String id,
		String taskName,
		String taskLocation,
		int initialAmount,
		int endingAmount,
		int actualKills,
		int taskProgressUnits,
		int killSlayerXp,
		int bonusSlayerXp,
		long activeMillis,
		long setupMillis,
		long startedAtMillis,
		long completedAtMillis,
		TaskRunStatus status)
	{
		this(id, taskName, taskLocation, initialAmount, endingAmount, true, actualKills,
			taskProgressUnits, killSlayerXp, bonusSlayerXp, 0, activeMillis, setupMillis,
			startedAtMillis, completedAtMillis, status);
	}

	public TaskKey taskKey(boolean separateByLocation)
	{
		return new TaskKey(
			taskName,
			separateByLocation ? taskLocation : null,
			getEncounterProfileId(), timingPolicy);
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

	public int getInitialAmount()
	{
		return initialAmount;
	}

	public int getEndingAmount()
	{
		return endingAmount;
	}

	public boolean isFullTaskObserved()
	{
		return fullTaskObserved;
	}

	void setFullTaskObserved(boolean fullTaskObserved)
	{
		this.fullTaskObserved = fullTaskObserved;
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

	public long getStartedAtMillis()
	{
		return startedAtMillis;
	}

	public long getCompletedAtMillis()
	{
		return completedAtMillis;
	}

	public TaskRunStatus getStatus()
	{
		return status;
	}

	public boolean isExcludedFromAverages()
	{
		return excludedFromAverages;
	}

	void setExcludedFromAverages(boolean excludedFromAverages)
	{
		this.excludedFromAverages = excludedFromAverages;
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
	}
}
