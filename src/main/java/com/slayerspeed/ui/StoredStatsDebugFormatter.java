package com.slayerspeed.ui;

import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskStatistics;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalDouble;

public final class StoredStatsDebugFormatter
{
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
		DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z");

	private StoredStatsDebugFormatter()
	{
	}

	public static String format(Collection<TaskStatistics> storedStatistics)
	{
		Collection<TaskStatistics> records = storedStatistics == null
			? Collections.emptyList()
			: storedStatistics;
		StringBuilder output = new StringBuilder();
		output.append("Slayer Task Speed - stored task data\n")
			.append("Exact task/location/encounter records: ")
			.append(records.size())
			.append("\n\n");
		if (records.isEmpty())
		{
			return output.append("No stored task statistics.\n").toString();
		}

		int recordNumber = 0;
		for (TaskStatistics statistics : records)
		{
			output.append("=== Record ").append(++recordNumber).append(" ===\n");
			line(output, "Storage key", new TaskKey(
				statistics.getTaskName(),
				statistics.getTaskLocation(),
				statistics.getEncounterProfileId()).asStorageKey());
			line(output, "Task", statistics.getTaskName());
			line(output, "Location", displayOptional(statistics.getTaskLocation()));
			line(output, "Encounter profile", statistics.getEncounterProfileName());
			line(output, "Encounter profile ID", displayOptional(statistics.getEncounterProfileId()));
			line(output, "Completed full tasks", statistics.getCompletedTaskCount());
			line(output, "Actual kills", statistics.getTotalActualKills());
			line(output, "Task progress units", statistics.getTotalTaskProgressUnits());
			line(output, "Slayer XP", statistics.getTotalSlayerXp());
			line(output, "Cannonballs used", statistics.getTotalCannonballsUsed());
			line(output, "Cannon-run kills", statistics.getTotalCannonRunActualKills());
			line(output, "Cannon-run task units", statistics.getTotalCannonRunTaskProgressUnits());
			line(output, "Active milliseconds", statistics.getTotalActiveMillis());
			line(output, "Active duration", formatDuration(statistics.getTotalActiveMillis()));
			line(output, "Full-task milliseconds", statistics.getTotalCompletedTaskMillis());
			line(output, "Full-task duration", formatDuration(statistics.getTotalCompletedTaskMillis()));
			line(output, "Literal KPH", KphCalculator.formatRate(KphCalculator.literalKph(
				statistics.getTotalActualKills(), statistics.getTotalActiveMillis())));
			line(output, "Effective KPH", KphCalculator.formatRate(KphCalculator.effectiveKph(
				statistics.getTotalTaskProgressUnits(), statistics.getTotalActiveMillis())));
			line(output, "Slayer XP/hour", KphCalculator.formatXpRate(KphCalculator.slayerXpPerHour(
				statistics.getTotalSlayerXp(), statistics.getTotalActiveMillis())));
			line(output, "Last updated", formatTimestamp(statistics.getLastUpdatedAtMillis()));
			line(output, "Retained recent runs", statistics.getRecentRuns().size());

			int runNumber = 0;
			for (TaskRun run : statistics.getRecentRuns())
			{
				output.append("  -- Run ").append(++runNumber).append(" --\n");
				runLine(output, "ID", run.getId());
				runLine(output, "Status", run.getStatus());
				runLine(output, "Excluded from averages", run.isExcludedFromAverages());
				runLine(output, "Full task observed", run.isFullTaskObserved());
				runLine(output, "Initial amount", run.getInitialAmount());
				runLine(output, "Ending amount", run.getEndingAmount());
				runLine(output, "Actual kills", run.getActualKills());
				runLine(output, "Task progress units", run.getTaskProgressUnits());
				runLine(output, "Kill Slayer XP", run.getKillSlayerXp());
				runLine(output, "Bonus Slayer XP", run.getBonusSlayerXp());
				runLine(output, "Cannonballs used", run.getCannonballsUsed());
				runLine(output, "Active milliseconds", run.getActiveMillis());
				runLine(output, "Setup milliseconds", run.getSetupMillis());
				runLine(output, "Started", formatTimestamp(run.getStartedAtMillis()));
				runLine(output, "Completed", formatTimestamp(run.getCompletedAtMillis()));
			}
			output.append('\n');
		}
		return output.toString();
	}

	private static void line(StringBuilder output, String name, Object value)
	{
		output.append(name).append(": ").append(value).append('\n');
	}

	private static void runLine(StringBuilder output, String name, Object value)
	{
		output.append("    ").append(name).append(": ").append(value).append('\n');
	}

	private static String displayOptional(String value)
	{
		return value == null || value.trim().isEmpty() ? "<none>" : value;
	}

	private static String formatDuration(long millis)
	{
		return KphCalculator.formatDuration(OptionalDouble.of(Math.max(0L, millis)));
	}

	private static String formatTimestamp(long timestamp)
	{
		return timestamp <= 0L
			? "<none>"
			: TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
	}
}
