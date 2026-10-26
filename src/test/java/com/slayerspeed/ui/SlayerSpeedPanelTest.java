package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedDisplayMode;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerSpeedPanelTest
{
    @Test public void recordingGapAndUndoReflectTheActualActionState() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedPanel panel = panel(new SlayerSpeedConfig() {});
            panel.updateProgress("profile:run", 174, 174, 102, 68);
            JLabel recorded = (JLabel) named(panel, "recordedProgress");
            assertTrue(recorded.getText().contains("68 units"));
            assertTrue(recorded.getToolTipText().contains("4 assignment units"));
            AtomicInteger actions = new AtomicInteger();
            panel.setDataActions(action -> { assertEquals("undo", action); actions.incrementAndGet(); });
            panel.setUndoAvailable(true);
            ((JButton) named(panel, "inlineUndo")).doClick();
            panel.setUndoAvailable(false);
            ((JButton) named(panel, "inlineUndo")).doClick();
            assertEquals(1, actions.get());
            assertFalse(named(panel, "inlineUndoNotice").isVisible());
        });
    }

    @Test public void sidebarModeChangesOnceAndRefreshDoesNotWriteConfiguration() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedDisplayMode[] mode = {SlayerSpeedDisplayMode.SIMPLE};
            SlayerSpeedPanel panel = panel(new SlayerSpeedConfig()
            {
                @Override public SlayerSpeedDisplayMode displayMode() { return mode[0]; }
            });
            AtomicInteger changes = new AtomicInteger();
            panel.setDisplayModeAction(value -> { changes.incrementAndGet(); mode[0] = value; });
            ((JToggleButton) named(panel, "detailedMode")).doClick();
            panel.update(SlayerSpeedTestModels.sampleModel(true, false, false), Collections.emptyList());
            panel.update(SlayerSpeedTestModels.sampleModel(true, false, false), Collections.emptyList());
            assertEquals(1, changes.get());
            assertEquals(SlayerSpeedDisplayMode.DETAILED, mode[0]);
            assertTrue(((JToggleButton) named(panel, "detailedMode")).isSelected());
        });
    }

    @Test public void historyRemainsSearchableAndExpandableFromSimpleMode() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedPanel panel = panel(new SlayerSpeedConfig() {});
            panel.update(SlayerSpeedTestModels.sampleModel(true, false, false),
                Arrays.asList(history("Araxytes"), history("Bloodveld")));
            assertFalse(named(panel, "historySection").isVisible());
            ((JButton) named(panel, "historyToggle")).doClick();
            assertTrue(named(panel, "historySection").isVisible());
            JTextField search = (JTextField) named(panel, "historySearch");
            search.setText("Araxytes");
            assertNotNull(named(panel, "historyCard:Araxytes"));
            assertNull(named(panel, "historyCard:Bloodveld"));
            ((JButton) named(panel, "historyRunsToggle")).doClick();
            Component runActions = named(panel, "historyRunActions");
            assertNotNull(runActions);
            for (Component current = runActions; current != panel; current = current.getParent())
            {
                assertTrue(current.isVisible());
            }
            search.setText("");
            assertNotNull(named(panel, "historyCard:Bloodveld"));
        });
    }

    private static SlayerSpeedPanel panel(SlayerSpeedConfig config)
    {
        SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> {}, () -> {}, (run, excluded) -> {},
            run -> {}, id -> {}, config);
        panel.update(SlayerSpeedTestModels.sampleModel(true, false, false), Collections.emptyList());
        return panel;
    }
    private static TaskStatistics history(String name)
    {
        ActiveTask task = new ActiveTask(name, null, 1, 1, 1000);
        task.addProgressUnits(1);
        task.addActiveMillis(60_000);
        task.setLastRemainingAmount(0);
        TaskStatistics stats = new TaskStatistics(name, null);
        stats.addRun(task.finish(TaskRunStatus.COMPLETED, 61_000), 50);
        return stats;
    }
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
}
