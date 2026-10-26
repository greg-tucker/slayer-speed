package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedDisplayMode;
import com.slayerspeed.SlayerSpeedPlugin;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class SlayerSpeedOverlay extends OverlayPanel
{
	private final SlayerSpeedPlugin plugin;
	private final SlayerSpeedConfig config;

	@Inject
	public SlayerSpeedOverlay(SlayerSpeedPlugin plugin, SlayerSpeedConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		SlayerSpeedViewModel model = plugin.getViewModel();
		if (!config.overlayEnabled() || !model.isActive() || !plugin.isOverlayVisible())
		{
			return null;
		}

		boolean detailed = config.overlayDetail().isDetailed(config.displayMode());
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder().text(model.getTask()).build());
		if (model.isEncounterSelectorRelevant())
		{
			panelComponent.getChildren().add(textLine(model.getEncounterProfileDisplay()));
		}
		panelComponent.getChildren().add(line("Remaining", model.getRemaining()));
		if (!"--".equals(model.getEta()))
		{
			panelComponent.getChildren().add(line("ETA", "~" + model.getEta()));
		}
        panelComponent.getChildren().add(textLine(detailed ? model.getEstimateDescription() : model.getCompactEstimateDescription()));
        if (config.showEstimatedFinishTime() && model.hasEstimatedFinishTime())
		{
			panelComponent.getChildren().add(line("Est. finish", model.getEstimatedFinishTime()));
		}
		if (detailed && !"--".equals(model.getCurrentLiteralKph()))
		{
			panelComponent.getChildren().add(line("Kills/hr", model.getCurrentLiteralKph()));
		}
		if (detailed
			&& !"--".equals(model.getCurrentEffectiveKph()))
		{
			panelComponent.getChildren().add(line("Task units/hr", model.getCurrentEffectiveKph()));
		}
		if (!"--".equals(model.getCurrentSlayerXpPerHour()))
		{
			panelComponent.getChildren().add(line("Slayer XP/hr", model.getCurrentSlayerXpPerHour()));
		}
		if (detailed)
		{
			panelComponent.getChildren().add(textLine(model.getObservedCounts()));
			if (model.isHistoryAvailable())
			{
				panelComponent.getChildren().add(line("Avg units/hr", model.getHistoricalEffectiveKph()));
			}
		}
		if (detailed && config.showPaceComparison() && model.hasPaceComparison())
		{
			panelComponent.getChildren().add(textLine("Vs. average: " + model.getPaceComparison()));
		}
		if (config.showCannonMetrics() && model.isCannonRelevant())
		{
			if (detailed) { panelComponent.getChildren().add(line("Cannonballs used", model.getCannonballsUsed())); }
			if (!"--".equals(model.getEstimatedRemainingCannonballs()))
			{
				panelComponent.getChildren().add(line("0".equals(model.getCannonballsUsed()) ? "If cannoning, need" : "Cannonballs needed", model.getEstimatedRemainingCannonballs()));
			}
			if (detailed && !"--".equals(model.getCannonballsPerKill()))
			{
				panelComponent.getChildren().add(line(model.getCannonRateLabel(), model.getCannonballsPerKill()));
			}
		}
        if (model.getActivityState() == SlayerSpeedViewModel.ActivityState.SUSPENDED
            || model.getActivityState() == SlayerSpeedViewModel.ActivityState.PAUSED)
        {
            panelComponent.getChildren().add(textLine(model.getActivityLabel()));
        }
        return super.render(graphics);
	}

    // Descriptive text must use the full-width left side. RuneLite restricts an
    // overflowing right-side value to one third of the overlay width.
    private static LineComponent textLine(String text)
    {
        return LineComponent.builder().left(text).build();
    }

    private static LineComponent line(String left, String right)
	{
		return LineComponent.builder().left(left).right(right).build();
	}
}
