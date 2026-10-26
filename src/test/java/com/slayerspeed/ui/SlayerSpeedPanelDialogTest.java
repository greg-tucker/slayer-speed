package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerSpeedPanelDialogTest
{
    @Test
    public void repeatedComparisonWindowsAreDisposedAndFollowContextCleanup() throws Exception
    {
        // Exercise actual window ownership where a display is available.
        if (Boolean.getBoolean("slayerspeed.requireDisplay"))
        {
            assertFalse("Window tests require a working display", GraphicsEnvironment.isHeadless());
        }
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() ->
        {
            SlayerSpeedPanel panel = new SlayerSpeedPanel(() -> {}, () -> {},
                (run, excluded) -> {}, run -> {}, id -> {}, new SlayerSpeedConfig() {});
            panel.update(SlayerSpeedTestModels.sampleModel(true, false, false), Collections.emptyList());
            panel.updateProgress("profile:task", 100, 100, 50);
            JButton compare = findCompareButton(panel);
            List<Window> originalWindows = java.util.Arrays.asList(Window.getWindows());
            try
            {
                compare.doClick();
                JDialog first = onlyOpenComparison(originalWindows);
                compare.doClick();
                JDialog second = onlyOpenComparison(originalWindows);
                assertNotSame(first, second);
                assertFalse("Previous comparison must be disposed", first.isDisplayable());

                panel.updateProgress("another-profile:task", 100, 100, 50);
                assertFalse("Account/task changes must close the comparison", second.isDisplayable());
                compare.doClick();
                JDialog third = onlyOpenComparison(originalWindows);
                panel.removeNotify();
                assertFalse("Removing the panel must close the comparison", third.isDisplayable());
            }
            finally
            {
                for (Window window : Window.getWindows())
                {
                    if (!originalWindows.contains(window)) { window.dispose(); }
                }
            }
        });
    }

    private static JDialog onlyOpenComparison(List<Window> originalWindows)
    {
        List<JDialog> dialogs = new ArrayList<>();
        for (Window window : Window.getWindows())
        {
            if (!originalWindows.contains(window) && window instanceof JDialog && window.isDisplayable()
                && "Compare personal task estimates".equals(((JDialog) window).getTitle()))
            {
                dialogs.add((JDialog) window);
            }
        }
        assertEquals("Exactly one comparison window should remain open", 1, dialogs.size());
        return dialogs.get(0);
    }

    private static JButton findCompareButton(Container container)
    {
        for (Component component : container.getComponents())
        {
            if (component instanceof JButton && "compareEstimates".equals(component.getName()))
            {
                return (JButton) component;
            }
            if (component instanceof Container)
            {
                JButton found = findCompareButton((Container) component);
                if (found != null) { return found; }
            }
        }
        return null;
    }
}
