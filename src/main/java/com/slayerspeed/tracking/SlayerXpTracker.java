package com.slayerspeed.tracking;

import java.util.OptionalInt;

public class SlayerXpTracker
{
	private Integer previousXp;

	public OptionalInt observe(int currentXp)
	{
		if (previousXp == null)
		{
			previousXp = currentXp;
			return OptionalInt.empty();
		}

		int delta = currentXp - previousXp;
		previousXp = currentXp;
		return delta > 0 ? OptionalInt.of(delta) : OptionalInt.empty();
	}

	public void reset()
	{
		previousXp = null;
	}

	public void setBaseline(int currentXp)
	{
		previousXp = currentXp;
	}

	public boolean hasBaseline()
	{
		return previousXp != null;
	}
}
