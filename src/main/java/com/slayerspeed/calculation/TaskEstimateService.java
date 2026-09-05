package com.slayerspeed.calculation;

import java.util.OptionalDouble;

/** No client, clock, persistence or Swing dependencies. */
public final class TaskEstimateService
{
    public TaskEstimate calculate(int remaining, long liveUnits, long liveMillis,
        long historicalUnits, long historicalMillis, int minimumUnits, boolean matches,
        int fullTasks, String scope)
    {
        OptionalDouble history = KphCalculator.ratePerHour(historicalUnits, historicalMillis);
        OptionalDouble live = KphCalculator.ratePerHour(liveUnits, liveMillis);
        boolean hasHistory = history.isPresent() && history.getAsDouble() > 0;
        OptionalDouble rate;
        TaskEstimate.Source source;
        if (matches && liveUnits >= minimumUnits)
        {
            rate = KphCalculator.ratePerHour(historicalUnits + liveUnits, historicalMillis + liveMillis);
            source = hasHistory ? TaskEstimate.Source.BLENDED : TaskEstimate.Source.LIVE;
        }
        else if (hasHistory)
        {
            rate = history;
            source = TaskEstimate.Source.HISTORY;
        }
        else
        {
            rate = matches ? live : OptionalDouble.empty();
            source = TaskEstimate.Source.LIVE;
        }
        OptionalDouble eta = rate.isPresent()
            ? KphCalculator.etaMillis(remaining, rate.getAsDouble()) : OptionalDouble.empty();
        if (!eta.isPresent()) { source = TaskEstimate.Source.UNAVAILABLE; }
        return new TaskEstimate(source, rate, eta, scope, fullTasks,
            matches ? "Waiting for timed task activity" : "No history for this encounter");
    }
}
