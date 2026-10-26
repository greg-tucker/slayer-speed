package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedPlugin;
import com.slayerspeed.calculation.TaskEstimate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** Reads published task snapshots; rendering never queries the game client. */
public final class SlayerSpeedInfoBox extends InfoBox
{
    public enum Metric
    {
        TIME_LEFT("Estimated time remaining"), KILLS_PER_HOUR("Kills per hour"), SLAYER_XP_PER_HOUR("Slayer XP per hour");
        private final String label;
        Metric(String label) { this.label = label; }
    }

    private final SlayerSpeedPlugin plugin;
    private final SlayerSpeedConfig config;
    private final Metric metric;

    public SlayerSpeedInfoBox(SlayerSpeedPlugin plugin, SlayerSpeedConfig config, Metric metric)
    {
        super(icon(metric), plugin);
        this.plugin = plugin;
        this.config = config;
        this.metric = metric;
    }

    public boolean isSelected()
    {
        switch (metric)
        {
            case TIME_LEFT: return config.infoBoxTimeLeft();
            case KILLS_PER_HOUR: return config.infoBoxKillsPerHour();
            default: return config.infoBoxSlayerXpPerHour();
        }
    }

    @Override public boolean render()
    {
        return config.showInfoBoxes() && isSelected()
            && plugin.isOverlayVisible() && plugin.getViewModel().isActive();
    }

    @Override public String getName() { return "SlayerSpeedPlugin_" + metric.name(); }

    @Override public String getText()
    {
        SlayerSpeedViewModel model = plugin.getViewModel();
        switch (metric)
        {
            case TIME_LEFT:
                TaskEstimate estimate = model.getEstimate();
                return estimate == null || !estimate.getEtaMillis().isPresent() ? "--"
                    : compactTime(estimate.getEtaMillis().getAsDouble());
            case KILLS_PER_HOUR: return compactRate(model.getCurrentLiteralKph());
            default: return compactRate(model.getCurrentSlayerXpPerHour());
        }
    }

    @Override public Color getTextColor() { return "--".equals(getText()) ? Color.LIGHT_GRAY : Color.WHITE; }

    @Override public String getTooltip()
    {
        SlayerSpeedViewModel model = plugin.getViewModel();
        String value = metric == Metric.TIME_LEFT ? model.getEta()
            : metric == Metric.KILLS_PER_HOUR ? model.getCurrentLiteralKph() : model.getCurrentSlayerXpPerHour();
        String explanation = metric == Metric.TIME_LEFT
            ? "<br>" + model.getEstimateDescription() + "<br>Estimated task activity, not a countdown."
            : metric == Metric.KILLS_PER_HOUR ? "<br>Physical kills during this task, not task counter units."
                : "<br>Slayer XP during this task, including recorded bonus XP.";
        return "Slayer Task Speed<br>" + model.getTask() + "<br>" + metric.label + ": "
            + ("--".equals(value) ? "Waiting for timed activity" : value) + explanation
            + (model.getActivityLabel().isEmpty() ? "" : "<br>" + model.getActivityLabel());
    }

    static String compactTime(double millis)
    {
        if (!Double.isFinite(millis) || millis < 0) { return "--"; }
        long seconds = (long) Math.ceil(millis / 1000.0);
        if (seconds < 60) { return seconds + "s"; }
        long minutes = (long) Math.ceil(seconds / 60.0);
        return minutes < 60 ? minutes + "m" : compactNumber(minutes / 60.0) + "h";
    }

    static String compactRate(String value)
    {
        if (value == null || value.isEmpty() || "--".equals(value)) { return "--"; }
        try
        {
            char last = Character.toLowerCase(value.charAt(value.length() - 1));
            double scale = last == 'k' ? 1000 : last == 'm' ? 1_000_000 : 1;
            double number = Double.parseDouble(scale == 1 ? value : value.substring(0, value.length() - 1)) * scale;
            if (!Double.isFinite(number) || number < 0) { return "--"; }
            if (number >= 1_000_000) { return compactNumber(number / 1_000_000) + "m"; }
            if (number >= 1000) { return compactNumber(number / 1000) + "k"; }
            return Long.toString(Math.round(number));
        }
        catch (NumberFormatException ex) { return "--"; }
    }

    private static String compactNumber(double value)
    {
        return value < 100 ? String.format(Locale.ENGLISH, "%.1f", value).replaceAll("\\.0$", "")
            : Long.toString(Math.round(value));
    }

    private static BufferedImage icon(Metric metric)
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(metric == Metric.TIME_LEFT ? new Color(255, 180, 70) : Color.LIGHT_GRAY);
            if (metric == Metric.TIME_LEFT)
            {
                graphics.drawOval(5, 1, 14, 14);
                graphics.drawLine(12, 4, 12, 8);
                graphics.drawLine(12, 8, 16, 10);
            }
            else if (metric == Metric.KILLS_PER_HOUR)
            {
                graphics.drawLine(5, 1, 19, 15);
                graphics.drawLine(19, 1, 5, 15);
                graphics.drawLine(3, 10, 9, 16);
                graphics.drawLine(15, 16, 21, 10);
            }
            else
            {
                graphics.setColor(new Color(155, 220, 135));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                graphics.drawString("XP", 4, 13);
            }
        }
        finally { graphics.dispose(); }
        return image;
    }
}
