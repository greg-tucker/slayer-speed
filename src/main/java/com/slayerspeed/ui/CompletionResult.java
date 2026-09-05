package com.slayerspeed.ui;

import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.TaskRun;
import java.util.OptionalDouble;

/** Session snapshot; copying or displaying this value never changes saved history. */
public final class CompletionResult
{
    private final String runId;
    private final String text;
    public CompletionResult(TaskRun run, boolean saved, boolean newBest, boolean firstFullTask)
    {
        runId = run.getId();
        String milestone = firstFullTask ? " — First full task" : newBest ? " — New PB!" : "";
        text = run.getTaskName() + " complete" + milestone
            + "\n" + run.getEncounterProfileName()
            + (run.getTaskLocation() == null ? "" : " / " + run.getTaskLocation())
            + "\n" + KphCalculator.formatDuration(OptionalDouble.of(run.getActiveMillis())) + " tracked"
            + " · " + run.getTaskProgressUnits() + " task units"
            + "\n" + KphCalculator.formatRate(KphCalculator.effectiveKph(run.getRateTaskProgressUnits(), run.getActiveMillis()))
            + " units/hr · " + KphCalculator.formatXpRate(KphCalculator.slayerXpPerHour(run.getRateSlayerXp(), run.getActiveMillis())) + " XP/hr"
            + (run.getCannonballsUsed() > 0 ? "\n" + run.getCannonballsUsed() + " cannonballs" : "")
            + "\n" + (run.getTimingPolicy() == 0 ? "Legacy timing" : "Segmented timing")
            + " · " + (run.isFullTaskObserved() ? "Full task" : "Partial observation")
            + "\n" + (saved ? "Saved to personal history" : "Not saved — copy this result and check Help & data");
    }
    public String getRunId() { return runId; }
    public String getText() { return text; }
    public String getHtml()
    {
        return "<html><div style='width:160px'>" + text.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</div></html>";
    }
}
