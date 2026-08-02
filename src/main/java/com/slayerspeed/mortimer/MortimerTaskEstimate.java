package com.slayerspeed.mortimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MortimerTaskEstimate
{
	private final List<ProfileEstimate> profiles;
	private final int additionalProfileCount;

	public MortimerTaskEstimate(List<ProfileEstimate> profiles, int additionalProfileCount)
	{
		this.profiles = profiles == null || profiles.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(profiles));
		this.additionalProfileCount = Math.max(0, additionalProfileCount);
	}

	public static MortimerTaskEstimate noData()
	{
		return new MortimerTaskEstimate(Collections.emptyList(), 0);
	}

	public boolean hasData()
	{
		return !profiles.isEmpty();
	}

	public List<ProfileEstimate> getProfiles()
	{
		return profiles;
	}

	public int getAdditionalProfileCount()
	{
		return additionalProfileCount;
	}

	public static final class ProfileEstimate
	{
		private final String profileName;
		private final String durationRange;
		private final int completedTasks;
		private final String confidence;

		public ProfileEstimate(
			String profileName,
			String durationRange,
			int completedTasks,
			String confidence)
		{
			this.profileName = profileName;
			this.durationRange = durationRange;
			this.completedTasks = completedTasks;
			this.confidence = confidence;
		}

		public String getProfileName()
		{
			return profileName;
		}

		public String getDurationRange()
		{
			return durationRange;
		}

		public int getCompletedTasks()
		{
			return completedTasks;
		}

		public String getConfidence()
		{
			return confidence;
		}
	}
}
