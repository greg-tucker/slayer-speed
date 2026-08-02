package com.slayerspeed.mortimer;

import com.slayerspeed.calculation.Confidence;
import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.EncounterProfile;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskStatistics;
import com.slayerspeed.persistence.TaskHistoryRepository;
import com.slayerspeed.tracking.EncounterProfileResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import javax.inject.Inject;

public class MortimerEstimateService
{
	private static final int MAXIMUM_DISPLAYED_PROFILES = 2;
	private static final Map<String, String> HISTORY_TASK_ALIASES = new HashMap<>();

	static
	{
		// Mortimer's UI uses the plural while RuneLite's Slayer service stores the singular.
		HISTORY_TASK_ALIASES.put("bloodvelds", "Bloodveld");
	}

	private final TaskHistoryRepository historyRepository;
	private final EncounterProfileResolver encounterProfileResolver;

	@Inject
	public MortimerEstimateService(
		TaskHistoryRepository historyRepository,
		EncounterProfileResolver encounterProfileResolver)
	{
		this.historyRepository = historyRepository;
		this.encounterProfileResolver = encounterProfileResolver;
	}

	public MortimerTaskEstimate estimate(MortimerTaskOffer offer)
	{
		String historyTaskName = historyTaskName(offer.getTaskName());
		Collection<TaskStatistics> taskHistories = historyRepository.profilesForTask(
			new TaskKey(historyTaskName, null), false);
		List<TaskStatistics> eligible = new ArrayList<>();
		boolean hasKnownProfile = false;
		for (TaskStatistics statistics : taskHistories)
		{
			if (statistics.getTotalTaskProgressUnits() > 0 && statistics.getTotalActiveMillis() > 0)
			{
				eligible.add(statistics);
				hasKnownProfile |= !statistics.getEncounterProfileId().isEmpty();
			}
		}
		if (hasKnownProfile)
		{
			eligible.removeIf(statistics -> statistics.getEncounterProfileId().isEmpty());
		}
		if (eligible.isEmpty())
		{
			return MortimerTaskEstimate.noData();
		}

		boolean labelProfiles = eligible.size() > 1
			|| encounterProfileResolver.suggestionsForTask(historyTaskName).size() > 1;
		List<MortimerTaskEstimate.ProfileEstimate> estimates = new ArrayList<>();
		for (TaskStatistics statistics : eligible)
		{
			OptionalDouble rate = KphCalculator.effectiveKph(
				statistics.getTotalTaskProgressUnits(), statistics.getTotalActiveMillis());
			if (!rate.isPresent())
			{
				continue;
			}
			OptionalDouble minimumMillis = KphCalculator.etaMillis(
				offer.getMinimumAmount(), rate.getAsDouble());
			OptionalDouble maximumMillis = KphCalculator.etaMillis(
				offer.getMaximumAmount(), rate.getAsDouble());
			Confidence confidence = Confidence.fromSample(
				statistics.getCompletedTaskCount(), statistics.getTotalTaskProgressUnits());
			estimates.add(new MortimerTaskEstimate.ProfileEstimate(
				labelProfiles ? compactProfileName(statistics) : "",
				formatRange(minimumMillis, maximumMillis),
				statistics.getCompletedTaskCount(),
				confidence.getDisplayName()));
		}

		if (estimates.isEmpty())
		{
			return MortimerTaskEstimate.noData();
		}
		int additionalProfiles = Math.max(0, estimates.size() - MAXIMUM_DISPLAYED_PROFILES);
		if (additionalProfiles > 0)
		{
			estimates = new ArrayList<>(estimates.subList(0, MAXIMUM_DISPLAYED_PROFILES));
		}
		return new MortimerTaskEstimate(estimates, additionalProfiles);
	}

	static String historyTaskName(String offerTaskName)
	{
		String normalized = offerTaskName == null
			? ""
			: offerTaskName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
		return HISTORY_TASK_ALIASES.getOrDefault(normalized, offerTaskName);
	}

	private static String compactProfileName(TaskStatistics statistics)
	{
		if (statistics.getEncounterProfileId().isEmpty())
		{
			return "Mixed";
		}
		String name = statistics.getEncounterProfileName()
			.replaceAll("(?i)\\s*\\((?:regular|boss)\\)$", "")
			.trim();
		return name.isEmpty() || EncounterProfile.UNKNOWN_DISPLAY_NAME.equals(name) ? "Mixed" : name;
	}

	private static String formatRange(OptionalDouble minimumMillis, OptionalDouble maximumMillis)
	{
		if (!minimumMillis.isPresent() || !maximumMillis.isPresent())
		{
			return "--";
		}
		long minimumMinutes = roundedMinutes(minimumMillis.getAsDouble());
		long maximumMinutes = Math.max(minimumMinutes, roundedMinutes(maximumMillis.getAsDouble()));
		if (minimumMinutes == maximumMinutes)
		{
			return "~" + formatMinutes(minimumMinutes);
		}
		return formatMinutes(minimumMinutes) + "-" + formatMinutes(maximumMinutes);
	}

	private static long roundedMinutes(double millis)
	{
		return Math.max(1L, Math.round(millis / 60_000.0));
	}

	private static String formatMinutes(long minutes)
	{
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long hours = minutes / 60;
		long remainingMinutes = minutes % 60;
		return remainingMinutes == 0 ? hours + "h" : hours + "h " + remainingMinutes + "m";
	}
}
