package com.slayerspeed.ui;

import com.slayerspeed.calculation.TaskEstimate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SlayerSpeedViewModel
{
	public enum ActivityState { UNSPECIFIED, WAITING, TRACKING, SUSPENDED }
    private final ActivityState activityState;
    private final TaskEstimate estimate;
    private final boolean active;
	private final String task;
	private final String remaining;
	private final String observedCounts;
	private final String currentLiteralKph;
	private final String currentEffectiveKph;
	private final boolean currentRatesDiffer;
	private final String currentSlayerXpPerHour;
	private final String eta;
	private final String paceComparison;
	private final String estimatedFinishTime;
	private final boolean historyAvailable;
	private final String historicalLiteralKph;
	private final String historicalEffectiveKph;
	private final String historicalSlayerXpPerHour;
	private final String historicalAverageDuration;
	private final String historySample;
	private final String confidence;
	private final boolean cannonRelevant;
	private final String cannonballsUsed;
	private final String cannonRateLabel;
	private final String cannonballsPerKill;
	private final String estimatedTotalCannonballs;
	private final String estimatedRemainingCannonballs;
	private final String completionSummary;
	private final boolean encounterSelectorRelevant;
	private final String encounterProfileDisplay;
	private final String encounterSelectionNote;
	private final String selectedEncounterOptionId;
	private final List<EncounterProfileOption> encounterOptions;

	public SlayerSpeedViewModel(
		boolean active,
		String task,
		String remaining,
		String observedCounts,
		String currentLiteralKph,
		String currentEffectiveKph,
		boolean currentRatesDiffer,
		String currentSlayerXpPerHour,
		String eta,
		String paceComparison,
		String estimatedFinishTime,
		boolean historyAvailable,
		String historicalLiteralKph,
		String historicalEffectiveKph,
		String historicalSlayerXpPerHour,
		String historicalAverageDuration,
		String historySample,
		String confidence,
		boolean cannonRelevant,
		String cannonballsUsed,
		String cannonRateLabel,
		String cannonballsPerKill,
		String estimatedTotalCannonballs,
		String estimatedRemainingCannonballs,
		String completionSummary)
	{
		this(
			active, task, remaining, observedCounts, currentLiteralKph, currentEffectiveKph,
			currentRatesDiffer, currentSlayerXpPerHour, eta, paceComparison, estimatedFinishTime,
			historyAvailable, historicalLiteralKph, historicalEffectiveKph,
			historicalSlayerXpPerHour, historicalAverageDuration, historySample, confidence,
			cannonRelevant, cannonballsUsed, cannonRateLabel, cannonballsPerKill,
			estimatedTotalCannonballs, estimatedRemainingCannonballs, completionSummary,
			false, "", "", EncounterProfileOption.AUTO_ID, Collections.emptyList());
	}

	public SlayerSpeedViewModel(
		boolean active,
		String task,
		String remaining,
		String observedCounts,
		String currentLiteralKph,
		String currentEffectiveKph,
		boolean currentRatesDiffer,
		String currentSlayerXpPerHour,
		String eta,
		String paceComparison,
		String estimatedFinishTime,
		boolean historyAvailable,
		String historicalLiteralKph,
		String historicalEffectiveKph,
		String historicalSlayerXpPerHour,
		String historicalAverageDuration,
		String historySample,
		String confidence,
		boolean cannonRelevant,
		String cannonballsUsed,
		String cannonRateLabel,
		String cannonballsPerKill,
		String estimatedTotalCannonballs,
		String estimatedRemainingCannonballs,
		String completionSummary,
		boolean encounterSelectorRelevant,
		String encounterProfileDisplay,
		String encounterSelectionNote,
		String selectedEncounterOptionId,
		List<EncounterProfileOption> encounterOptions)
	{
        this(active, task, remaining, observedCounts, currentLiteralKph, currentEffectiveKph, currentRatesDiffer, currentSlayerXpPerHour, eta, paceComparison, estimatedFinishTime, historyAvailable, historicalLiteralKph, historicalEffectiveKph, historicalSlayerXpPerHour, historicalAverageDuration, historySample, confidence, cannonRelevant, cannonballsUsed, cannonRateLabel, cannonballsPerKill, estimatedTotalCannonballs, estimatedRemainingCannonballs, completionSummary, encounterSelectorRelevant, encounterProfileDisplay, encounterSelectionNote, selectedEncounterOptionId, encounterOptions, null);
    }

    public SlayerSpeedViewModel(
		boolean active,
		String task,
		String remaining,
		String observedCounts,
		String currentLiteralKph,
		String currentEffectiveKph,
		boolean currentRatesDiffer,
		String currentSlayerXpPerHour,
		String eta,
		String paceComparison,
		String estimatedFinishTime,
		boolean historyAvailable,
		String historicalLiteralKph,
		String historicalEffectiveKph,
		String historicalSlayerXpPerHour,
		String historicalAverageDuration,
		String historySample,
		String confidence,
		boolean cannonRelevant,
		String cannonballsUsed,
		String cannonRateLabel,
		String cannonballsPerKill,
		String estimatedTotalCannonballs,
		String estimatedRemainingCannonballs,
		String completionSummary,
		boolean encounterSelectorRelevant,
		String encounterProfileDisplay,
		String encounterSelectionNote,
		String selectedEncounterOptionId,
		List<EncounterProfileOption> encounterOptions,
        TaskEstimate estimate)
	{
		this.activityState = ActivityState.UNSPECIFIED;
        this.estimate = estimate;
		this.active = active;
		this.task = task;
		this.remaining = remaining;
		this.observedCounts = observedCounts;
		this.currentLiteralKph = currentLiteralKph;
		this.currentEffectiveKph = currentEffectiveKph;
		this.currentRatesDiffer = currentRatesDiffer;
		this.currentSlayerXpPerHour = currentSlayerXpPerHour;
		this.eta = eta;
		this.paceComparison = paceComparison;
		this.estimatedFinishTime = estimatedFinishTime;
		this.historyAvailable = historyAvailable;
		this.historicalLiteralKph = historicalLiteralKph;
		this.historicalEffectiveKph = historicalEffectiveKph;
		this.historicalSlayerXpPerHour = historicalSlayerXpPerHour;
		this.historicalAverageDuration = historicalAverageDuration;
		this.historySample = historySample;
		this.confidence = confidence;
		this.cannonRelevant = cannonRelevant;
		this.cannonballsUsed = cannonballsUsed;
		this.cannonRateLabel = cannonRateLabel;
		this.cannonballsPerKill = cannonballsPerKill;
		this.estimatedTotalCannonballs = estimatedTotalCannonballs;
		this.estimatedRemainingCannonballs = estimatedRemainingCannonballs;
		this.completionSummary = completionSummary;
		this.encounterSelectorRelevant = encounterSelectorRelevant;
		this.encounterProfileDisplay = encounterProfileDisplay;
		this.encounterSelectionNote = encounterSelectionNote;
		this.selectedEncounterOptionId = selectedEncounterOptionId;
		this.encounterOptions = encounterOptions == null || encounterOptions.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(encounterOptions));
	}

	public static SlayerSpeedViewModel noTask()
	{
		return noTask(null);
	}

	public static SlayerSpeedViewModel noTask(String completionSummary)
	{
		return new SlayerSpeedViewModel(
			false, "No active Slayer task", "--", "--", "--", "--", false, "--", "--", "--", "--",
			false, "--", "--", "--", "--", "Complete a task to build your personal averages.", "No data",
			false, "0", "Average / kill", "--", "--", "--", completionSummary);
	}

    private SlayerSpeedViewModel(SlayerSpeedViewModel source, ActivityState state)
    {
        this.activityState = state;
        this.estimate = source.estimate;
        this.active = source.active;
        this.task = source.task;
        this.remaining = source.remaining;
        this.observedCounts = source.observedCounts;
        this.currentLiteralKph = source.currentLiteralKph;
        this.currentEffectiveKph = source.currentEffectiveKph;
        this.currentRatesDiffer = source.currentRatesDiffer;
        this.currentSlayerXpPerHour = source.currentSlayerXpPerHour;
        this.eta = source.eta;
        this.paceComparison = source.paceComparison;
        this.estimatedFinishTime = source.estimatedFinishTime;
        this.historyAvailable = source.historyAvailable;
        this.historicalLiteralKph = source.historicalLiteralKph;
        this.historicalEffectiveKph = source.historicalEffectiveKph;
        this.historicalSlayerXpPerHour = source.historicalSlayerXpPerHour;
        this.historicalAverageDuration = source.historicalAverageDuration;
        this.historySample = source.historySample;
        this.confidence = source.confidence;
        this.cannonRelevant = source.cannonRelevant;
        this.cannonballsUsed = source.cannonballsUsed;
        this.cannonRateLabel = source.cannonRateLabel;
        this.cannonballsPerKill = source.cannonballsPerKill;
        this.estimatedTotalCannonballs = source.estimatedTotalCannonballs;
        this.estimatedRemainingCannonballs = source.estimatedRemainingCannonballs;
        this.completionSummary = source.completionSummary;
        this.encounterSelectorRelevant = source.encounterSelectorRelevant;
        this.encounterProfileDisplay = source.encounterProfileDisplay;
        this.encounterSelectionNote = source.encounterSelectionNote;
        this.selectedEncounterOptionId = source.selectedEncounterOptionId;
        this.encounterOptions = source.encounterOptions;
    }
    public SlayerSpeedViewModel withActivityState(ActivityState state)
    {
        return new SlayerSpeedViewModel(this, state);
    }
    public ActivityState getActivityState() { return activityState; }
    public String getActivityLabel()
    {
        switch (activityState)
        {
            case TRACKING: return "Tracking task activity";
            case SUSPENDED: return "Waiting for task activity";
            case WAITING: return "Waiting for timed activity";
            default: return "";
        }
    }
    public String getActivityExplanation()
    {
        if (activityState == ActivityState.TRACKING || activityState == ActivityState.UNSPECIFIED) { return ""; }
        String rates = "--".equals(currentSlayerXpPerHour) && "--".equals(currentEffectiveKph)
            ? "" : "Rates shown are from recorded activity. ";
        if (estimate == null || estimate.getSource() == TaskEstimate.Source.UNAVAILABLE)
        {
            return rates + "An estimate needs timed task progress.";
        }
        switch (estimate.getSource())
        {
            case HISTORY: return rates + "ETA uses saved history.";
            case BLENDED: return rates + "ETA uses history and recorded pace.";
            default: return rates + "ETA uses recorded pace.";
        }
    }

    public String getCompactEstimateDescription()
    {
        return estimate == null ? getEstimateDescription() : estimate.getCompactDescription();
    }

    public TaskEstimate getEstimate() { return estimate; }

    public String getEstimateDescription()
    {
        return estimate == null ? ("--".equals(eta) ? "Waiting for timed task activity" : "Personal estimate")
            : estimate.getDescription();
    }

    public boolean isActive()
	{
		return active;
	}

	public String getTask()
	{
		return task;
	}

	public String getRemaining()
	{
		return remaining;
	}

	public String getObservedCounts()
	{
		return observedCounts;
	}

	public String getCurrentLiteralKph()
	{
		return currentLiteralKph;
	}

	public String getCurrentEffectiveKph()
	{
		return currentEffectiveKph;
	}

	public boolean isCurrentRatesDiffer()
	{
		return currentRatesDiffer;
	}

	public String getCurrentSlayerXpPerHour()
	{
		return currentSlayerXpPerHour;
	}

	public String getEta()
	{
		return eta;
	}

	public String getPaceComparison()
	{
		return paceComparison;
	}

	public boolean hasPaceComparison()
	{
		return paceComparison != null && !paceComparison.isEmpty();
	}

	public String getEstimatedFinishTime()
	{
		return estimatedFinishTime;
	}

	public boolean hasEstimatedFinishTime()
	{
		return estimatedFinishTime != null && !estimatedFinishTime.isEmpty();
	}

	public boolean isHistoryAvailable()
	{
		return historyAvailable;
	}

	public String getHistoricalLiteralKph()
	{
		return historicalLiteralKph;
	}

	public String getHistoricalEffectiveKph()
	{
		return historicalEffectiveKph;
	}

	public String getHistoricalSlayerXpPerHour()
	{
		return historicalSlayerXpPerHour;
	}

	public String getHistoricalAverageDuration()
	{
		return historicalAverageDuration;
	}

	public String getHistorySample()
	{
		return historySample;
	}

	public String getConfidence()
	{
		return confidence;
	}

	public boolean isCannonRelevant()
	{
		return cannonRelevant;
	}

	public String getCannonballsUsed()
	{
		return cannonballsUsed;
	}

	public String getCannonRateLabel()
	{
		return cannonRateLabel;
	}

	public String getCannonballsPerKill()
	{
		return cannonballsPerKill;
	}

	public String getEstimatedTotalCannonballs()
	{
		return estimatedTotalCannonballs;
	}

	public String getEstimatedRemainingCannonballs()
	{
		return estimatedRemainingCannonballs;
	}

	public String getCompletionSummary()
	{
		return completionSummary;
	}

	public boolean isEncounterSelectorRelevant()
	{
		return encounterSelectorRelevant;
	}

	public String getEncounterProfileDisplay()
	{
		return encounterProfileDisplay;
	}

	public String getEncounterSelectionNote()
	{
		return encounterSelectionNote;
	}

	public String getSelectedEncounterOptionId()
	{
		return selectedEncounterOptionId;
	}

	public List<EncounterProfileOption> getEncounterOptions()
	{
		return encounterOptions;
	}
}
