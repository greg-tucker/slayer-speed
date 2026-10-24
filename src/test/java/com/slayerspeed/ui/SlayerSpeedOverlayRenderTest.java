package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedPlugin;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerSpeedOverlayRenderTest
{
    @Test public void hiddenOverlayReturnsWithoutRenderingAndCanReappear()
    {
        boolean[] visible = {false};
        SlayerSpeedPlugin plugin = new SlayerSpeedPlugin()
        {
            @Override public boolean isOverlayVisible() { return visible[0]; }
            @Override public SlayerSpeedViewModel getViewModel()
            {
                return SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
            }
        };
        SlayerSpeedOverlay overlay = new SlayerSpeedOverlay(plugin, new SlayerSpeedConfig() {});
        Graphics2D graphics = new BufferedImage(240, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            graphics.setFont(FontManager.getRunescapeFont());
            assertNull(overlay.render(graphics));
            visible[0] = true;
            assertNotNull(overlay.render(graphics));
            assertNotNull(overlay.render(graphics));
            visible[0] = false;
            assertNull(overlay.render(graphics));
        }
        finally { graphics.dispose(); }
    }

    @Test public void longProvenanceUsesOverlayWidthInsteadOfAThinRightColumn() throws Exception
    {
        SlayerSpeedPlugin plugin = new SlayerSpeedPlugin()
        {
            @Override public boolean isOverlayVisible() { return true; }
            @Override public SlayerSpeedViewModel getViewModel()
            {
                return SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
            }
        };
        SlayerSpeedOverlay overlay = new SlayerSpeedOverlay(plugin, new SlayerSpeedConfig() {});
        overlay.setClearChildren(false);
        Path output = Paths.get("build", "ux-review");
        Files.createDirectories(output);
        for (int width : new int[] {180, 220})
        {
            overlay.setPreferredSize(new Dimension(width, 0));
            BufferedImage image = new BufferedImage(width + 10, 220, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setFont(FontManager.getRunescapeFont());
            int lineHeight = graphics.getFontMetrics().getHeight();
            // RuneLite returns the previous frame's panel dimensions. Warm the layout first.
            overlay.render(graphics);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(AlphaComposite.SrcOver);
            Dimension size = overlay.render(graphics);
            LineComponent source = (LineComponent) overlay.getPanelComponent().getChildren().get(4);
            assertTrue("Fixture must contain a populated overlay", size.height >= lineHeight * 5);
            assertTrue("Source must be rendered", source.getBounds().height > 0);
            assertTrue("Source should fit in at most three full-width lines", source.getBounds().height <= lineHeight * 3);
            assertTrue("Compact overlay should not become a tall text column", size.height < lineHeight * 10);
            graphics.dispose();
            ImageIO.write(image.getSubimage(0, 0, width, size.height), "png",
                output.resolve("overlay-" + width + ".png").toFile());
        }
    }
    @Test public void detailedCountsAndPaceStayReadableAtNarrowWidths() throws Exception
    {
        SlayerSpeedPlugin plugin = new SlayerSpeedPlugin()
        {
            @Override public boolean isOverlayVisible() { return true; }
            @Override public SlayerSpeedViewModel getViewModel()
            {
                return SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
            }
        };
        SlayerSpeedOverlay overlay = new SlayerSpeedOverlay(plugin, new SlayerSpeedConfig()
        {
            @Override public com.slayerspeed.SlayerSpeedDisplayMode displayMode()
            {
                return com.slayerspeed.SlayerSpeedDisplayMode.DETAILED;
            }
            @Override public boolean showEstimatedFinishTime() { return true; }
            @Override public boolean showPaceComparison() { return true; }
        });
        overlay.setClearChildren(false);
        Path output = Paths.get("build", "ux-review");
        Files.createDirectories(output);
        for (int width : new int[] {180, 220})
        {
            for (int fontSize : new int[] {12, 16})
            {
                overlay.setPreferredSize(new Dimension(width, 0));
                BufferedImage image = new BufferedImage(width + 10, 600, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setFont(FontManager.getRunescapeFont().deriveFont((float) fontSize));
                int lineHeight = graphics.getFontMetrics().getHeight();
                overlay.render(graphics);
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setComposite(AlphaComposite.SrcOver);
                Dimension size = overlay.render(graphics);
                for (int index : new int[] {9, 11})
                {
                    LineComponent line = (LineComponent) overlay.getPanelComponent().getChildren().get(index);
                    assertTrue("Counts/pace must render", line.getBounds().height > 0);
                    assertTrue("Counts/pace must use full-width lines at " + width + " px / font " + fontSize,
                        line.getBounds().height <= lineHeight * 2);
                }
                graphics.dispose();
                ImageIO.write(image.getSubimage(0, 0, width, size.height), "png",
                    output.resolve("overlay-detailed-" + width + "-" + fontSize + ".png").toFile());
            }
        }
    }

}
