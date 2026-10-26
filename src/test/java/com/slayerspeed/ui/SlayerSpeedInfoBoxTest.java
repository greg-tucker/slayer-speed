package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedInGameDisplay;
import com.slayerspeed.SlayerSpeedPlugin;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.InfoBoxComponent;
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
    }

    @Test public void runeLiteConfigProxyHonorsLegacyOffAndExplicitDisplaySelection() throws Exception
    {
        net.runelite.client.config.ConfigManager manager = mock(net.runelite.client.config.ConfigManager.class, CALLS_REAL_METHODS);
        doReturn("false").when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "showOverlay");
        doReturn(null).when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "inGameDisplay");
        doReturn(null).when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "overlayEnabled");
        doReturn(null).when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "showInfoBoxes");
        assertEquals(SlayerSpeedInGameDisplay.OFF, configProxy(manager).inGameDisplay());
        assertFalse(configProxy(manager).overlayEnabled());
        assertFalse(configProxy(manager).showInfoBoxes());
        doReturn("INFOBOXES").when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "inGameDisplay");
        assertEquals(SlayerSpeedInGameDisplay.INFOBOXES, configProxy(manager).inGameDisplay());
        assertFalse(configProxy(manager).overlayEnabled());
        assertTrue(configProxy(manager).showInfoBoxes());
        doReturn("true").when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "overlayEnabled");
        assertTrue(configProxy(manager).overlayEnabled());
        assertTrue(configProxy(manager).showInfoBoxes());
        doReturn("false").when(manager).getConfiguration(SlayerSpeedConfig.GROUP, "showInfoBoxes");
        assertTrue(configProxy(manager).overlayEnabled());
        assertFalse(configProxy(manager).showInfoBoxes());
    }

    private static SlayerSpeedConfig configProxy(net.runelite.client.config.ConfigManager manager) throws Exception
    {
        Class<?> type = Class.forName("net.runelite.client.config.ConfigInvocationHandler");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(net.runelite.client.config.ConfigManager.class);
        constructor.setAccessible(true);
        return (SlayerSpeedConfig) java.lang.reflect.Proxy.newProxyInstance(SlayerSpeedConfig.class.getClassLoader(),
            new Class<?>[] {SlayerSpeedConfig.class}, (java.lang.reflect.InvocationHandler) constructor.newInstance(manager));
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
        when(plugin.getViewModel()).thenReturn(SlayerSpeedPanelRenderTest.sampleModel(true, false, false));
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
    }

    @Test public void unavailableValuesRemainClearlyLabelled()
    {
        SlayerSpeedPlugin plugin = mock(SlayerSpeedPlugin.class);
        when(plugin.getViewModel()).thenReturn(SlayerSpeedViewModel.noTask());
        for (SlayerSpeedInfoBox.Metric metric : SlayerSpeedInfoBox.Metric.values())
        {
            SlayerSpeedInfoBox box = new SlayerSpeedInfoBox(plugin, config(new boolean[] {true, true, true}), metric);
            assertEquals("--", box.getText());
            assertTrue(box.getTooltip().contains("Waiting for timed activity"));
            assertFalse(box.render());
        }
    }

    @Test public void overlayAndInfoboxSwitchesWorkIndependently() throws Exception
    {
        SlayerSpeedPlugin plugin = mock(SlayerSpeedPlugin.class);
        when(plugin.isOverlayVisible()).thenReturn(true);
        when(plugin.getViewModel()).thenReturn(SlayerSpeedPanelRenderTest.sampleModel(true, false, false));
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

    @Test public void rendersReadableNativeBoxesAtDefaultSize() throws Exception
    {
        SlayerSpeedPlugin plugin = mock(SlayerSpeedPlugin.class);
        when(plugin.isOverlayVisible()).thenReturn(true);
        when(plugin.getViewModel()).thenReturn(SlayerSpeedPanelRenderTest.sampleModel(true, false, false));
        BufferedImage image = new BufferedImage(110, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setFont(FontManager.getRunescapeSmallFont());
            int index = 0;
            for (SlayerSpeedInfoBox.Metric metric : SlayerSpeedInfoBox.Metric.values())
            {
                SlayerSpeedInfoBox box = new SlayerSpeedInfoBox(plugin, config(new boolean[] {true, true, true}), metric);
                assertTrue("Caption fits a standard infobox", graphics.getFontMetrics().stringWidth(box.getText()) <= 30);
                InfoBoxComponent component = new InfoBoxComponent();
                component.setImage(box.getImage());
                component.setText(box.getText());
                component.setColor(box.getTextColor());
                component.setFont(FontManager.getRunescapeSmallFont());
                component.setPreferredSize(new Dimension(32, 32));
                component.setPreferredLocation(new java.awt.Point(3 + index++ * 35, 3));
                component.render(graphics);
                assertTrue(box.getTooltip().contains("Araxytes"));
            }
        }
        finally { graphics.dispose(); }
        Path output = Paths.get("build", "ux-preview", "infoboxes.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
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
