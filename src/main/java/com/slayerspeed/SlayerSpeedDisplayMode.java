package com.slayerspeed;

public enum SlayerSpeedDisplayMode
{
	SIMPLE("Simple"),
	DETAILED("Detailed");

	private final String displayName;

	SlayerSpeedDisplayMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
