package com.slayerspeed;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SlayerSpeedConfig.GROUP)
public interface SlayerSpeedConfig extends Config
{
	String GROUP = "slayerspeed";

	@ConfigSection(
		name = "Display",
		description = "Choose which task information is shown",
		position = 0,
		closedByDefault = false
	)
	String DISPLAY_SECTION = "display";

	@ConfigSection(
		name = "Estimates",
		description = "Configure task time and pace estimates",
		position = 1,
		closedByDefault = false
	)
	String ESTIMATES_SECTION = "estimates";

	@ConfigSection(
		name = "Cannon",
		description = "Configure cannonball tracking and estimates",
		position = 2,
		closedByDefault = false
	)
	String CANNON_SECTION = "cannon";

	@ConfigSection(
		name = "History",
		description = "Configure saved task history",
		position = 3,
		closedByDefault = false
	)
	String HISTORY_SECTION = "history";

	@ConfigSection(
		name = "Advanced",
		description = "Advanced tracking and sampling controls",
		position = 4,
		closedByDefault = true
	)
	String ADVANCED_SECTION = "advanced";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show current SlayerSpeed rates and ETA in an in-game overlay",
		position = 0,
		section = DISPLAY_SECTION
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayMode",
		name = "Detail level",
		description = "Simple shows essential information; Detailed shows every tracked metric",
		position = 1,
		section = DISPLAY_SECTION
	)
	default SlayerSpeedDisplayMode displayMode()
	{
		return SlayerSpeedDisplayMode.SIMPLE;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "idleTimeoutMinutes",
		name = "Idle timeout",
		description = "Maximum gap in minutes counted between task activity events",
		position = 0,
		section = ADVANCED_SECTION
	)
	default int idleTimeoutMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "separateByLocation",
		name = "Separate locations",
		description = "Keep separate averages for location-specific assignments",
		position = 0,
		section = HISTORY_SECTION
	)
	default boolean separateByLocation()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPaceComparison",
		name = "Show pace comparison",
		description = "Compare the current task pace with your saved average",
		position = 0,
		section = ESTIMATES_SECTION
	)
	default boolean showPaceComparison()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEstimatedFinishTime",
		name = "Show finish time",
		description = "Show the estimated local clock time when the task will finish",
		position = 1,
		section = ESTIMATES_SECTION
	)
	default boolean showEstimatedFinishTime()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCompletionSummary",
		name = "Completion summary",
		description = "Show task results and personal-best status after completion",
		position = 2,
		section = DISPLAY_SECTION
	)
	default boolean showCompletionSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLearningProgress",
		name = "Learning progress",
		description = "Explain how many observations are needed before a new estimate is ready",
		position = 2,
		section = ESTIMATES_SECTION
	)
	default boolean showLearningProgress()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEncounterSelector",
		name = "Monster estimate selector",
		description = "Allow Auto or manual selection when one assignment has different monster or boss rates",
		position = 3,
		section = ESTIMATES_SECTION
	)
	default boolean showEncounterSelector()
	{
		return true;
	}

	@ConfigItem(
		keyName = "experimentalMortimerEstimates",
		name = "Experimental Mortimer estimates",
		description = "Show personal task-time ranges on Mortimer's task-choice screen; this experimental interface may change",
		position = 4,
		section = ESTIMATES_SECTION
	)
	default boolean experimentalMortimerEstimates()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showRecentRuns",
		name = "Show recent runs",
		description = "Show individual task runs with exclude and delete controls",
		position = 1,
		section = HISTORY_SECTION
	)
	default boolean showRecentRuns()
	{
		return true;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "recentRunsShown",
		name = "Runs shown initially",
		description = "Number of runs shown when task history is expanded; older retained runs remain available through Show all",
		position = 2,
		section = HISTORY_SECTION
	)
	default int recentRunsShown()
	{
		return 5;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "currentRateMinimumUnits",
		name = "Blend live rate after",
		description = "Task progress before blending live and historical rates; a first-task live estimate can appear sooner",
		position = 1,
		section = ADVANCED_SECTION
	)
	default int currentRateMinimumUnits()
	{
		return 10;
	}

	@Range(min = 5, max = 100)
	@ConfigItem(
		keyName = "maximumRecentRuns",
		name = "Runs kept per task",
		description = "Maximum number of recent task runs retained for each task",
		position = 3,
		section = HISTORY_SECTION
	)
	default int maximumRecentRuns()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "trackCannonballs",
		name = "Track cannonballs",
		description = "Record cannonballs consumed while a Slayer assignment is active",
		position = 0,
		section = CANNON_SECTION
	)
	default boolean trackCannonballs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCannonMetrics",
		name = "Show cannon metrics",
		description = "Show cannon metrics when the current task or its history contains cannon usage",
		position = 1,
		section = CANNON_SECTION
	)
	default boolean showCannonMetrics()
	{
		return true;
	}
    @ConfigItem(keyName = "estimateWindow", name = "Estimate history",
        description = "Lifetime by default. Recent windows use only eligible retained runs; missing runs are shown, never filled from lifetime totals.",
        position = 5, section = ESTIMATES_SECTION)
    default com.slayerspeed.calculation.EstimateWindow estimateWindow()
    {
        return com.slayerspeed.calculation.EstimateWindow.LIFETIME;
    }

    @ConfigItem(keyName = "experimentalSegmentedTiming", name = "Experimental segmented timing",
        description = "New tasks use matched timing intervals and manual pause. Kept separate from legacy history; requires live validation.",
        position = 2, section = ADVANCED_SECTION)
    default boolean experimentalSegmentedTiming() { return false; }
}
