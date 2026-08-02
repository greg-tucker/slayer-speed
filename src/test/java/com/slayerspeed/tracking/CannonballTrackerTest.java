package com.slayerspeed.tracking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CannonballTrackerTest
{
	@Test
	public void countsAmmoDecreasesButNotReloads()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true);

		tracker.observeLoaded(29, true);
		assertEquals(1, tracker.drainConsumed(true));
		tracker.observeLoaded(60, true);
		assertEquals(0, tracker.drainConsumed(true));
		tracker.observeLoaded(58, true);
		assertEquals(2, tracker.drainConsumed(true));
	}

	@Test
	public void alwaysRebaselinesWhenTrackingIsNotEligible()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true);

		tracker.observeLoaded(25, false);
		assertEquals(0, tracker.drainConsumed(false));
		tracker.observeLoaded(24, true);
		assertEquals(1, tracker.drainConsumed(true));
		tracker.setCannonPlaced(false);
		tracker.observeLoaded(0, true);
		assertEquals(0, tracker.drainConsumed(true));
	}

	@Test
	public void discardsPendingDecreaseWhenCannonIsPickedUpInTheSameTick()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true);
		tracker.observeLoaded(0, true);
		tracker.setCannonPlaced(false);

		assertEquals(0, tracker.drainConsumed(true));
	}
}
