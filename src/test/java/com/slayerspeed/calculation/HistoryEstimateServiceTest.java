package com.slayerspeed.calculation;

import com.slayerspeed.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryEstimateServiceTest
{
    private final HistoryEstimateService service = new HistoryEstimateService();
    private TaskRun run(String id, int units, long time, long ended, TaskRunStatus status, boolean full, int balls)
    {
        return new TaskRun(id, "Gargoyles", null, "", "Mixed", units, 0, full,
            units, units, units * 100, 0, balls, time, 0, 0, ended, status);
    }

    @Test public void filtersBeforeLimitingAndDoesNotBackfillFromLifetime()
    {
        TaskStatistics history = new TaskStatistics("Gargoyles", null);
        history.addRun(run("old", 900, 900000, 1, TaskRunStatus.COMPLETED, true, 0), 2);
        history.addRun(run("partial", 20, 60000, 2, TaskRunStatus.COMPLETED, false, 40), 2);
        history.addRun(run("skip", 90, 60000, 3, TaskRunStatus.SKIPPED, true, 900), 2);
        TaskStatistics selected = service.select(history, EstimateWindow.RECENT_5);
        assertEquals(1, selected.getRecentRuns().size());
        assertEquals(20, selected.getTotalTaskProgressUnits());
        assertEquals(0, selected.getCompletedTaskCount());
        assertEquals(40, selected.getTotalCannonballsUsed());
        assertEquals(20, selected.getTotalCannonRunTaskProgressUnits());
        assertEquals("Legacy recent 1/5 eligible runs", service.scope(selected, EstimateWindow.RECENT_5, 0));
        assertSame(history, service.select(history, EstimateWindow.LIFETIME));
        assertEquals(920, history.getTotalTaskProgressUnits());
    }

    @Test public void fallbackChangesScopeAndNeverBlendsCohorts()
    {
        TaskStatistics legacy = new TaskStatistics("Gargoyles", null);
        legacy.addRun(run("old", 100, 3600000, 1, TaskRunStatus.COMPLETED, true, 0), 50);
        HistoryEstimateService.Selection result = service.selectWithFallback(null, legacy, EstimateWindow.RECENT_5);
        assertTrue(result.legacyFallback);
        assertEquals(EstimateWindow.LIFETIME, result.window);
        assertEquals("Legacy lifetime history (fallback)", result.getScope());
        TaskEstimate estimate = new TaskEstimateService().calculate(100, 1000, 60000,
            result.statistics.getTotalTaskProgressUnits(), result.statistics.getTotalActiveMillis(), 10,
            !result.legacyFallback, 1, result.getScope());
        assertEquals(TaskEstimate.Source.HISTORY, estimate.getSource());
        assertEquals(3600000, estimate.getEtaMillis().getAsDouble(), 0.01);
    }

    @Test public void sortsGloballyAndRejectsExcludedAndUntimedRuns()
    {
        TaskStatistics history = new TaskStatistics("Gargoyles", null);
        for (int i = 8; i >= 1; i--)
        {
            history.addRun(run("run" + i, i, 60000, i, TaskRunStatus.COMPLETED, true, 0), 50);
        }
        history.setRunExcluded("run8", true);
        history.addRun(run("untimed", 999, 0, 100, TaskRunStatus.COMPLETED, true, 0), 50);
        TaskStatistics selected = service.select(history, EstimateWindow.RECENT_5);
        assertEquals(5, selected.getRecentRuns().size());
        assertEquals("run7", selected.getRecentRuns().get(0).getId());
        assertEquals(25, selected.getTotalTaskProgressUnits());
        assertEquals(300000, selected.getTotalActiveMillis());
        assertEquals(60000, service.estimate(selected, EstimateWindow.RECENT_5, 5).getEtaMillis().getAsDouble(), 0.01);
    }
}
