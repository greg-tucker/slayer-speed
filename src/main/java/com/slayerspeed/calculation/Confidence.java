package com.slayerspeed.calculation;

public enum Confidence
{
	NO_DATA("No data"),
	LOW("Low"),
	MEDIUM("Medium"),
	HIGH("High");

	private final String displayName;

	Confidence(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public static Confidence fromSample(int completedTasks, int progressUnits)
	{
		if (completedTasks <= 0 || progressUnits <= 0)
		{
			return NO_DATA;
		}
		if (completedTasks < 2 || progressUnits < 30)
		{
			return LOW;
		}
		if (completedTasks < 5)
		{
			return MEDIUM;
		}
		return HIGH;
	}
}

