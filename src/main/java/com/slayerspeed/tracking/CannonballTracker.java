package com.slayerspeed.tracking;

import javax.inject.Inject;

/** Tracks the player's loaded cannon ammo without treating reloads as consumption. */
public class CannonballTracker
{
	private int lastLoaded = -1;
	private boolean cannonPlaced;
	private int pendingConsumed;

	@Inject
	public CannonballTracker()
	{
	}

	public void reset(int loaded, boolean placed)
	{
		lastLoaded = Math.max(0, loaded);
		cannonPlaced = placed;
		pendingConsumed = 0;
	}

	public void clear()
	{
		lastLoaded = -1;
		cannonPlaced = false;
		pendingConsumed = 0;
	}

	public void setCannonPlaced(boolean placed)
	{
		cannonPlaced = placed;
	}

	public void observeLoaded(int loaded, boolean trackingEligibleTask)
	{
		int current = Math.max(0, loaded);
		if (lastLoaded >= 0 && cannonPlaced && trackingEligibleTask && current < lastLoaded)
		{
			pendingConsumed += lastLoaded - current;
		}
		lastLoaded = current;
	}

	/**
	 * Commits decreases after the tick's varp updates have settled. This prevents picking up
	 * a loaded cannon (which can clear its ammo varp) from looking like ammunition was fired.
	 */
	public int drainConsumed(boolean trackingEligibleTask)
	{
		int consumed = cannonPlaced && trackingEligibleTask ? pendingConsumed : 0;
		pendingConsumed = 0;
		return consumed;
	}
}
