package com.slayerspeed.calculation;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaskEstimateServiceTest
{
    private final TaskEstimateService service = new TaskEstimateService();

    @Test public void firstTaskIsAvailableBeforeBlendThreshold()
    {
        TaskEstimate result = service.calculate(90, 2, 60000, 0, 0, 10, true, 0, "Lifetime history");
        assertEquals(TaskEstimate.Source.LIVE, result.getSource());
        assertEquals(2700000, result.getEtaMillis().getAsDouble(), 0.001);
        assertTrue(result.getDescription().contains("this task only"));
    }

    @Test public void preservesHistoricalAndPooledRates()
    {
        TaskEstimate before = service.calculate(90, 9, 60000, 100, 3600000, 10, true, 1, "Lifetime history");
        assertEquals(TaskEstimate.Source.HISTORY, before.getSource());
        assertEquals(100, before.getRate().getAsDouble(), 0.001);
        TaskEstimate after = service.calculate(90, 10, 60000, 100, 3600000, 10, true, 1, "Lifetime history");
        assertEquals(TaskEstimate.Source.BLENDED, after.getSource());
        assertEquals(110 * 3600000.0 / 3660000, after.getRate().getAsDouble(), 0.001);
    }

    @Test public void noTimingOrWrongEncounterDoesNotInventAnEstimate()
    {
        assertFalse(service.calculate(90, 1, 0, 0, 0, 10, true, 0, "").getEtaMillis().isPresent());
        TaskEstimate mismatch = service.calculate(90, 30, 60000, 0, 0, 10, false, 0, "");
        assertEquals(TaskEstimate.Source.UNAVAILABLE, mismatch.getSource());
        assertEquals("No history for this encounter", mismatch.getDescription());
        TaskEstimate history = service.calculate(90, 30, 60000, 100, 3600000, 10, false, 0, "Lifetime history");
        assertEquals(TaskEstimate.Source.HISTORY, history.getSource());
        assertEquals(100, history.getRate().getAsDouble(), 0.001);
    }
}
