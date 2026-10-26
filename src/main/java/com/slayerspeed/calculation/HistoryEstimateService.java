package com.slayerspeed.calculation;

import com.slayerspeed.model.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Select after eligibility and location grouping, never truncate raw runs first. */
public final class HistoryEstimateService
{
    public TaskStatistics select(TaskStatistics history, EstimateWindow window)
    {
        if (history == null || window == null || window == EstimateWindow.LIFETIME) { return history; }
        TaskStatistics selected = new TaskStatistics(history.getTaskName(), history.getTaskLocation(),
            history.getEncounterProfileId(), history.getEncounterProfileName());
        List<TaskRun> runs = history.getRecentRuns().stream()
            .filter(run -> new TaskKey(run.getTaskName(), null, run.getEncounterProfileId()).equals(
                new TaskKey(history.getTaskName(), null, history.getEncounterProfileId()))
                && (history.getTaskLocation() == null || new TaskKey(run.getTaskName(), run.getTaskLocation())
                    .equals(new TaskKey(history.getTaskName(), history.getTaskLocation()))))
            .filter(run -> run.getStatus() == TaskRunStatus.COMPLETED && !run.isExcludedFromAverages()
                && run.getTaskProgressUnits() > 0 && run.getActiveMillis() > 0)
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

    public String scope(TaskStatistics selected, EstimateWindow window)
    {
        if (window == null || window == EstimateWindow.LIFETIME) { return "Lifetime history"; }
        int available = selected == null ? 0 : selected.getRecentRuns().size();
        return "Recent " + available + "/" + window.getSize() + " eligible runs";
    }

    public TaskEstimate estimate(TaskStatistics selected, EstimateWindow window, int remaining)
    {
        return new TaskEstimateService().calculate(remaining, 0, 0,
            selected == null ? 0 : selected.getTotalTaskProgressUnits(),
            selected == null ? 0 : selected.getTotalActiveMillis(), 10, false,
            selected == null ? 0 : selected.getCompletedTaskCount(),
            scope(selected, window));
    }
}
