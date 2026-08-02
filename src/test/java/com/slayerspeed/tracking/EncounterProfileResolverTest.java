package com.slayerspeed.tracking;

import com.slayerspeed.model.EncounterProfile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EncounterProfileResolverTest
{
	private final EncounterProfileResolver resolver = new EncounterProfileResolver();

	@Test
	public void separatesAraxxorFromRegularAraxytes()
	{
		EncounterProfile regular = resolver.resolve("Araxytes", "Araxyte");
		EncounterProfile boss = resolver.resolve("Araxytes", "Araxxor");

		assertEquals("npc:araxyte", regular.getId());
		assertEquals("boss:araxxor", boss.getId());
		assertEquals(2, resolver.suggestionsForTask("Araxytes").size());
	}

	@Test
	public void groupsDagannothCombatLevelsButSeparatesTheKings()
	{
		EncounterProfile level74 = resolver.resolve("Dagannoth", "Dagannoth");
		EncounterProfile level92 = resolver.resolve("Dagannoth", "Dagannoth");
		EncounterProfile rex = resolver.resolve("Dagannoth", "Dagannoth Rex");
		EncounterProfile prime = resolver.resolve("Dagannoth", "Dagannoth Prime");

		assertEquals(level74, level92);
		assertEquals("group:dagannoth", level74.getId());
		assertEquals(rex, prime);
		assertEquals("boss:dagannoth-kings", rex.getId());
	}

	@Test
	public void groupsMultiNameCannonFamilies()
	{
		assertEquals(
			resolver.resolve("Kalphites", "Kalphite Worker"),
			resolver.resolve("Kalphites", "Kalphite Guardian"));
	}

	@Test
	public void supportsCurrentRuneLiteAssignmentLabels()
	{
		assertEquals(2, resolver.suggestionsForTask("Kalphites").size());
		assertEquals(2, resolver.suggestionsForTask("Cave kraken").size());
		assertEquals(
			"boss:dagannoth-kings",
			resolver.resolve("Dagannoth Kings", "Dagannoth Supreme").getId());
	}

	@Test
	public void superiorKillsDoNotChangeTheEncounterProfile()
	{
		assertNull(resolver.resolve("Bloodvelds", "Insatiable bloodveld"));
	}

	@Test
	public void unknownMonsterNamesStillCreateStableNameBasedProfiles()
	{
		EncounterProfile first = resolver.resolve("Bloodvelds", "Mutated Bloodveld");
		EncounterProfile second = resolver.resolve("Bloodvelds", "  mutated   bloodveld ");

		assertEquals(first, second);
		assertEquals("npc:mutated-bloodveld", first.getId());
	}
}
