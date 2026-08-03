package com.slayerspeed.tracking;

import javax.inject.Inject;

/** Tracks fired cannon ammo while excluding reloads and ammunition returned to inventory. */
public class CannonballTracker
{
	private int lastLoaded = -1;
	private int lastInventoryCannonballs = -1;
	private boolean cannonPlaced;
	private int pendingConsumed;

	@Inject
	public CannonballTracker()
	{
	}

	public void reset(int loaded, boolean placed, int inventoryCannonballs)
	{
		lastLoaded = Math.max(0, loaded);
		lastInventoryCannonballs = Math.max(0, inventoryCannonballs);
		cannonPlaced = placed;
		pendingConsumed = 0;
	}

	public void clear()
	{
		lastLoaded = -1;
		lastInventoryCannonballs = -1;
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
	 * Commits decreases after the tick's state updates have settled. Reaching zero while
	 * ammunition returns to inventory indicates Empty; only any unmatched decrease was fired.
	 * Picking up the cannon is excluded separately by the placed state.
	 */
	public int drainConsumed(boolean trackingEligibleTask, int inventoryCannonballs)
	{
		int currentInventoryCannonballs = Math.max(0, inventoryCannonballs);
		int loadedDecrease = pendingConsumed;
		int consumed = cannonPlaced && trackingEligibleTask ? loadedDecrease : 0;
		if (consumed > 0 && lastLoaded == 0 && lastInventoryCannonballs >= 0)
		{
			int returned = Math.max(0, currentInventoryCannonballs - lastInventoryCannonballs);
			consumed = Math.max(0, consumed - Math.min(loadedDecrease, returned));
		}
		pendingConsumed = 0;
		lastInventoryCannonballs = currentInventoryCannonballs;
		return consumed;
	}
}
