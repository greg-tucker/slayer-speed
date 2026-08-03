package com.slayerspeed.tracking;

import javax.inject.Inject;

/** Tracks the player's loaded cannon ammo without treating reloads as consumption. */
public class CannonballTracker
{
	private static final int EMPTY_CORRELATION_TICKS = 2;

	private int lastLoaded = -1;
	private boolean cannonPlaced;
	private int pendingConsumed;
	private int emptyInventoryBaseline = -1;
	private int emptyInteractionTick = Integer.MIN_VALUE;

	@Inject
	public CannonballTracker()
	{
	}

	public void reset(int loaded, boolean placed)
	{
		lastLoaded = Math.max(0, loaded);
		cannonPlaced = placed;
		pendingConsumed = 0;
		clearEmptying();
	}

	public void clear()
	{
		lastLoaded = -1;
		cannonPlaced = false;
		pendingConsumed = 0;
		clearEmptying();
	}

	public void setCannonPlaced(boolean placed)
	{
		cannonPlaced = placed;
	}

	public void beginEmptying(int inventoryCannonballs, int tick)
	{
		if (!cannonPlaced || lastLoaded <= 0)
		{
			return;
		}
		emptyInventoryBaseline = Math.max(0, inventoryCannonballs);
		emptyInteractionTick = tick;
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
	public int drainConsumed(boolean trackingEligibleTask, int inventoryCannonballs, int tick)
	{
		int loadedDecrease = pendingConsumed;
		int consumed = cannonPlaced && trackingEligibleTask ? loadedDecrease : 0;
		if (consumed > 0 && isRecentEmptyInteraction(tick))
		{
			int returned = Math.max(0, inventoryCannonballs - emptyInventoryBaseline);
			consumed = Math.max(0, consumed - Math.min(loadedDecrease, returned));
		}
		pendingConsumed = 0;
		if (loadedDecrease > 0 || isEmptyInteractionExpired(tick))
		{
			clearEmptying();
		}
		return consumed;
	}

	private boolean isRecentEmptyInteraction(int tick)
	{
		if (emptyInteractionTick == Integer.MIN_VALUE)
		{
			return false;
		}
		long elapsedTicks = (long) tick - emptyInteractionTick;
		return elapsedTicks >= 0 && elapsedTicks <= EMPTY_CORRELATION_TICKS;
	}

	private boolean isEmptyInteractionExpired(int tick)
	{
		return emptyInteractionTick != Integer.MIN_VALUE
			&& (long) tick - emptyInteractionTick > EMPTY_CORRELATION_TICKS;
	}

	private void clearEmptying()
	{
		emptyInventoryBaseline = -1;
		emptyInteractionTick = Integer.MIN_VALUE;
	}
}
