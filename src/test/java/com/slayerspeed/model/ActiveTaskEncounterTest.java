package com.slayerspeed.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActiveTaskEncounterTest
{
	@Test
	public void autoProfileFollowsTheDominantConfirmedMonsterWithoutCombatLevels()
	{
		ActiveTask task = new ActiveTask("Araxytes", null, 50, 50, 1L);
		EncounterProfile regular = new EncounterProfile("npc:araxyte", "Araxytes (regular)");
		EncounterProfile boss = new EncounterProfile("boss:araxxor", "Araxxor (boss)");

		task.observeEncounter(regular);
		task.observeEncounter(boss);
		task.observeEncounter(regular);

		assertEquals("npc:araxyte", task.getDetectedEncounterProfileId());
		assertEquals("npc:araxyte", task.finish(TaskRunStatus.COMPLETED, 2L).getEncounterProfileId());
	}

	@Test
	public void manualProfileOverridesAutoForTheCurrentRun()
	{
		ActiveTask task = new ActiveTask("Araxytes", null, 50, 50, 1L);
		task.observeEncounter(new EncounterProfile("npc:araxyte", "Araxytes (regular)"));
		task.setManualEncounterProfile(new EncounterProfile("boss:araxxor", "Araxxor (boss)"));

		TaskRun run = task.finish(TaskRunStatus.COMPLETED, 2L);

		assertEquals("boss:araxxor", run.getEncounterProfileId());
		assertEquals("Araxxor (boss)", run.getEncounterProfileName());
	}
}
