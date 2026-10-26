package com.slayerspeed.ui;

import java.util.Arrays;

final class SlayerSpeedTestModels
{
    private SlayerSpeedTestModels() {}
	static SlayerSpeedViewModel sampleModel(
		boolean historyAvailable, boolean cannonRelevant, boolean manualSelection)
	{
		return new SlayerSpeedViewModel(
			true,
			"Araxytes",
			"12",
			"43 kills · 43 task units",
			"465.5",
			"465.5",
			false,
			"66.5k",
			"1m 33s",
			historyAvailable ? "8% faster" : "",
			"11:57",
			historyAvailable,
			historyAvailable ? "460.1" : "--",
			historyAvailable ? "461.0" : "--",
			historyAvailable ? "64.2k" : "--",
			historyAvailable ? "28m" : "--",
			historyAvailable
				? "<html><center>3 full tasks<br>650 observed units</center></html>"
				: "<html><center>First task<br>Average ready<br>after completion</center></html>",
			historyAvailable ? "Medium" : "No data",
			cannonRelevant,
			cannonRelevant ? "312" : "0",
			"This task / kill",
			cannonRelevant ? "7.3" : "--",
			cannonRelevant ? "314" : "--",
			cannonRelevant ? "88" : "--",
			null,
			true,
			historyAvailable ? "Araxxor (boss)" : "Araxytes (regular)",
			manualSelection ? "Manual estimate for this task" : "Auto-detected from confirmed kills",
			manualSelection ? "boss:araxxor" : EncounterProfileOption.AUTO_ID,
			Arrays.asList(
				new EncounterProfileOption(
					EncounterProfileOption.AUTO_ID,
					historyAvailable ? "Auto: Araxxor (boss)" : "Auto: Araxytes (regular)"),
				new EncounterProfileOption("npc:araxyte", "Araxytes (regular)"),
				new EncounterProfileOption("boss:araxxor", "Araxxor (boss)")),
            new com.slayerspeed.calculation.TaskEstimateService().calculate(12, 43, 100000,
                historyAvailable ? 650 : 0, historyAvailable ? 2000000 : 0, 10, true,
                historyAvailable ? 3 : 0, "Legacy lifetime history"));
	}
}
