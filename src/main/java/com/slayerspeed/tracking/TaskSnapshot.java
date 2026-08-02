package com.slayerspeed.tracking;

public final class TaskSnapshot
{
	private final String taskName;
	private final String taskLocation;
	private final int initialAmount;
	private final int remainingAmount;

	private TaskSnapshot(String taskName, String taskLocation, int initialAmount, int remainingAmount)
	{
		this.taskName = taskName;
		this.taskLocation = taskLocation;
		this.initialAmount = initialAmount;
		this.remainingAmount = remainingAmount;
	}

	public static TaskSnapshot active(String taskName, String taskLocation, int initialAmount, int remainingAmount)
	{
		return new TaskSnapshot(taskName, taskLocation, initialAmount, remainingAmount);
	}

	public static TaskSnapshot none()
	{
		return new TaskSnapshot(null, null, 0, 0);
	}

	public boolean hasTask()
	{
		return taskName != null && !taskName.trim().isEmpty() && remainingAmount > 0;
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

	public int getRemainingAmount()
	{
		return remainingAmount;
	}
}

