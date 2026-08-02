package com.slayerspeed.calculation;

import java.util.OptionalDouble;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KphCalculatorTest
{
	@Test
	public void calculatesAllRatesFromSharedActiveTime()
	{
		long oneHour = 3_600_000L;
		assertEquals(100.0, KphCalculator.literalKph(100, oneHour).getAsDouble(), 0.001);
		assertEquals(125.0, KphCalculator.effectiveKph(125, oneHour).getAsDouble(), 0.001);
		assertEquals(15_000.0, KphCalculator.slayerXpPerHour(15_000, oneHour).getAsDouble(), 0.001);
	}

	@Test
	public void rejectsRatesWithoutElapsedTime()
	{
		assertFalse(KphCalculator.literalKph(10, 0).isPresent());
	}

	@Test
	public void calculatesEtaFromEffectiveKph()
	{
		OptionalDouble eta = KphCalculator.etaMillis(125, 125.0);
		assertTrue(eta.isPresent());
		assertEquals(3_600_000.0, eta.getAsDouble(), 0.001);
	}

	@Test
	public void calculatesCannonUsageAndRoundsTaskSupplyUp()
	{
		assertEquals(2.5, KphCalculator.cannonballsPerKill(250, 100).getAsDouble(), 0.001);
		OptionalDouble perUnit = KphCalculator.cannonballsPerTaskUnit(250, 125);
		assertEquals(2.0, perUnit.getAsDouble(), 0.001);
		assertEquals("301", KphCalculator.formatCannonballEstimate(
			KphCalculator.estimatedCannonballs(150, 2.001)));
	}

	@Test
	public void formatsDurations()
	{
		assertEquals("1h 30m", KphCalculator.formatDuration(OptionalDouble.of(5_400_000)));
		assertEquals("45m", KphCalculator.formatDuration(OptionalDouble.of(2_700_000)));
		assertEquals("1m 33s", KphCalculator.formatDuration(OptionalDouble.of(93_000)));
		assertEquals("42s", KphCalculator.formatDuration(OptionalDouble.of(42_000)));
		assertEquals("--", KphCalculator.formatDuration(OptionalDouble.empty()));
	}

	@Test
	public void formatsXpRatesCompactly()
	{
		assertEquals("66.5k", KphCalculator.formatXpRate(OptionalDouble.of(66_517.7)));
		assertEquals("1.25m", KphCalculator.formatXpRate(OptionalDouble.of(1_250_000)));
		assertEquals("950", KphCalculator.formatXpRate(OptionalDouble.of(950)));
		assertEquals("--", KphCalculator.formatXpRate(OptionalDouble.empty()));
	}

	@Test
	public void derivesConfidenceFromTaskAndProgressSamples()
	{
		assertEquals(Confidence.NO_DATA, Confidence.fromSample(0, 0));
		assertEquals(Confidence.LOW, Confidence.fromSample(1, 100));
		assertEquals(Confidence.MEDIUM, Confidence.fromSample(3, 300));
		assertEquals(Confidence.HIGH, Confidence.fromSample(5, 500));
	}
}
