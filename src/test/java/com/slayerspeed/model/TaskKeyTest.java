package com.slayerspeed.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TaskKeyTest
{
	@Test
	public void normalizesCaseAndWhitespace()
	{
		assertEquals(
			new TaskKey("Abyssal   Demons", "  Slayer Tower "),
			new TaskKey("abyssal demons", "slayer tower"));
	}

	@Test
	public void treatsBlankAndNullLocationsAsEquivalent()
	{
		assertEquals(new TaskKey("Gargoyles", null), new TaskKey("gargoyles", "  "));
	}

	@Test
	public void separatesDifferentLocations()
	{
		assertNotEquals(
			new TaskKey("Bloodvelds", "Slayer Tower"),
			new TaskKey("Bloodvelds", "Stronghold Slayer Cave"));
	}

	@Test
	public void separatesEncounterProfilesWithoutUsingCombatLevel()
	{
		TaskKey regular = new TaskKey("Araxytes", null, "npc:araxyte");
		TaskKey boss = new TaskKey("Araxytes", null, "boss:araxxor");

		assertNotEquals(regular, boss);
		assertEquals("araxytes|target:npc:araxyte", regular.asStorageKey());
	}
}
