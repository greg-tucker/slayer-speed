package com.slayerspeed.ui;

import java.util.Objects;

public final class EncounterProfileOption
{
	public static final String AUTO_ID = "__auto__";

	private final String id;
	private final String label;

	public EncounterProfileOption(String id, String label)
	{
		this.id = id;
		this.label = label;
	}

	public String getId()
	{
		return id;
	}

	@Override
	public String toString()
	{
		return label;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof EncounterProfileOption
			&& id.equals(((EncounterProfileOption) other).id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
}
