package com.slayerspeed.tracking;

import com.slayerspeed.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SegmentedTimingTest
{
    private final ActiveTimeTracker clock = new ActiveTimeTracker();
    private ActiveTask task(int policy)
    {
        ActiveTask task = new ActiveTask("Gargoyles", null, 100, 100, 100);
        task.setTimingPolicy(policy);
        return task;
    }
    private void activity(ActiveTask task, int units, long now)
    {
        task.addProgressUnits(units);
        task.addActualKills(units);
        task.addKillSlayerXp(units * 100);
        clock.recordActivity(task, now, 5);
    }

    @Test public void excludesUntimedAnchorAndResumesWithoutCountingGap()
    {
        ActiveTask task = task(1);
        activity(task, 1, 1000);
        task.addKillSlayerXp(25);
        clock.recordActivity(task, 1000, 5);
        activity(task, 2, 61000);
        assertEquals(2, task.getRateTaskProgressUnits());
        assertEquals(200, task.getRateSlayerXp());
        assertEquals(60000, task.getActiveMillis());
        task.pauseManually();
        activity(task, 1, 601000);
        assertFalse(task.isManualPaused());
        activity(task, 1, 661000);
        TaskRun run = task.finish(TaskRunStatus.COMPLETED, 662000);
        assertEquals(5, run.getTaskProgressUnits());
        assertEquals(3, run.getRateTaskProgressUnits());
        assertEquals(120000, run.getActiveMillis());
        assertEquals(1, run.getTimingPolicy());
        assertFalse(run.isFullTaskObserved());
    }

    @Test public void explicitResumeAnchorsAndIdleGapRemainsCapped()
    {
        ActiveTask task = task(1);
        task.pauseManually();
        task.resumeManually(1000);
        activity(task, 1, 121000);
        assertEquals(120000, task.getActiveMillis());
        activity(task, 1, 721000);
        assertEquals(420000, task.getActiveMillis());
        clock.pause(task);
        activity(task, 1, 1000000);
        assertFalse(task.isSuspended());
        assertEquals(2, task.getRateTaskProgressUnits());
        assertEquals(420000, task.getActiveMillis());
    }

    @Test public void legacyResumesAndCohortsCannotBePooled()
    {
        ActiveTask legacy = task(0);
        activity(legacy, 1, 1000);
        clock.pause(legacy);
        activity(legacy, 1, 61000);
        assertFalse(legacy.isSuspended());
        assertEquals(2, legacy.getRateTaskProgressUnits());
        assertNotEquals(legacy.taskKey(false), task(1).taskKey(false));
        TaskStatistics statistics = new TaskStatistics("Gargoyles", null);
        try
        {
            statistics.addRun(task(1).finish(TaskRunStatus.COMPLETED, 70000), 20);
            fail("Mixed timing must be rejected");
        }
        catch (IllegalArgumentException expected) { }
    }
}
