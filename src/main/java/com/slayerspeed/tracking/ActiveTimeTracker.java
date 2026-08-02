package com.slayerspeed.tracking;

import com.slayerspeed.model.ActiveTask;

public class ActiveTimeTracker
{
	public void recordActivity(ActiveTask task, long nowMillis, int idleTimeoutMinutes)
	{
		long previous = task.getLastActivityAtMillis();
		if (previous <= 0)
		{
			if (task.getSetupMillis() == 0)
			{
				task.setSetupMillis(Math.max(0L, nowMillis - task.getStartedAtMillis()));
			}
			task.setLastActivityAtMillis(nowMillis);
			return;
		}

		long gap = Math.max(0L, nowMillis - previous);
		long cap = Math.max(1, idleTimeoutMinutes) * 60_000L;
		task.addActiveMillis(Math.min(gap, cap));
		task.setLastActivityAtMillis(nowMillis);
	}

	public void pause(ActiveTask task)
	{
		if (task != null)
		{
			task.setLastActivityAtMillis(0L);
		}
	}
}

