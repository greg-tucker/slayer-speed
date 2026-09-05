package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.mortimer.MortimerEstimateService;
import com.slayerspeed.mortimer.MortimerTaskChoiceParser;
import com.slayerspeed.mortimer.MortimerTaskEstimate;
import com.slayerspeed.mortimer.MortimerTaskOffer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MortimerChoiceOverlay extends Overlay
{
	private static final Color PANEL_BACKGROUND = new Color(0, 0, 0, 175);
	private static final Color PANEL_BORDER = new Color(105, 82, 48, 220);
	private static final Color HEADING = new Color(255, 152, 31);
	private static final Color ESTIMATE = new Color(144, 238, 144);
	private static final Color MUTED = new Color(190, 190, 190);
	private static final Color SHADOW = new Color(0, 0, 0, 230);

	private final SlayerSpeedConfig config;
	private final MortimerTaskChoiceParser choiceParser;
	private final MortimerEstimateService estimateService;

	@Inject
	public MortimerChoiceOverlay(
		SlayerSpeedConfig config,
		MortimerTaskChoiceParser choiceParser,
		MortimerEstimateService estimateService)
	{
		this.config = config;
		this.choiceParser = choiceParser;
		this.estimateService = estimateService;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.experimentalMortimerEstimates())
		{
			return null;
		}

		List<MortimerTaskOffer> offers = choiceParser.readOffers();
		if (offers.isEmpty())
		{
			return null;
		}

		Font originalFont = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		for (MortimerTaskOffer offer : offers)
		{
			renderOffer(graphics, offer, estimateService.estimate(offer));
		}
		graphics.setFont(originalFont);
		return null;
	}

	private static void renderOffer(
		Graphics2D graphics,
		MortimerTaskOffer offer,
		MortimerTaskEstimate estimate)
	{
		Rectangle row = offer.getRowBounds();
		if (row.width <= 0 || row.height <= 0)
		{
			return;
		}

        int panelHeight = 24 + (estimate.hasData() ? estimate.getProfiles().size() * 28 : 15)
            + (estimate.getAdditionalProfileCount() > 0 ? 13 : 0);
		int panelWidth = Math.max(112, Math.min(138, row.width / 4));
		int panelX = row.x + row.width - panelWidth - 8;
		int panelY = row.y + Math.max(4, (row.height - panelHeight) / 2);

		graphics.setColor(PANEL_BACKGROUND);
		graphics.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 8, 8);
		graphics.setColor(PANEL_BORDER);
		graphics.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 8, 8);

        String heading = "All locations";
		int textY = panelY + 13;
		drawCentered(graphics, heading, panelX, panelWidth, textY, HEADING);
		textY += 15;

		if (!estimate.hasData())
		{
			drawCentered(graphics, "No personal data", panelX, panelWidth, textY, MUTED);
			return;
		}

        for (MortimerTaskEstimate.ProfileEstimate profile : estimate.getProfiles())
        {
            drawEstimate(graphics, profile, panelX, panelWidth, textY);
            textY += 13;
            String source = profile.getSourceDescription().replace(" history", "")
                .replace(" recent ", " ").replace(" eligible runs", " runs")
                .replace("lifetime (fallback)", "fallback");
            drawCentered(graphics, source, panelX, panelWidth, textY, MUTED);
            textY += 15;
        }
        if (estimate.getAdditionalProfileCount() > 0)
		{
			drawCentered(
				graphics,
				"+" + estimate.getAdditionalProfileCount() + " more profiles",
				panelX,
				panelWidth,
				textY,
				MUTED);
		}
	}

	private static void drawEstimate(
		Graphics2D graphics,
		MortimerTaskEstimate.ProfileEstimate profile,
		int panelX,
		int panelWidth,
		int baselineY)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		int maximumWidth = panelWidth - 8;
		String duration = profile.getDurationRange();
		String label = profile.getProfileName();
		String text = duration;
		if (!label.isEmpty())
		{
			String suffix = ": " + duration;
			int labelWidth = maximumWidth - metrics.stringWidth(suffix);
			text = labelWidth <= metrics.stringWidth("...")
				? duration
				: fitText(metrics, label, labelWidth) + suffix;
		}
		drawCentered(graphics, text, panelX, panelWidth, baselineY, ESTIMATE);
	}

	private static void drawCentered(
		Graphics2D graphics,
		String value,
		int panelX,
		int panelWidth,
		int baselineY,
		Color color)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		String text = fitText(metrics, value, panelWidth - 8);
		int x = panelX + (panelWidth - metrics.stringWidth(text)) / 2;
		graphics.setColor(SHADOW);
		graphics.drawString(text, x + 1, baselineY + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, baselineY);
	}

	private static String fitText(FontMetrics metrics, String value, int maximumWidth)
	{
		if (metrics.stringWidth(value) <= maximumWidth)
		{
			return value;
		}
		String suffix = "...";
		int end = value.length();
		while (end > 1 && metrics.stringWidth(value.substring(0, end) + suffix) > maximumWidth)
		{
			end--;
		}
		return value.substring(0, end).trim() + suffix;
	}
}
