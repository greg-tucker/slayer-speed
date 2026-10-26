package com.slayerspeed.calculation;

import com.slayerspeed.model.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Select after eligibility and location/cohort grouping, never truncate raw runs first. */
public final class HistoryEstimateService
{
    public static final class Selection
    {
        public final TaskStatistics statistics;
        public final EstimateWindow window;
        public final boolean legacyFallback;
        private Selection(TaskStatistics statistics, EstimateWindow window, boolean fallback)
        {
            this.statistics = statistics; this.window = window; this.legacyFallback = fallback;
        }
        public String getScope()
        {
            return new HistoryEstimateService().scope(statistics, window,
                statistics == null ? 0 : statistics.getTimingPolicy()) + (legacyFallback ? " (fallback)" : "");
        }
    }

    public Selection selectWithFallback(TaskStatistics history, TaskStatistics legacy, EstimateWindow window)
    {
        TaskStatistics selected = select(history, window);
        if ((selected == null || selected.getTotalTaskProgressUnits() <= 0 || selected.getTotalActiveMillis() <= 0)
            && legacy != null && legacy.getTimingPolicy() == 0
            && legacy.getTotalTaskProgressUnits() > 0 && legacy.getTotalActiveMillis() > 0)
        {
            return new Selection(legacy, EstimateWindow.LIFETIME, true);
        }
        return new Selection(selected, window, false);
    }

    public TaskStatistics select(TaskStatistics history, EstimateWindow window)
    {
        if (history == null || window == null || window == EstimateWindow.LIFETIME) { return history; }
        TaskStatistics selected = new TaskStatistics(history.getTaskName(), history.getTaskLocation(),
            history.getEncounterProfileId(), history.getEncounterProfileName(), history.getTimingPolicy());
        List<TaskRun> runs = history.getRecentRuns().stream()
            .filter(run -> new TaskKey(run.getTaskName(), null, run.getEncounterProfileId()).equals(
                new TaskKey(history.getTaskName(), null, history.getEncounterProfileId()))
                && (history.getTaskLocation() == null || new TaskKey(run.getTaskName(), run.getTaskLocation())
                    .equals(new TaskKey(history.getTaskName(), history.getTaskLocation()))))
            .filter(run -> run.getStatus() == TaskRunStatus.COMPLETED && !run.isExcludedFromAverages()
                && run.getTimingPolicy() == history.getTimingPolicy()
                && run.getRateTaskProgressUnits() > 0 && run.getActiveMillis() > 0)
            .sorted(Comparator.comparingLong(TaskRun::getCompletedAtMillis).reversed()
                .thenComparing(TaskRun::getId))
            .limit(window.getSize()).collect(Collectors.toList());
        // addRun prepends, so retain the selected global timestamp ordering.
        for (int index = runs.size() - 1; index >= 0; index--)
        {
            selected.addRun(runs.get(index), window.getSize());
        }
        return selected;
    }

    public String scope(TaskStatistics selected, EstimateWindow window, int policy)
    {
        String timing = policy == 0 ? "Legacy" : "Segmented";
        if (window == null || window == EstimateWindow.LIFETIME) { return timing + " lifetime history"; }
        int available = selected == null ? 0 : selected.getRecentRuns().size();
        return timing + " recent " + available + "/" + window.getSize() + " eligible runs";
    }

    public TaskEstimate estimate(TaskStatistics selected, EstimateWindow window, int remaining)
    {
        return new TaskEstimateService().calculate(remaining, 0, 0,
            selected == null ? 0 : selected.getTotalTaskProgressUnits(),
            selected == null ? 0 : selected.getTotalActiveMillis(), 10, false,
            selected == null ? 0 : selected.getCompletedTaskCount(),
            scope(selected, window, selected == null ? 0 : selected.getTimingPolicy()));
    }
}
