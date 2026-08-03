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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SlayerSpeedPanelRenderTest
{
	@Test
	public void rendersSimpleAndDetailedPanelsAtRuneLiteWidth() throws Exception
	{
		Path output = Paths.get("build", "ux-review");
		Files.createDirectories(output);
		renderFirstRun(output.resolve("first-run.png"));
		render(output.resolve("simple.png"), new SlayerSpeedConfig()
		{
		}, false, false, false, false);
		render(output.resolve("detailed.png"), new SlayerSpeedConfig()
		{
			@Override
			public SlayerSpeedDisplayMode displayMode()
			{
				return SlayerSpeedDisplayMode.DETAILED;
			}
		}, true, true, false, false);
		render(output.resolve("manual.png"), new SlayerSpeedConfig()
		{
		}, true, false, true, false);
		render(output.resolve("history-expanded.png"), new SlayerSpeedConfig()
		{
		}, true, true, false, true);
		assertTrue(Files.size(output.resolve("first-run.png")) > 0L);
		assertTrue(Files.size(output.resolve("simple.png")) > 0L);
		assertTrue(Files.size(output.resolve("detailed.png")) > 0L);
		assertTrue(Files.size(output.resolve("manual.png")) > 0L);
		assertTrue(Files.size(output.resolve("history-expanded.png")) > 0L);
	}

	@Test
	public void onboardingOnlyAppearsWhileGlobalHistoryIsEmpty() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> { }, () -> { },
				(run, excluded) -> { }, run -> { }, profileId -> { }, new SlayerSpeedConfig()
				{
				});
			panel.update(SlayerSpeedViewModel.noTask(), Collections.emptyList());
			assertNotNull(findNamedComponent(panel, "historyOnboarding"));

			Collection<TaskStatistics> history = sampleHistory();
			panel.update(sampleModel(true, false, false), history);
			assertTrue(findNamedComponent(panel, "historyOnboarding") == null);
		});
	}

	@Test
	public void taskHistoryCollapsesRunsAndExpandsOnDemand() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerSpeedConfig config = new SlayerSpeedConfig()
			{
				@Override
				public int recentRunsShown()
				{
					return 2;
				}
			};
			SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> { }, () -> { },
				(run, excluded) -> { }, run -> { }, profileId -> { }, config);
			Collection<TaskStatistics> history = sampleHistory();
			panel.update(sampleModel(true, true, false), history);

			JButton toggle = findNamedButton(panel, "historyRunsToggle");
			assertNotNull(toggle);
			assertTrue(toggle.getText().startsWith("Runs (3)"));
			assertEquals(0, countEffectivelyVisibleButtons(panel, "historyRunActions"));

			toggle.doClick();
			assertTrue(toggle.getText().startsWith("Hide"));
			assertEquals(2, countEffectivelyVisibleButtons(panel, "historyRunActions"));

			JButton showAll = findNamedButton(panel, "historyShowAllRuns");
			assertNotNull(showAll);
			assertTrue(isEffectivelyVisible(showAll, panel));
			showAll.doClick();
			assertEquals(3, countEffectivelyVisibleButtons(panel, "historyRunActions"));

			toggle.doClick();
			assertFalse(toggle.getText().startsWith("Hide"));
			assertEquals(0, countEffectivelyVisibleButtons(panel, "historyRunActions"));
		});
	}

	private static void render(Path output, SlayerSpeedConfig config, boolean historyAvailable,
		boolean cannonRelevant, boolean manualSelection, boolean expandHistory) throws Exception
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
				if (expandHistory)
				{
					JButton toggle = findNamedButton(panel, "historyRunsToggle");
					assertNotNull(toggle);
					toggle.doClick();
				}
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

	private static void renderFirstRun(Path output) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> { }, () -> { },
					(run, excluded) -> { }, run -> { }, profileId -> { }, new SlayerSpeedConfig()
					{
					});
				panel.update(SlayerSpeedViewModel.noTask(), Collections.emptyList());
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

	private static JButton findNamedButton(Container container, String name)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof JButton && name.equals(component.getName()))
			{
				return (JButton) component;
			}
			if (component instanceof Container)
			{
				JButton match = findNamedButton((Container) component, name);
				if (match != null)
				{
					return match;
				}
			}
		}
		return null;
	}

	private static Component findNamedComponent(Container container, String name)
	{
		for (Component component : container.getComponents())
		{
			if (name.equals(component.getName()))
			{
				return component;
			}
			if (component instanceof Container)
			{
				Component match = findNamedComponent((Container) component, name);
				if (match != null)
				{
					return match;
				}
			}
		}
		return null;
	}

	private static int countEffectivelyVisibleButtons(Container root, String name)
	{
		List<JButton> buttons = new ArrayList<>();
		collectNamedButtons(root, name, buttons);
		int visible = 0;
		for (JButton button : buttons)
		{
			if (isEffectivelyVisible(button, root))
			{
				visible++;
			}
		}
		return visible;
	}

	private static void collectNamedButtons(Container container, String name, List<JButton> matches)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof JButton && name.equals(component.getName()))
			{
				matches.add((JButton) component);
			}
			if (component instanceof Container)
			{
				collectNamedButtons((Container) component, name, matches);
			}
		}
	}

	private static boolean isEffectivelyVisible(Component component, Container root)
	{
		Component current = component;
		while (current != null)
		{
			if (!current.isVisible())
			{
				return false;
			}
			if (current == root)
			{
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	private static Collection<TaskStatistics> sampleHistory()
	{
		long completedAt = 1_775_000_000_000L;
		return Arrays.asList(
			sampleStatistics(
				"Araxytes", "boss:araxxor", "Araxxor (boss)", 3,
				210, 205, 210, 28_000, 1_500, 1_680_000L, completedAt),
			sampleStatistics(
				"Dark Beasts", "npc:dark-beast", "Dark beast", 2,
				48, 48, 48, 10_600, 0, 1_560_000L, completedAt - 259_200_000L),
			sampleStatistics(
				"Nechryael", "npc:greater-nechryael", "Greater Nechryael", 2,
				249, 237, 237, 46_000, 800, 2_760_000L, completedAt - 432_000_000L));
	}

	private static TaskStatistics sampleStatistics(
		String taskName,
		String profileId,
		String profileName,
		int runCount,
		int initialAmount,
		int kills,
		int units,
		int slayerXp,
		int cannonballs,
		long activeMillis,
		long latestCompletion)
	{
		TaskStatistics statistics = new TaskStatistics(taskName, null, profileId, profileName);
		long completedAt = latestCompletion;
		for (int index = 0; index < runCount; index++)
		{
			statistics.addRun(new TaskRun(
				taskName + "-run-" + index,
				taskName,
				null,
				profileId,
				profileName,
				initialAmount + index,
				0,
				true,
				kills + index,
				units + index,
				slayerXp + index * 100,
				0,
				cannonballs == 0 ? 0 : cannonballs + index * 10,
				activeMillis + index * 20_000L,
				0L,
				completedAt - activeMillis,
				completedAt,
				TaskRunStatus.COMPLETED), 50);
			completedAt -= 86_400_000L;
		}
		return statistics;
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
