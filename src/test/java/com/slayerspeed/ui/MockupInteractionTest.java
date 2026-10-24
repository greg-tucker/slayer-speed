package com.slayerspeed.ui;

import com.slayerspeed.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MockupInteractionTest
{
    private static Component named(Container container, String name)
    {
        for (Component child : container.getComponents())
        {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container)
            {
                Component found = named((Container) child, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }
    @Test public void labelsTheRecordingGapAndProvidesInlineUndo() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> {}, () -> {}, (run, excluded) -> {},
                run -> {}, id -> {}, new SlayerSpeedConfig() {});
            panel.update(SlayerSpeedPanelRenderTest.sampleModel(true, false, false), Collections.emptyList());
            panel.updateProgress("profile:run", 174, 174, 102, 68);
            JLabel recorded = (JLabel) named(panel, "recordedProgress");
            assertTrue(recorded.getText().contains("68 units"));
            assertTrue(recorded.getToolTipText().contains("4 assignment units"));
            AtomicInteger actions = new AtomicInteger();
            panel.setDataActions(action -> { assertEquals("undo", action); actions.incrementAndGet(); });
            panel.setUndoAvailable(true);
            assertTrue(named(panel, "inlineUndoNotice").isVisible());
            ((JButton) named(panel, "inlineUndo")).doClick();
            panel.setUndoAvailable(false);
            assertFalse(named(panel, "inlineUndoNotice").isVisible());
            ((JButton) named(panel, "inlineUndo")).doClick();
            assertEquals(1, actions.get());
        });
    }
    @Test public void viewButtonsSyncWithoutWritingOnEveryRefresh() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedDisplayMode[] mode = {SlayerSpeedDisplayMode.SIMPLE};
            SlayerSpeedConfig config = new SlayerSpeedConfig()
            {
                @Override public SlayerSpeedDisplayMode displayMode() { return mode[0]; }
            };
            SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> {}, () -> {}, (run, excluded) -> {},
                run -> {}, id -> {}, config);
            AtomicInteger changes = new AtomicInteger();
            panel.setDisplayModeAction(selected -> { changes.incrementAndGet(); mode[0] = selected; });
            SlayerSpeedViewModel model = SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
            panel.update(model, Collections.emptyList());
            ((JToggleButton) named(panel, "detailedMode")).doClick();
            panel.update(model, Collections.emptyList());
            panel.update(model, Collections.emptyList());
            assertEquals(SlayerSpeedDisplayMode.DETAILED, mode[0]);
            assertTrue(((JToggleButton) named(panel, "detailedMode")).isSelected());
            assertEquals(1, changes.get());
        });
    }
    @Test public void activityExplanationPreservesRatesAndDescribesTheirSource()
    {
        SlayerSpeedViewModel original = SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
        SlayerSpeedViewModel waiting = original.withActivityState(SlayerSpeedViewModel.ActivityState.SUSPENDED);
        assertEquals(original.getEta(), waiting.getEta());
        assertEquals(original.getCurrentSlayerXpPerHour(), waiting.getCurrentSlayerXpPerHour());
        assertSame(original.getEstimate(), waiting.getEstimate());
        assertEquals(SlayerSpeedViewModel.ActivityState.UNSPECIFIED, original.getActivityState());
        assertEquals("Waiting for task activity", waiting.getActivityLabel());
        assertTrue(waiting.getActivityExplanation().contains("history and recorded pace"));
        assertEquals("", original.withActivityState(SlayerSpeedViewModel.ActivityState.TRACKING).getActivityExplanation());
    }
    @Test public void simpleModeDisclosesSecondarySectionsWithoutLosingAccess() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedDisplayMode[] mode = {SlayerSpeedDisplayMode.SIMPLE};
            SlayerSpeedConfig config = new SlayerSpeedConfig()
            {
                @Override public SlayerSpeedDisplayMode displayMode() { return mode[0]; }
            };
            SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> {}, () -> {}, (run, excluded) -> {},
                run -> {}, id -> {}, config);
            SlayerSpeedViewModel model = SlayerSpeedPanelRenderTest.sampleModel(true, false, false);
            panel.update(model, Collections.emptyList());
            assertFalse(named(panel, "taskOptions").isVisible());
            assertFalse(named(panel, "historySection").isVisible());
            assertTrue(named(panel, "simpleCurrentXp").isVisible());
            ((JButton) named(panel, "taskOptionsToggle")).doClick();
            ((JButton) named(panel, "historyToggle")).doClick();
            panel.update(model, Collections.emptyList());
            assertTrue(named(panel, "taskOptions").isVisible());
            assertTrue(named(panel, "historySection").isVisible());
            ((JButton) named(panel, "historyToggle")).doClick();
            mode[0] = SlayerSpeedDisplayMode.DETAILED;
            panel.update(model, Collections.emptyList());
            assertTrue(named(panel, "historySection").isVisible());
            assertFalse(named(panel, "historyToggle").isVisible());
            mode[0] = SlayerSpeedDisplayMode.SIMPLE;
            panel.update(SlayerSpeedViewModel.noTask(), Collections.emptyList());
            assertTrue(named(panel, "historySection").isVisible());
            assertFalse(named(panel, "taskOptionsToggle").isVisible());
        });
    }
    @Test public void compactOverlayIsIndependentOfDetailedSidebar()
    {
        SlayerSpeedOverlayMode[] overlayMode = {SlayerSpeedOverlayMode.SIMPLE};
        SlayerSpeedConfig config = new SlayerSpeedConfig()
        {
            @Override public SlayerSpeedDisplayMode displayMode() { return SlayerSpeedDisplayMode.DETAILED; }
            @Override public SlayerSpeedOverlayMode overlayDetail() { return overlayMode[0]; }
        };
        SlayerSpeedOverlay overlay = new SlayerSpeedOverlay(new SlayerSpeedPlugin()
        {
            @Override public boolean isOverlayVisible() { return true; }
            @Override public SlayerSpeedViewModel getViewModel() { return SlayerSpeedPanelRenderTest.sampleModel(true, false, false); }
        }, config);
        overlay.setClearChildren(false);
        Graphics2D graphics = new BufferedImage(300, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
        overlay.render(graphics);
        int compact = overlay.getPanelComponent().getChildren().size();
        overlayMode[0] = SlayerSpeedOverlayMode.DETAILED;
        overlay.render(graphics);
        assertTrue(overlay.getPanelComponent().getChildren().size() > compact + 3);
        assertEquals(SlayerSpeedDisplayMode.DETAILED, config.displayMode());
        assertEquals(SlayerSpeedOverlayMode.FOLLOW_SIDEBAR, new SlayerSpeedConfig() {}.overlayDetail());
        graphics.dispose();
    }
}
