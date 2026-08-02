package com.slayerspeed.mortimer;

import java.awt.Rectangle;

public final class MortimerTaskOffer
{
	private final String taskName;
	private final int minimumAmount;
	private final int maximumAmount;
	private final Rectangle rowBounds;

	public MortimerTaskOffer(
		String taskName,
		int minimumAmount,
		int maximumAmount,
		Rectangle rowBounds)
	{
		this.taskName = taskName;
		this.minimumAmount = minimumAmount;
		this.maximumAmount = maximumAmount;
		this.rowBounds = rowBounds == null ? new Rectangle() : new Rectangle(rowBounds);
	}

	public String getTaskName()
	{
		return taskName;
	}

	public int getMinimumAmount()
	{
		return minimumAmount;
	}

	public int getMaximumAmount()
	{
		return maximumAmount;
	}

	public Rectangle getRowBounds()
	{
		return new Rectangle(rowBounds);
	}
}
