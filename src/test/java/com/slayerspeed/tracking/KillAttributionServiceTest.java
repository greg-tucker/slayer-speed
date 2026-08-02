package com.slayerspeed.tracking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KillAttributionServiceTest
{
	@Test
	public void xpConfirmsOneLiteralKillWithoutAssumingProgressUnits()
	{
		KillAttributionService service = new KillAttributionService();
		Object npc = new Object();

		service.onCandidateDeath(npc, 1, "Bloodveld", true, 100);
		AttributionResult xp = service.onSlayerXp(120, 100);
		AttributionResult progress = service.onTaskProgress(2, 100);

		assertEquals(1, xp.getLiteralKills());
		assertEquals(120, xp.getSlayerXp());
		assertEquals(0, progress.getLiteralKills());
	}

	@Test
	public void supportsXpArrivingBeforeDeath()
	{
		KillAttributionService service = new KillAttributionService();
		service.onSlayerXp(150, 200);
		AttributionResult result = service.onCandidateDeath(new Object(), 2, "Gargoyle", true, 201);

		assertEquals(1, result.getLiteralKills());
		assertEquals(150, result.getSlayerXp());
	}

	@Test
	public void taskProgressConsumesPendingXpWithoutADeathCandidate()
	{
		KillAttributionService service = new KillAttributionService();
		service.onSlayerXp(150, 200);

		AttributionResult result = service.onTaskProgress(1, 201);

		assertEquals(0, result.getLiteralKills());
		assertEquals(150, result.getSlayerXp());
	}

	@Test
	public void xpCanFollowRecentTaskProgress()
	{
		KillAttributionService service = new KillAttributionService();
		service.onTaskProgress(1, 200);

		AttributionResult result = service.onSlayerXp(150, 201);

		assertEquals(150, result.getSlayerXp());
	}

	@Test
	public void completionXpCanBypassProgressCorrelationAndBeConsumedAsBonus()
	{
		KillAttributionService service = new KillAttributionService();
		service.onTaskProgress(1, 200);

		AttributionResult result = service.onSlayerXp(5_000, 201, false);

		assertEquals(0, result.getSlayerXp());
		assertEquals(5_000, service.consumePendingXpForCompletion(201));
	}

	@Test
	public void ambiguousXpPrefersTheInteractedCandidate()
	{
		KillAttributionService service = new KillAttributionService();
		Object localNpc = new Object();
		Object otherNpc = new Object();
		service.onCandidateDeath(localNpc, 1, "Bloodveld", true, 100);
		service.onCandidateDeath(otherNpc, 1, "Bloodveld", false, 100);

		AttributionResult xp = service.onSlayerXp(120, 100);
		AttributionResult localLoot = service.onLoot(localNpc, 1, 101);

		assertEquals(1, xp.getLiteralKills());
		assertEquals(0, localLoot.getLiteralKills());
	}

	@Test
	public void lootUsesNpcIdentityBeforeSharedNpcId()
	{
		KillAttributionService service = new KillAttributionService();
		Object localNpc = new Object();
		service.onCandidateDeath(localNpc, 1, "Bloodveld", false, 100);
		service.onCandidateDeath(new Object(), 1, "Bloodveld", false, 100);

		AttributionResult result = service.onLoot(localNpc, 1, 101);

		assertEquals(1, result.getLiteralKills());
	}

	@Test
	public void duplicateDeathEventDoesNotCreateTwoCandidates()
	{
		KillAttributionService service = new KillAttributionService();
		Object npc = new Object();
		service.onCandidateDeath(npc, 1, "Bloodveld", true, 100);
		service.onCandidateDeath(npc, 1, "Bloodveld", true, 100);

		AttributionResult result = service.onTaskProgress(2, 101);

		assertEquals(1, result.getLiteralKills());
	}

	@Test
	public void progressBatchCountsOnlyTheNumberOfSupportedKills()
	{
		KillAttributionService service = new KillAttributionService();
		service.onCandidateDeath(new Object(), 1, "Bloodveld", true, 100);
		service.onCandidateDeath(new Object(), 1, "Bloodveld", true, 100);

		AttributionResult result = service.onTaskProgress(1, 101);

		assertEquals(1, result.getLiteralKills());
	}

	@Test
	public void simultaneousDeathsAreCountedAsABatchWithoutXpDuplicates()
	{
		KillAttributionService service = new KillAttributionService();
		Object first = new Object();
		Object second = new Object();
		service.onCandidateDeath(first, 1, "Araxyte", false, 100);
		service.onCandidateDeath(second, 1, "Araxyte", false, 100);

		AttributionResult progress = service.onTaskProgress(2, 101);
		AttributionResult firstLoot = service.onLoot(first, 1, 101);
		AttributionResult secondLoot = service.onLoot(second, 1, 101);

		assertEquals(2, progress.getLiteralKills());
		assertEquals(0, firstLoot.getLiteralKills());
		assertEquals(0, secondLoot.getLiteralKills());
	}

	@Test
	public void laterEvidenceCanAddABraceletPreventedBatchKill()
	{
		KillAttributionService service = new KillAttributionService();
		Object first = new Object();
		Object second = new Object();
		service.onCandidateDeath(first, 1, "Araxyte", false, 100);
		service.onCandidateDeath(second, 1, "Araxyte", false, 100);

		AttributionResult progress = service.onTaskProgress(1, 101);
		AttributionResult firstLoot = service.onLoot(first, 1, 101);
		AttributionResult secondLoot = service.onLoot(second, 1, 101);

		assertEquals(1, progress.getLiteralKills());
		assertEquals(0, firstLoot.getLiteralKills());
		assertEquals(1, secondLoot.getLiteralKills());
	}

	@Test
	public void interactionAndProgressCanConfirmKillWithoutLoot()
	{
		KillAttributionService service = new KillAttributionService();
		service.onCandidateDeath(new Object(), 3, "Dust devil", true, 300);
		AttributionResult result = service.onTaskProgress(1, 301);

		assertEquals(1, result.getLiteralKills());
	}

	@Test
	public void isolatedXpIsNotAttributed()
	{
		KillAttributionService service = new KillAttributionService();
		AttributionResult result = service.onSlayerXp(10_000, 400);
		service.advanceTick(405);

		assertEquals(0, result.getLiteralKills());
		assertEquals(0, result.getSlayerXp());
	}
}
