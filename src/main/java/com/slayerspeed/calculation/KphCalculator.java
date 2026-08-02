package com.slayerspeed.calculation;

import java.time.Duration;
import java.util.Locale;
import java.util.OptionalDouble;

public final class KphCalculator
{
	private static final double MILLIS_PER_HOUR = 3_600_000.0;

	private KphCalculator()
	{
	}

	public static OptionalDouble ratePerHour(long amount, long activeMillis)
	{
		if (amount < 0 || activeMillis <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(amount * MILLIS_PER_HOUR / activeMillis);
	}

	public static OptionalDouble literalKph(int actualKills, long activeMillis)
	{
		return ratePerHour(actualKills, activeMillis);
	}

	public static OptionalDouble effectiveKph(int progressUnits, long activeMillis)
	{
		return ratePerHour(progressUnits, activeMillis);
	}

	public static OptionalDouble slayerXpPerHour(int slayerXp, long activeMillis)
	{
		return ratePerHour(slayerXp, activeMillis);
	}

	public static OptionalDouble xpPerKill(int slayerXp, int actualKills)
	{
		return ratio(slayerXp, actualKills);
	}

	public static OptionalDouble xpPerTaskUnit(int slayerXp, int progressUnits)
	{
		return ratio(slayerXp, progressUnits);
	}

	public static OptionalDouble cannonballsPerKill(int cannonballs, int actualKills)
	{
		return ratio(cannonballs, actualKills);
	}

	public static OptionalDouble cannonballsPerTaskUnit(int cannonballs, int progressUnits)
	{
		return ratio(cannonballs, progressUnits);
	}

	public static OptionalDouble estimatedCannonballs(int taskSize, double cannonballsPerTaskUnit)
	{
		if (taskSize <= 0 || !Double.isFinite(cannonballsPerTaskUnit) || cannonballsPerTaskUnit < 0.0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(taskSize * cannonballsPerTaskUnit);
	}

	private static OptionalDouble ratio(int numerator, int denominator)
	{
		if (numerator < 0 || denominator <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of((double) numerator / denominator);
	}

	public static OptionalDouble etaMillis(int remainingAmount, double effectiveKph)
	{
		if (remainingAmount < 0 || !Double.isFinite(effectiveKph) || effectiveKph <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(remainingAmount * MILLIS_PER_HOUR / effectiveKph);
	}

	public static String formatRate(OptionalDouble rate)
	{
		return rate.isPresent() ? String.format(Locale.ENGLISH, "%.1f", rate.getAsDouble()) : "--";
	}

	public static String formatXpRate(OptionalDouble rate)
	{
		if (!rate.isPresent())
		{
			return "--";
		}
		double value = Math.max(0.0, rate.getAsDouble());
		if (value >= 1_000_000.0)
		{
			return String.format(Locale.ENGLISH, "%.2fm", value / 1_000_000.0);
		}
		if (value >= 1_000.0)
		{
			return String.format(Locale.ENGLISH, "%.1fk", value / 1_000.0);
		}
		return String.format(Locale.ENGLISH, "%.0f", value);
	}

	public static String formatDuration(OptionalDouble millis)
	{
		if (!millis.isPresent())
		{
			return "--";
		}
		long seconds = Math.max(0L, Math.round(millis.getAsDouble() / 1000.0));
		Duration duration = Duration.ofSeconds(seconds);
		long hours = duration.toHours();
		long minutes = duration.minusHours(hours).toMinutes();
		long remainingSeconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
		if (hours == 0 && minutes < 10)
		{
			return minutes > 0
				? String.format(Locale.ENGLISH, "%dm %02ds", minutes, remainingSeconds)
				: remainingSeconds + "s";
		}
		return hours > 0 ? String.format(Locale.ENGLISH, "%dh %02dm", hours, minutes) : minutes + "m";
	}

	public static String formatCannonballEstimate(OptionalDouble cannonballs)
	{
		return cannonballs.isPresent()
			? Long.toString((long) Math.ceil(Math.max(0.0, cannonballs.getAsDouble())))
			: "--";
	}
}
