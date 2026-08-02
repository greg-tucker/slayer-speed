package com.slayerspeed.tracking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SlayerXpTrackerTest
{
	@Test
	public void establishesBaselineAndReturnsOnlyPositiveDeltas()
	{
		SlayerXpTracker tracker = new SlayerXpTracker();
		assertFalse(tracker.observe(1_000).isPresent());
		assertEquals(150, tracker.observe(1_150).getAsInt());
		assertFalse(tracker.observe(1_100).isPresent());
	}

	@Test
	public void explicitBaselineCapturesTheFirstKill()
	{
		SlayerXpTracker tracker = new SlayerXpTracker();
		tracker.setBaseline(2_000);
		assertEquals(200, tracker.observe(2_200).getAsInt());
	}
}
