package com.slayerspeed.mortimer;

import com.google.gson.Gson;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.persistence.TaskHistoryRepository;
import com.slayerspeed.tracking.EncounterProfileResolver;
import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MortimerEstimateServiceTest
{
	@Test
	public void keepsRegularAndBossTimeRangesSeparate()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		repository.saveRun(run(
			"regular", "Araxytes", "npc:araxyte", "Araxytes (regular)", 100, 3_600_000L, 1),
			true, 50);
		repository.saveRun(run(
			"boss", "Araxytes", "boss:araxxor", "Araxxor (boss)", 20, 3_600_000L, 2),
			true, 50);
		MortimerEstimateService service = new MortimerEstimateService(
			repository, new EncounterProfileResolver());

		MortimerTaskEstimate estimate = service.estimate(
			new MortimerTaskOffer("Araxytes", 100, 150, new Rectangle()));

		assertTrue(estimate.hasData());
		assertEquals(2, estimate.getProfiles().size());
		assertEquals("Araxxor", estimate.getProfiles().get(0).getProfileName());
		assertEquals("5h-7h 30m", estimate.getProfiles().get(0).getDurationRange());
		assertEquals("Araxytes", estimate.getProfiles().get(1).getProfileName());
		assertEquals("1h-1h 30m", estimate.getProfiles().get(1).getDurationRange());
	}

	@Test
	public void resolvesMortimerBloodveldsToRuneLiteHistoryName()
	{
		TaskHistoryRepository repository = new TaskHistoryRepository(null, new Gson());
		repository.saveRun(run(
			"bloodveld", "Bloodveld", "npc:bloodveld", "Bloodveld", 100, 3_600_000L, 1),
			true, 50);
		MortimerEstimateService service = new MortimerEstimateService(
			repository, new EncounterProfileResolver());

		MortimerTaskEstimate estimate = service.estimate(
			new MortimerTaskOffer("Bloodvelds", 150, 200, new Rectangle()));

		assertTrue(estimate.hasData());
		assertEquals("1h 30m-2h", estimate.getProfiles().get(0).getDurationRange());
		assertEquals("Bloodveld", MortimerEstimateService.historyTaskName(" Bloodvelds "));
	}

	@Test
	public void returnsNoDataForUnseenTasks()
	{
		MortimerEstimateService service = new MortimerEstimateService(
			new TaskHistoryRepository(null, new Gson()), new EncounterProfileResolver());

		MortimerTaskEstimate estimate = service.estimate(
			new MortimerTaskOffer("Venators", 120, 180, new Rectangle()));

		assertFalse(estimate.hasData());
		assertTrue(estimate.getProfiles().isEmpty());
	}

	private static TaskRun run(
		String id,
		String taskName,
		String profileId,
		String profileName,
		int amount,
		long activeMillis,
		long completedAt)
	{
		return new TaskRun(
			id, taskName, null, profileId, profileName, amount, 0, true,
			amount, amount, amount * 100, 0, 0, activeMillis, 0, 0, completedAt,
			TaskRunStatus.COMPLETED);
	}
}
