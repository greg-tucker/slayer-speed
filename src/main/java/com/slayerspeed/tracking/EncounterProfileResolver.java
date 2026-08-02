package com.slayerspeed.tracking;

import com.slayerspeed.model.EncounterProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EncounterProfileResolver
{
	private static final Map<String, LinkedHashMap<String, EncounterProfile>> DETECTION_BY_TASK =
		new LinkedHashMap<>();
	private static final Map<String, List<EncounterProfile>> SUGGESTIONS_BY_TASK = new LinkedHashMap<>();
	private static final Set<String> SUPERIOR_NAMES = new LinkedHashSet<>();

	static
	{
		add("abyssal demons", "npc:abyssal-demon", "Abyssal demons (regular)", "abyssal demon");
		add("abyssal demons", "boss:abyssal-sire", "Abyssal Sire (boss)", "the abyssal sire", "abyssal sire");

		add("araxytes", "npc:araxyte", "Araxytes (regular)", "araxyte");
		add("araxytes", "boss:araxxor", "Araxxor (boss)", "araxxor");

		add("black dragons", "npc:black-dragon", "Black dragons", "black dragon", "baby black dragon");
		add("black dragons", "npc:brutal-black-dragon", "Brutal black dragons", "brutal black dragon");
		add("black dragons", "boss:king-black-dragon", "King Black Dragon (boss)", "king black dragon");

		add("blue dragons", "npc:blue-dragon", "Blue dragons", "blue dragon", "baby blue dragon");
		add("blue dragons", "npc:brutal-blue-dragon", "Brutal blue dragons", "brutal blue dragon");
		add("blue dragons", "boss:vorkath", "Vorkath (boss)", "vorkath");

		add("dagannoth", "group:dagannoth", "Dagannoths (regular)", "dagannoth", "dagannoth spawn");
		add("dagannoth", "boss:dagannoth-kings", "Dagannoth Kings (boss)",
			"dagannoth rex", "dagannoth prime", "dagannoth supreme");

		add("gargoyles", "npc:gargoyle", "Gargoyles (regular)", "gargoyle");
		add("gargoyles", "boss:grotesque-guardians", "Grotesque Guardians (boss)", "dawn", "dusk");

		add("greater demons", "npc:greater-demon", "Greater demons", "greater demon");
		add("greater demons", "npc:tormented-demon", "Tormented demons", "tormented demon");
		add("greater demons", "boss:kril-tsutsaroth", "K'ril Tsutsaroth (boss)", "k'ril tsutsaroth");
		add("greater demons", "boss:skotizo", "Skotizo (boss)", "skotizo");

		add("hellhounds", "npc:hellhound", "Hellhounds (regular)", "hellhound");
		add("hellhounds", "boss:cerberus", "Cerberus (boss)", "cerberus");

		add("hydras", "npc:hydra", "Hydras (regular)", "hydra");
		add("hydras", "boss:alchemical-hydra", "Alchemical Hydra (boss)", "alchemical hydra");

		add("kalphite", "group:kalphite", "Kalphites (regular)",
			"kalphite worker", "kalphite soldier", "kalphite guardian");
		add("kalphite", "boss:kalphite-queen", "Kalphite Queen (boss)", "kalphite queen");

		addKrakenTask("kraken");
		addKrakenTask("krakens");

		add("smoke devils", "npc:smoke-devil", "Smoke devils (regular)", "smoke devil");
		add("smoke devils", "boss:thermonuclear-smoke-devil", "Thermonuclear smoke devil (boss)",
			"thermonuclear smoke devil");

		Collections.addAll(SUPERIOR_NAMES,
			"choke devil", "night beast", "greater abyssal demon", "marble gargoyle",
			"nechryarch", "crushing hand", "chasm crawler", "screaming banshee",
			"screaming twisted banshee", "giant rockslug", "cockathrice", "flaming pyrelord",
			"monstrous basilisk", "malevolent mage", "insatiable bloodveld",
			"insatiable mutated bloodveld", "vitreous jelly", "vitreous warped jelly",
			"cave abomination", "abhorrent spectre", "repugnant spectre", "shadow wyrm");
	}

	public EncounterProfile resolve(String taskName, String npcName)
	{
		String normalizedNpc = normalize(npcName);
		if (normalizedNpc.isEmpty() || SUPERIOR_NAMES.contains(normalizedNpc))
		{
			return null;
		}

		Map<String, EncounterProfile> taskProfiles = DETECTION_BY_TASK.get(normalize(taskName));
		if (taskProfiles != null)
		{
			EncounterProfile known = taskProfiles.get(normalizedNpc);
			if (known != null)
			{
				return known;
			}
		}
		return new EncounterProfile("npc:" + normalizedNpc.replace(' ', '-'), cleanDisplayName(npcName));
	}

	public List<EncounterProfile> suggestionsForTask(String taskName)
	{
		List<EncounterProfile> suggestions = SUGGESTIONS_BY_TASK.get(normalize(taskName));
		return suggestions == null ? Collections.emptyList() : suggestions;
	}

	private static void addKrakenTask(String taskName)
	{
		add(taskName, "npc:cave-kraken", "Cave krakens (regular)", "cave kraken");
		add(taskName, "boss:kraken", "Kraken (boss)", "kraken");
	}

	private static void add(String taskName, String id, String displayName, String... npcNames)
	{
		String task = normalize(taskName);
		EncounterProfile profile = new EncounterProfile(id, displayName);
		LinkedHashMap<String, EncounterProfile> detection = DETECTION_BY_TASK.computeIfAbsent(
			task, ignored -> new LinkedHashMap<>());
		for (String npcName : npcNames)
		{
			detection.put(normalize(npcName), profile);
		}
		List<EncounterProfile> suggestions = SUGGESTIONS_BY_TASK.computeIfAbsent(
			task, ignored -> new ArrayList<>());
		if (!suggestions.contains(profile))
		{
			suggestions.add(profile);
		}
	}

	private static String normalize(String value)
	{
		return value == null
			? ""
			: value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
	}

	private static String cleanDisplayName(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "Unknown monster";
		}
		return value.trim().replaceAll("\\s+", " ");
	}
}
