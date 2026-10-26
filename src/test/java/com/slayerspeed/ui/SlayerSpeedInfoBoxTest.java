package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedInGameDisplay;
import com.slayerspeed.SlayerSpeedPlugin;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SlayerSpeedInfoBoxTest
{
    @Test public void defaultsPreserveExistingOverlayPreferences()
    {
        assertEquals(SlayerSpeedInGameDisplay.OVERLAY, new SlayerSpeedConfig() {}.inGameDisplay());
        assertTrue(new SlayerSpeedConfig() {}.overlayEnabled());
        assertFalse(new SlayerSpeedConfig() {}.showInfoBoxes());
        assertEquals(SlayerSpeedInGameDisplay.OFF, new SlayerSpeedConfig()
        {
            @Override public boolean showOverlay() { return false; }
        }.inGameDisplay());
        assertTrue(new SlayerSpeedConfig() {}.infoBoxTimeLeft());
        assertFalse(new SlayerSpeedConfig() {}.infoBoxKillsPerHour());
        assertFalse(new SlayerSpeedConfig() {}.infoBoxSlayerXpPerHour());
        for (SlayerSpeedInGameDisplay legacy : SlayerSpeedInGameDisplay.values())
        {
            SlayerSpeedConfig config = new SlayerSpeedConfig()
            {
                @Override public SlayerSpeedInGameDisplay inGameDisplay() { return legacy; }
            };
            assertEquals(legacy.showsOverlay(), config.overlayEnabled());
            assertEquals(legacy.showsInfoBoxes(), config.showInfoBoxes());
        }
    }

    @Test public void abbreviatesValuesWithoutTurningAnEstimateIntoACountdown()
    {
        assertEquals("--", SlayerSpeedInfoBox.compactRate("--"));
        assertEquals("--", SlayerSpeedInfoBox.compactRate(""));
        assertEquals("--", SlayerSpeedInfoBox.compactRate("NaN"));
        assertEquals("--", SlayerSpeedInfoBox.compactRate("-1"));
        assertEquals("245", SlayerSpeedInfoBox.compactRate("245.0"));
        assertEquals("32.5k", SlayerSpeedInfoBox.compactRate("32.5k"));
        assertEquals("1.2m", SlayerSpeedInfoBox.compactRate("1200000"));
        assertEquals("59s", SlayerSpeedInfoBox.compactTime(59_000));
        assertEquals("1m", SlayerSpeedInfoBox.compactTime(60_000));
        assertEquals("18m", SlayerSpeedInfoBox.compactTime(18 * 60_000));
        assertEquals("1.5h", SlayerSpeedInfoBox.compactTime(90 * 60_000));
        assertEquals("--", SlayerSpeedInfoBox.compactTime(Double.NaN));
    }

    @Test public void eachMetricHasIndependentVisibilityAndStableIdentity()
    {
        SlayerSpeedPlugin plugin = mock(SlayerSpeedPlugin.class);
        when(plugin.isOverlayVisible()).thenReturn(true);
        when(plugin.getViewModel()).thenReturn(SlayerSpeedTestModels.sampleModel(true, false, false));
        boolean[] enabled = {true, false, false};
        SlayerSpeedConfig config = config(enabled);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (SlayerSpeedInfoBox.Metric metric : SlayerSpeedInfoBox.Metric.values())
        {
            SlayerSpeedInfoBox box = new SlayerSpeedInfoBox(plugin, config, metric);
            assertTrue(names.add(box.getName()));
            assertEquals(enabled[metric.ordinal()], box.render());
            enabled[metric.ordinal()] = true;
            assertTrue(box.render());
            assertEquals(box.getName(), new SlayerSpeedInfoBox(plugin, config, metric).getName());
        }
        when(plugin.isOverlayVisible()).thenReturn(false);
        assertFalse(new SlayerSpeedInfoBox(plugin, config, SlayerSpeedInfoBox.Metric.TIME_LEFT).render());
        when(plugin.getViewModel()).thenReturn(SlayerSpeedViewModel.noTask());
        for (SlayerSpeedInfoBox.Metric metric : SlayerSpeedInfoBox.Metric.values())
        {
            SlayerSpeedInfoBox box = new SlayerSpeedInfoBox(plugin, config, metric);
            assertEquals("--", box.getText());
            assertFalse(box.render());
        }
    }

    @Test public void overlayAndInfoboxSwitchesWorkIndependently() throws Exception
    {
        SlayerSpeedPlugin plugin = mock(SlayerSpeedPlugin.class);
        when(plugin.isOverlayVisible()).thenReturn(true);
        when(plugin.getViewModel()).thenReturn(SlayerSpeedTestModels.sampleModel(true, false, false));
        boolean[] enabled = {false, true};
        SlayerSpeedConfig config = new SlayerSpeedConfig()
        {
            @Override public boolean overlayEnabled() { return enabled[0]; }
            @Override public boolean showInfoBoxes() { return enabled[1]; }
        };
        SlayerSpeedOverlay overlay = new SlayerSpeedOverlay(plugin, config);
        SlayerSpeedInfoBox box = new SlayerSpeedInfoBox(plugin, config, SlayerSpeedInfoBox.Metric.TIME_LEFT);
        Graphics2D graphics = new BufferedImage(240, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            graphics.setFont(FontManager.getRunescapeSmallFont());
            assertNull(overlay.render(graphics));
            assertTrue(box.render());
            enabled[0] = true;
            assertNotNull(overlay.render(graphics));
            assertTrue(box.render());
            enabled[1] = false;
            assertNotNull(overlay.render(graphics));
            assertFalse(box.render());
            enabled[0] = false;
            assertNull(overlay.render(graphics));
            assertFalse(box.render());
        }
        finally { graphics.dispose(); }
    }

    private static SlayerSpeedConfig config(boolean[] enabled)
    {
        return new SlayerSpeedConfig()
        {
            @Override public SlayerSpeedInGameDisplay inGameDisplay() { return SlayerSpeedInGameDisplay.INFOBOXES; }
            @Override public boolean infoBoxTimeLeft() { return enabled[0]; }
            @Override public boolean infoBoxKillsPerHour() { return enabled[1]; }
            @Override public boolean infoBoxSlayerXpPerHour() { return enabled[2]; }
        };
    }
}
