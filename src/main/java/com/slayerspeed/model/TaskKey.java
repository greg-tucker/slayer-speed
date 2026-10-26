package com.slayerspeed.model;

import java.util.Locale;
import java.util.Objects;

public final class TaskKey
{
	private final String taskName;
	private final String taskLocation;
	private final String encounterProfileId;

	public TaskKey(String taskName, String taskLocation)
	{
		this(taskName, taskLocation, EncounterProfile.UNKNOWN_ID);
	}

	public TaskKey(String taskName, String taskLocation, String encounterProfileId)
	{
		this.taskName = normalizeRequired(taskName);
		this.taskLocation = normalizeOptional(taskLocation);
		this.encounterProfileId = normalizeOptional(encounterProfileId);
	}

	private static String normalizeRequired(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			throw new IllegalArgumentException("Task name is required");
		}
		return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
	}

	private static String normalizeOptional(String value)
	{
		return value == null || value.trim().isEmpty()
			? ""
			: value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
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
		return encounterProfileId;
	}

	public boolean hasEncounterProfile()
	{
		return !encounterProfileId.isEmpty();
	}

	public String asStorageKey()
	{
		String assignment = taskLocation.isEmpty() ? taskName : taskName + "|" + taskLocation;
		return encounterProfileId.isEmpty()
			? assignment
			: assignment + "|target:" + encounterProfileId;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof TaskKey))
		{
			return false;
		}
		TaskKey taskKey = (TaskKey) other;
		return taskName.equals(taskKey.taskName)
			&& taskLocation.equals(taskKey.taskLocation)
			&& encounterProfileId.equals(taskKey.encounterProfileId);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(taskName, taskLocation, encounterProfileId);
	}

	@Override
	public String toString()
	{
		return asStorageKey();
	}
}
