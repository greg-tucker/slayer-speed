package com.slayerspeed.tracking;

import com.slayerspeed.model.TaskRun;

public final class TaskUpdate
{
	private static final TaskUpdate NONE = new TaskUpdate(0, false, null);

	private final int progressDelta;
	private final boolean taskStarted;
	private final TaskRun endedRun;

	public TaskUpdate(int progressDelta, boolean taskStarted, TaskRun endedRun)
	{
		this.progressDelta = progressDelta;
		this.taskStarted = taskStarted;
		this.endedRun = endedRun;
	}

	public static TaskUpdate none()
	{
		return NONE;
	}

	public int getProgressDelta()
	{
		return progressDelta;
	}

	public boolean isTaskStarted()
	{
		return taskStarted;
	}

	public TaskRun getEndedRun()
	{
		return endedRun;
	}
}

