package com.slayerspeed.tracking;

import com.slayerspeed.model.ActiveTask;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActiveTimeTrackerTest
{
	@Test
	public void capsLongActivityGapsAndPausesCleanly()
	{
		ActiveTask task = new ActiveTask("Gargoyles", null, 100, 100, 1_000L);
		ActiveTimeTracker tracker = new ActiveTimeTracker();

		tracker.recordActivity(task, 11_000L, 5);
		tracker.recordActivity(task, 71_000L, 5);
		tracker.recordActivity(task, 971_000L, 5);

		assertEquals(360_000L, task.getActiveMillis());
		assertEquals(10_000L, task.getSetupMillis());

		tracker.pause(task);
		tracker.recordActivity(task, 2_000_000L, 5);
		assertEquals(360_000L, task.getActiveMillis());
	}
}

