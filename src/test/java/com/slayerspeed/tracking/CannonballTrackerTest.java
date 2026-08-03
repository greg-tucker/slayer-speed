package com.slayerspeed.tracking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CannonballTrackerTest
{
	@Test
	public void countsAmmoDecreasesButNotReloads()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);

		tracker.observeLoaded(29, true);
		assertEquals(1, tracker.drainConsumed(true, 100));
		tracker.observeLoaded(60, true);
		assertEquals(0, tracker.drainConsumed(true, 70));
		tracker.observeLoaded(58, true);
		assertEquals(2, tracker.drainConsumed(true, 70));
	}

	@Test
	public void alwaysRebaselinesWhenTrackingIsNotEligible()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);

		tracker.observeLoaded(25, false);
		assertEquals(0, tracker.drainConsumed(false, 100));
		tracker.observeLoaded(24, true);
		assertEquals(1, tracker.drainConsumed(true, 100));
		tracker.setCannonPlaced(false);
		tracker.observeLoaded(0, true);
		assertEquals(0, tracker.drainConsumed(true, 124));
	}

	@Test
	public void discardsPendingDecreaseWhenCannonIsPickedUpInTheSameTick()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);
		tracker.observeLoaded(0, true);
		tracker.setCannonPlaced(false);

		assertEquals(0, tracker.drainConsumed(true, 130));
	}

	@Test
	public void doesNotCountCannonballsReturnedByEmptying()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);

		tracker.observeLoaded(0, true);

		assertEquals(0, tracker.drainConsumed(true, 130));
	}

	@Test
	public void countsShotsThatOccurWhileEmptying()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);

		tracker.observeLoaded(0, true);

		assertEquals(1, tracker.drainConsumed(true, 129));
	}

	@Test
	public void doesNotTreatInventoryIncreaseAsReturnedAmmoUntilCannonReachesZero()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(30, true, 100);

		tracker.observeLoaded(29, true);

		assertEquals(1, tracker.drainConsumed(true, 101));
	}

	@Test
	public void countsShotsWhenCannonRunsOutWithoutReturningAmmo()
	{
		CannonballTracker tracker = new CannonballTracker();
		tracker.reset(2, true, 100);

		tracker.observeLoaded(0, true);

		assertEquals(2, tracker.drainConsumed(true, 100));
	}
}
