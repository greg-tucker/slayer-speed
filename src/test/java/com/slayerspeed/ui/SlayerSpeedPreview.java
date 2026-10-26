package com.slayerspeed.ui;

import com.slayerspeed.*;
import com.slayerspeed.calculation.*;
import com.slayerspeed.model.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Actual plugin components, synthetic data only. Never creates a Client or ConfigManager. */
public final class SlayerSpeedPreview
{
    enum Scene { ACTIVE, FIRST_TASK, WAITING, COMPLETED, EMPTY, LONG_NAMES, CANNON, DELETED }
    static final class PreviewConfig implements SlayerSpeedConfig
    {
        SlayerSpeedDisplayMode mode = SlayerSpeedDisplayMode.SIMPLE;
        SlayerSpeedOverlayMode overlayMode = SlayerSpeedOverlayMode.SIMPLE;
        @Override public SlayerSpeedOverlayMode overlayDetail() { return overlayMode; }
        @Override public SlayerSpeedDisplayMode displayMode() { return mode; }
    }
    private final PreviewConfig config = new PreviewConfig();
    private Scene scene = Scene.ACTIVE;
    private List<TaskStatistics> history = sampleHistory();
    private String selectedEncounter = EncounterProfileOption.AUTO_ID;
    private boolean completionDismissed;
    private SlayerSpeedPanel sidebar;
    private SlayerSpeedViewModel model;
    private final SlayerSpeedOverlay overlay;
    private final JPanel root = new JPanel(new BorderLayout(12, 12));
    private final JPanel sidebarHost = new JPanel(new BorderLayout());
    private final JPanel overlayHost = new JPanel(new BorderLayout());
    private final JLabel notice = new JLabel("Synthetic preview — no account data or credentials are loaded.");
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(242, 210, 360, 1));
    private final JSpinner overlayWidth = new JSpinner(new SpinnerNumberModel(200, 140, 320, 1));
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(12, 10, 18, 1));
    private final JComboBox<SlayerSpeedDisplayMode> modes = new JComboBox<>(SlayerSpeedDisplayMode.values());
    private boolean gameFont;

    public SlayerSpeedPreview()
    {
        overlay = new SlayerSpeedOverlay(new SlayerSpeedPlugin()
        {
            @Override public boolean isOverlayVisible() { return true; }
            @Override public SlayerSpeedViewModel getViewModel() { return model; }
        }, config);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel controls = new JPanel(new GridLayout(0, 2, 8, 6));
        JComboBox<Scene> scenes = new JComboBox<>(Scene.values());
        JComboBox<String> fonts = new JComboBox<>(new String[] {"RuneLite UI", "RuneScape"});
        controls.add(new JLabel("Scenario")); controls.add(scenes);
        controls.add(new JLabel("Sidebar detail")); controls.add(modes);
        JComboBox<SlayerSpeedOverlayMode> overlayModes = new JComboBox<>(SlayerSpeedOverlayMode.values());
        overlayModes.setSelectedItem(config.overlayMode);
        controls.add(new JLabel("Overlay detail")); controls.add(overlayModes);
        overlayModes.addActionListener(e -> { config.overlayMode = (SlayerSpeedOverlayMode) overlayModes.getSelectedItem(); refresh(); });
        controls.add(new JLabel("Sidebar width (px)")); controls.add(width);
        controls.add(new JLabel("Overlay width (px)")); controls.add(overlayWidth);
        controls.add(new JLabel("Font")); controls.add(fonts);
        controls.add(new JLabel("Font size")); controls.add(fontSize);
        JButton reset = new JButton("Reset scenario");
        JButton export = new JButton("Export PNGs");
        controls.add(reset); controls.add(export);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(notice, BorderLayout.NORTH); top.add(controls, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        JPanel previews = new JPanel(new BorderLayout(16, 0));
        previews.add(sidebarHost, BorderLayout.WEST);
        previews.add(overlayHost, BorderLayout.CENTER);
        root.add(previews, BorderLayout.CENTER);
        scenes.addActionListener(e -> { scene = (Scene) scenes.getSelectedItem(); resetScenario(); });
        modes.addActionListener(e ->
        {
            SlayerSpeedDisplayMode selected = (SlayerSpeedDisplayMode) modes.getSelectedItem();
            if (config.mode != selected) { config.mode = selected; refresh(); }
        });
        width.addChangeListener(e -> refresh());
        overlayWidth.addChangeListener(e -> refresh());
        fonts.addActionListener(e -> { gameFont = fonts.getSelectedIndex() == 1; rebuildForFont(); });
        fontSize.addChangeListener(e -> rebuildForFont());
        reset.addActionListener(e -> resetScenario());
        export.addActionListener(e ->
        {
            try { export(Paths.get("build", "ux-preview"), "interactive"); notice.setText("Saved PNGs to build/ux-preview"); }
            catch (Exception ex) { JOptionPane.showMessageDialog(root, ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE); }
        });
        createSidebar();
        refresh();
    }

    private void createSidebar()
    {
        sidebar = new SlayerSpeedPanel(() -> { history.removeIf(h -> h.getTaskName().equals(taskName())); changed(); },
            () -> { history.clear(); changed(); },
            (run, excluded) -> { history.forEach(h -> h.setRunExcluded(run.getId(), excluded)); changed(); },
            this::delete,
            id -> { selectedEncounter = id; refresh(); }, config);
        sidebar.setDisplayModeAction(mode -> { config.mode = mode; refresh(); });
        sidebar.setDismissCompletionAction(() -> { completionDismissed = true; refresh(); });
        sidebarHost.removeAll();
        JScrollPane scroll = new JScrollPane(sidebar);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarHost.add(scroll);
    }

    private void delete(TaskRun run)
    {
        history.forEach(h -> h.deleteRun(run.getId()));
        history.removeIf(h -> h.getRecentRuns().isEmpty() && h.getTotalTaskProgressUnits() == 0);
        refresh();
    }
    private void changed() { refresh(); }
    private void resetScenario()
    {
        history = scene == Scene.EMPTY || scene == Scene.FIRST_TASK ? new ArrayList<>() : sampleHistory();
        completionDismissed = false; selectedEncounter = EncounterProfileOption.AUTO_ID;
        createSidebar(); refresh();
        if (scene == Scene.DELETED) { delete(history.get(2).getRecentRuns().get(0)); }
    }
    private String taskName() { return scene == Scene.LONG_NAMES ? "Greater Demons (Wilderness Slayer Cave)" : "Greater Demons"; }

    private SlayerSpeedViewModel createModel()
    {
        CompletionResult completion = completion();
        if (scene == Scene.EMPTY || scene == Scene.COMPLETED)
        {
            return SlayerSpeedViewModel.noTask(completion == null ? null : completion.getHtml());
        }
        boolean first = scene == Scene.FIRST_TASK;
        boolean cannon = scene == Scene.CANNON;
        boolean matches = !"boss:kril".equals(selectedEncounter);
        TaskStatistics stats = history.stream().filter(h -> h.getTaskName().equals("Greater Demons")).findFirst().orElse(null);
        if (!matches || first) { stats = null; }
        long millis = first ? 60000 : 5563636;
        int units = first ? 2 : 68;
        int remaining = first ? 172 : 102;
        TaskEstimate estimate = new TaskEstimateService().calculate(remaining, units, millis,
            stats == null ? 0 : stats.getTotalTaskProgressUnits(), stats == null ? 0 : stats.getTotalActiveMillis(),
            10, matches, stats == null ? 0 : stats.getCompletedTaskCount(), "Lifetime history");
        return new SlayerSpeedViewModel(true, taskName(), Integer.toString(remaining), units + " kills · " + units + " task units",
            KphCalculator.formatRate(KphCalculator.ratePerHour(units, millis)),
            KphCalculator.formatRate(KphCalculator.ratePerHour(units, millis)), false,
            first ? "36.0k" : "49.6k", KphCalculator.formatDuration(estimate.getEtaMillis()),
            stats == null ? "" : "18% slower", scene == Scene.WAITING ? "" : "14:01", stats != null,
            stats == null ? "--" : "54.1", stats == null ? "--" : "54.1", stats == null ? "--" : "49.3k",
            stats == null ? "--" : "2h 52m", stats == null ? "No saved history yet" : "1 fully observed task",
            estimate.getDescription(), cannon, cannon ? "312" : "0", "This task / kill", cannon ? "4.6" : "--",
            cannon ? "800" : "--", cannon ? "469" : "--", completion == null ? null : completion.getHtml(), true,
            matches ? "Tormented demons" : "K'ril Tsutsaroth",
            EncounterProfileOption.AUTO_ID.equals(selectedEncounter) ? "Based on confirmed kills" : "Manual recording override",
            selectedEncounter, Arrays.asList(new EncounterProfileOption(EncounterProfileOption.AUTO_ID, "Auto: Tormented demons"),
                new EncounterProfileOption("npc:tormented", "Tormented demons"),
                new EncounterProfileOption("boss:kril", "K'ril Tsutsaroth")), estimate)
            .withActivityState(scene == Scene.WAITING ? SlayerSpeedViewModel.ActivityState.SUSPENDED
                : SlayerSpeedViewModel.ActivityState.TRACKING);
    }
    private CompletionResult completion()
    {
        if (scene != Scene.COMPLETED || completionDismissed) { return null; }
        return new CompletionResult(sampleHistory().get(0).getRecentRuns().get(0), true, false, true);
    }
    private void refresh()
    {
        model = createModel();
        modes.setSelectedItem(config.mode);
        sidebar.updateHistoryContext(model.isActive() ? taskName() : "");
        sidebar.update(model, new ArrayList<>(history));
        sidebar.updateActivity(!model.isActive() ? "" : scene == Scene.WAITING
            ? "Waiting for task activity" : "Tracking — capped activity gaps");
        sidebar.updateProgress("preview:" + scene, model.isActive() ? 174 : 0, 174,
            model.isActive() ? (scene == Scene.FIRST_TASK ? 172 : 102) : 0,
            model.isActive() ? (scene == Scene.FIRST_TASK ? 2 : 68) : -1);
        sidebar.updateCompletion(completion());
        sidebarHost.setPreferredSize(new Dimension((Integer) width.getValue() + 18, 700));
        overlayHost.removeAll();
        try
        {
            BufferedImage image = overlayImage();
            JLabel label = image == null ? new JLabel("No active-task overlay") : new JLabel(new ImageIcon(image));
            label.setVerticalAlignment(SwingConstants.TOP);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            overlayHost.add(label, BorderLayout.NORTH);
        }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        applyPreviewFont(sidebar);
        root.revalidate(); root.repaint();
    }
    private BufferedImage overlayImage()
    {
        int w = (Integer) overlayWidth.getValue();
        BufferedImage image = new BufferedImage(w + 20, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(FontManager.getRunescapeFont().deriveFont(((Integer) fontSize.getValue()).floatValue()));
        overlay.setPreferredSize(new Dimension(w, 0));
        overlay.render(graphics);
        graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setComposite(AlphaComposite.SrcOver);
        Dimension size = overlay.render(graphics); graphics.dispose();
        return size == null ? null : image.getSubimage(0, 0, Math.min(image.getWidth(), size.width), Math.min(image.getHeight(), size.height));
    }
    private void rebuildForFont()
    {
        createSidebar(); refresh();
    }
    private void applyPreviewFont(Component component)
    {
        if (component instanceof JComponent && component.getFont() != null)
        {
            JComponent target = (JComponent) component;
            Font original = (Font) target.getClientProperty("preview.originalFont");
            if (original == null)
            {
                original = component.getFont();
                target.putClientProperty("preview.originalFont", original);
            }
            Font family = gameFont ? FontManager.getRunescapeFont() : original;
            component.setFont(family.deriveFont(original.getStyle(),
                original.getSize2D() * ((Integer) fontSize.getValue()) / 12f));
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents()) { applyPreviewFont(child); }
        }
    }
    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
    }
    void export(Path directory, String prefix) throws Exception
    {
        Files.createDirectories(directory);
        int w = (Integer) width.getValue();
        sidebar.setSize(w, 2000); layout(sidebar);
        int h = sidebar.getPreferredSize().height;
        sidebar.setSize(w, h); layout(sidebar);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics(); sidebar.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", directory.resolve(prefix + "-sidebar.png").toFile());
        BufferedImage overlayImage = overlayImage();
        if (overlayImage != null) { ImageIO.write(overlayImage, "png", directory.resolve(prefix + "-overlay.png").toFile()); }
    }
    static List<TaskStatistics> sampleHistory()
    {
        List<TaskStatistics> samples = new ArrayList<>();
        samples.add(history("Greater Demons", "npc:tormented", "Tormented demons", 155, 141327, 10320000));
        samples.add(history("Black Dragons", "npc:black-dragon", "Black dragons", 30, 1242, 208000));
        samples.add(history("Fire Giants", "npc:fire-giant", "Fire giants", 0, 0, 0));
        return samples;
    }
    private static TaskStatistics history(String name, String profile, String display, int units, int xp, long millis)
    {
        TaskStatistics statistics = new TaskStatistics(name, null, profile, display);
        statistics.addRun(new TaskRun("preview-" + profile, name, null, profile, display, units, 0, true,
            units, units, xp, 0, 0, millis, 0, 1700000000000L, 1700000000000L + millis,
            units > 0 ? TaskRunStatus.COMPLETED : TaskRunStatus.UNKNOWN_INCOMPLETE), 50);
        return statistics;
    }
    public static void main(String[] args) throws Exception
    {
        boolean render = Arrays.asList(args).contains("--render");
        SwingUtilities.invokeAndWait(() ->
        {
            RuneLiteLAF.setup();
            SlayerSpeedPreview preview = new SlayerSpeedPreview();
            if (render)
            {
                try
                {
                    for (Scene scene : Scene.values())
                    {
                        preview.scene = scene; preview.resetScenario();
                        preview.export(Paths.get("build", "ux-preview"), scene.name().toLowerCase(Locale.ENGLISH));
                    }
                    preview.scene = Scene.ACTIVE; preview.config.mode = SlayerSpeedDisplayMode.DETAILED;
                    preview.resetScenario(); preview.export(Paths.get("build", "ux-preview"), "detailed");
                    preview.config.mode = SlayerSpeedDisplayMode.SIMPLE;
                    preview.width.setValue(225); preview.fontSize.setValue(16); preview.gameFont = true;
                    preview.rebuildForFont(); preview.export(Paths.get("build", "ux-preview"), "narrow-large-font");
                    System.out.println("Rendered synthetic previews to build/ux-preview");
                }
                catch (Exception ex) { throw new IllegalStateException(ex); }
            }
            else
            {
                JFrame frame = new JFrame("Slayer Task Speed — standalone UI preview (synthetic data)");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setContentPane(preview.root); frame.setSize(850, 1000); frame.setLocationByPlatform(true);
                frame.setVisible(true);
                System.out.println("Standalone preview ready. Close the window to stop it.");
            }
        });
    }
}
