package com.slayerspeed.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttributionResult
{
	public static final AttributionResult NONE = new AttributionResult(0, 0);

	private final int literalKills;
	private final int slayerXp;
	private final List<String> confirmedNpcNames;

	public AttributionResult(int literalKills, int slayerXp)
	{
		this(literalKills, slayerXp, Collections.emptyList());
	}

	public AttributionResult(int literalKills, int slayerXp, List<String> confirmedNpcNames)
	{
		this.literalKills = literalKills;
		this.slayerXp = slayerXp;
		this.confirmedNpcNames = confirmedNpcNames == null || confirmedNpcNames.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(confirmedNpcNames));
	}

	public int getLiteralKills()
	{
		return literalKills;
	}

	public int getSlayerXp()
	{
		return slayerXp;
	}

	public List<String> getConfirmedNpcNames()
	{
		return confirmedNpcNames;
	}

	public AttributionResult plus(AttributionResult other)
	{
		List<String> names = new ArrayList<>(confirmedNpcNames);
		names.addAll(other.confirmedNpcNames);
		return new AttributionResult(
			literalKills + other.literalKills,
			slayerXp + other.slayerXp,
			names);
	}
}
