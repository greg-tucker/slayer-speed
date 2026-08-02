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
import java.util.Collection;
import java.util.Collections;
import javax.imageio.ImageIO;
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
		}, false, false);
		render(output.resolve("detailed.png"), new SlayerSpeedConfig()
		{
			@Override
			public SlayerSpeedDisplayMode displayMode()
			{
				return SlayerSpeedDisplayMode.DETAILED;
			}
		}, true, true);
		assertTrue(Files.size(output.resolve("simple.png")) > 0L);
		assertTrue(Files.size(output.resolve("detailed.png")) > 0L);
	}

	private static void render(Path output, SlayerSpeedConfig config, boolean historyAvailable,
		boolean cannonRelevant) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> { }, () -> { },
					(run, excluded) -> { }, run -> { }, config);
				Collection<TaskStatistics> history = historyAvailable
					? sampleHistory()
					: Collections.emptyList();
				panel.update(sampleModel(historyAvailable, cannonRelevant), history);
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

	private static Collection<TaskStatistics> sampleHistory()
	{
		TaskStatistics statistics = new TaskStatistics("Araxytes", null);
		long completedAt = 1_775_000_000_000L;
		for (int index = 0; index < 3; index++)
		{
			statistics.addRun(new TaskRun(
				"run-" + index,
				"Araxytes",
				null,
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

	private static SlayerSpeedViewModel sampleModel(boolean historyAvailable, boolean cannonRelevant)
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
			null);
	}
}
