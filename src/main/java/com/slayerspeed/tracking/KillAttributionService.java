package com.slayerspeed.tracking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KillAttributionService
{
	private static final int CORRELATION_TICKS = 2;
	private static final int EXPIRY_TICKS = 4;

	private final Deque<CandidateDeath> candidates = new ArrayDeque<>();
	private final Deque<PendingXp> pendingXp = new ArrayDeque<>();
	private int lastTaskProgressTick = Integer.MIN_VALUE;

	public AttributionResult onCandidateDeath(Object identity, int npcId, String npcName, boolean interacted, int tick)
	{
		for (CandidateDeath existing : candidates)
		{
			if (existing.getNpcIdentity() == identity && withinWindow(existing.getDeathTick(), tick))
			{
				return AttributionResult.NONE;
			}
		}

		CandidateDeath candidate = new CandidateDeath(identity, npcId, npcName, tick, interacted);
		candidates.addLast(candidate);
		log.debug("Candidate Slayer death: {} ({}) on tick {}", npcName, npcId, tick);

		PendingXp xp = candidate.isInteracted() ? findPendingXp(tick) : null;
		if (xp != null)
		{
			pendingXp.remove(xp);
			candidate.setXpMatched();
			return confirm(candidate, xp.amount);
		}
		return AttributionResult.NONE;
	}

	public AttributionResult onSlayerXp(int xp, int tick)
	{
		return onSlayerXp(xp, tick, true);
	}

	public AttributionResult onSlayerXp(int xp, int tick, boolean allowTaskProgressCorrelation)
	{
		if (xp <= 0)
		{
			return AttributionResult.NONE;
		}
		CandidateDeath candidate = findCandidateForXp(tick);
		if (candidate == null)
		{
			if (allowTaskProgressCorrelation
				&& lastTaskProgressTick != Integer.MIN_VALUE
				&& withinWindow(lastTaskProgressTick, tick))
			{
				return new AttributionResult(0, xp);
			}
			pendingXp.addLast(new PendingXp(xp, tick));
			return AttributionResult.NONE;
		}
		candidate.setXpMatched();
		return confirm(candidate, xp);
	}

	public AttributionResult onTaskProgress(int progressUnits, int tick)
	{
		if (progressUnits > 0)
		{
			lastTaskProgressTick = tick;
		}
		int kills = 0;
		List<String> confirmedNpcNames = new ArrayList<>();
		List<CandidateDeath> batchCandidates = progressUnits > 0
			? findCandidatesForProgress(tick)
			: new ArrayList<>();
		if (!batchCandidates.isEmpty())
		{
			kills = Math.min(progressUnits, batchCandidates.size());
			CandidateDeath.ProgressBatch batch = new CandidateDeath.ProgressBatch(kills);
			for (CandidateDeath candidate : batchCandidates)
			{
				candidate.assignProgressBatch(batch);
			}
			for (int index = 0; index < kills; index++)
			{
				confirmedNpcNames.add(batchCandidates.get(index).getNpcName());
			}
			log.debug("Confirmed {} Slayer kills from {} simultaneous candidates and {} progress units",
				kills, batchCandidates.size(), progressUnits);
		}

		int xpAmount = 0;
		if (progressUnits > 0)
		{
			PendingXp xp = findPendingXp(tick);
			if (xp != null)
			{
				pendingXp.remove(xp);
				xpAmount = xp.amount;
			}
		}
		return kills == 0 && xpAmount == 0
			? AttributionResult.NONE
			: new AttributionResult(kills, xpAmount, confirmedNpcNames);
	}

	public AttributionResult onLoot(Object identity, int npcId, int tick)
	{
		CandidateDeath candidate = findCandidateForLoot(identity, npcId, tick);
		if (candidate != null)
		{
			candidate.setLootMatched();
			PendingXp xp = !candidate.isXpMatched() ? findPendingXp(tick) : null;
			if (xp != null)
			{
				pendingXp.remove(xp);
				candidate.setXpMatched();
			}
			return confirm(candidate, xp == null ? 0 : xp.amount);
		}
		return AttributionResult.NONE;
	}

	public void advanceTick(int tick)
	{
		candidates.removeIf(candidate -> tick - candidate.getDeathTick() > EXPIRY_TICKS);
		pendingXp.removeIf(xp -> tick - xp.tick > EXPIRY_TICKS);
	}

	public void reset()
	{
		candidates.clear();
		pendingXp.clear();
		lastTaskProgressTick = Integer.MIN_VALUE;
	}

	public int consumePendingXpForCompletion(int tick)
	{
		PendingXp xp = findPendingXp(tick);
		if (xp == null)
		{
			return 0;
		}
		pendingXp.remove(xp);
		return xp.amount;
	}

	private AttributionResult confirm(CandidateDeath candidate, int xp)
	{
		int kills = 0;
		if (!candidate.isConfirmed() && candidate.shouldConfirm())
		{
			candidate.confirm();
			kills = candidate.hasProgressBatch()
				? (candidate.recordBatchEvidence() ? 1 : 0)
				: 1;
			log.debug("Confirmed Slayer kill: {}", candidate.getNpcName());
		}
		return kills == 0 && xp == 0
			? AttributionResult.NONE
			: new AttributionResult(
				kills,
				xp,
				kills > 0 ? Collections.singletonList(candidate.getNpcName()) : Collections.emptyList());
	}

	private CandidateDeath findCandidateForXp(int tick)
	{
		CandidateDeath onlyInteractedCandidate = null;
		int interactedCount = 0;
		Iterator<CandidateDeath> iterator = candidates.descendingIterator();
		while (iterator.hasNext())
		{
			CandidateDeath candidate = iterator.next();
			if (!candidate.isXpMatched() && withinWindow(candidate.getDeathTick(), tick))
			{
				if (candidate.isInteracted())
				{
					interactedCount++;
					onlyInteractedCandidate = candidate;
				}
			}
		}
		if (interactedCount == 1)
		{
			return onlyInteractedCandidate;
		}
		return null;
	}

	private PendingXp findPendingXp(int tick)
	{
		Iterator<PendingXp> iterator = pendingXp.descendingIterator();
		while (iterator.hasNext())
		{
			PendingXp xp = iterator.next();
			if (withinWindow(xp.tick, tick))
			{
				return xp;
			}
		}
		return null;
	}

	private List<CandidateDeath> findCandidatesForProgress(int tick)
	{
		List<CandidateDeath> matches = new ArrayList<>();
		Iterator<CandidateDeath> iterator = candidates.descendingIterator();
		while (iterator.hasNext())
		{
			CandidateDeath candidate = iterator.next();
			if (!candidate.isConfirmed()
				&& !candidate.hasProgressBatch()
				&& withinWindow(candidate.getDeathTick(), tick))
			{
				matches.add(candidate);
			}
		}
		return matches;
	}

	private CandidateDeath findCandidateForLoot(Object identity, int npcId, int tick)
	{
		Iterator<CandidateDeath> iterator = candidates.descendingIterator();
		while (iterator.hasNext())
		{
			CandidateDeath candidate = iterator.next();
			if (candidate.getNpcIdentity() == identity && withinWindow(candidate.getDeathTick(), tick))
			{
				return candidate;
			}
		}

		if (identity == null)
		{
			iterator = candidates.descendingIterator();
			while (iterator.hasNext())
			{
				CandidateDeath candidate = iterator.next();
				if (candidate.getNpcId() == npcId && withinWindow(candidate.getDeathTick(), tick))
				{
					return candidate;
				}
			}
		}
		return null;
	}

	private static boolean withinWindow(int firstTick, int secondTick)
	{
		return Math.abs(firstTick - secondTick) <= CORRELATION_TICKS;
	}

	private static final class PendingXp
	{
		private final int amount;
		private final int tick;

		private PendingXp(int amount, int tick)
		{
			this.amount = amount;
			this.tick = tick;
		}
	}
}
