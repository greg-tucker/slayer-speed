package com.slayerspeed.model;

import java.util.Objects;

public final class EncounterProfile
{
	public static final String UNKNOWN_ID = "";
	public static final String UNKNOWN_DISPLAY_NAME = "Older mixed data";

	private final String id;
	private final String displayName;

	public EncounterProfile(String id, String displayName)
	{
		this.id = id == null ? UNKNOWN_ID : id;
		this.displayName = displayName == null || displayName.trim().isEmpty()
			? UNKNOWN_DISPLAY_NAME
			: displayName.trim();
	}

	public String getId()
	{
		return id;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean isKnown()
	{
		return !id.isEmpty();
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof EncounterProfile))
		{
			return false;
		}
		EncounterProfile profile = (EncounterProfile) other;
		return id.equals(profile.id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
}
