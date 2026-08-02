package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedDisplayMode;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SlayerSpeedPanelRenderTest
{
	@Test
	public void rendersSimpleAndDetailedPanelsAtRuneLiteWidth() throws Exception
	{
		Path output = Paths.get("build", "ux-review");
		Files.createDirectories(output);
		render(output.resolve("simple.png"), new SlayerSpeedConfig()
		{
		}, false, false, false);
		render(output.resolve("detailed.png"), new SlayerSpeedConfig()
		{
			@Override
			public SlayerSpeedDisplayMode displayMode()
			{
				return SlayerSpeedDisplayMode.DETAILED;
			}
		}, true, true, false);
		render(output.resolve("manual.png"), new SlayerSpeedConfig()
		{
		}, true, false, true);
		assertTrue(Files.size(output.resolve("simple.png")) > 0L);
		assertTrue(Files.size(output.resolve("detailed.png")) > 0L);
		assertTrue(Files.size(output.resolve("manual.png")) > 0L);
	}

	private static void render(Path output, SlayerSpeedConfig config, boolean historyAvailable,
		boolean cannonRelevant, boolean manualSelection) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> { }, () -> { },
					(run, excluded) -> { }, run -> { }, profileId -> { }, config);
				assertTrue(hasNamedButton(panel, "storedStatsDebugButton"));
				Collection<TaskStatistics> history = historyAvailable
					? sampleHistory()
					: Collections.emptyList();
				panel.update(sampleModel(historyAvailable, cannonRelevant, manualSelection), history);
				int height = Math.max(720, panel.getPreferredSize().height);
				panel.setSize(new Dimension(242, height));
				layoutRecursively(panel);
				BufferedImage image = new BufferedImage(242, height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				panel.printAll(graphics);
				graphics.dispose();
				ImageIO.write(image, "png", output.toFile());
			}
			catch (Exception ex)
			{
				throw new RuntimeException(ex);
			}
		});
	}

	private static boolean hasNamedButton(Container container, String name)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof JButton && name.equals(component.getName()))
			{
				return true;
			}
			if (component instanceof Container && hasNamedButton((Container) component, name))
			{
				return true;
			}
		}
		return false;
	}

	private static Collection<TaskStatistics> sampleHistory()
	{
		TaskStatistics statistics = new TaskStatistics(
			"Araxytes", null, "boss:araxxor", "Araxxor (boss)");
		long completedAt = 1_775_000_000_000L;
		for (int index = 0; index < 3; index++)
		{
			statistics.addRun(new TaskRun(
				"run-" + index,
				"Araxytes",
				null,
				"boss:araxxor",
				"Araxxor (boss)",
				210 + index,
				0,
				true,
				205 + index,
				210 + index,
				28_000,
				1_500,
				1_500 + index * 10,
				1_680_000L + index * 20_000L,
				0L,
				completedAt - 1_800_000L,
				completedAt,
				TaskRunStatus.COMPLETED), 50);
			completedAt -= 86_400_000L;
		}
		return Collections.singletonList(statistics);
	}

	private static void layoutRecursively(Container container)
	{
		container.doLayout();
		for (Component component : container.getComponents())
		{
			if (component instanceof Container)
			{
				layoutRecursively((Container) component);
			}
		}
	}

	private static SlayerSpeedViewModel sampleModel(
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
				new EncounterProfileOption("boss:araxxor", "Araxxor (boss)")));
	}
}
