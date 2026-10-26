package com.slayerspeed.ui;

import com.slayerspeed.calculation.TaskEstimate;
import com.slayerspeed.calculation.EstimateWindow;
import com.slayerspeed.calculation.HistoryEstimateService;
import com.slayerspeed.calculation.TaskEstimateService;
import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.TaskStatistics;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Collection;
import javax.swing.*;

/** Read-only snapshot. Deliberately has no tracker, repository or mutation callback. */
public final class TaskPreviewPanel extends JPanel
{
    private final JComboBox<Option> choices = new JComboBox<>();
    private final JSpinner amount = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
    private final JLabel result = new JLabel();
    private final JLabel source = new JLabel();
    private final JLabel cannon = new JLabel();
    private final JTextArea samples = new JTextArea(7, 42);

    public TaskPreviewPanel(Collection<TaskStatistics> histories, int remaining)
    {
        this(histories, remaining, EstimateWindow.LIFETIME);
    }

    public TaskPreviewPanel(Collection<TaskStatistics> histories, int remaining, EstimateWindow window)
    {
        super(new BorderLayout(0, 8));
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 5));
        controls.add(new JLabel("Task / encounter / location"));
        for (TaskStatistics history : histories) { choices.addItem(new Option(history, window)); }
        choices.getAccessibleContext().setAccessibleName("Personal history to preview");
        amount.getAccessibleContext().setAccessibleName("Task units remaining");
        amount.setValue(Math.max(1, Math.min(10000, remaining)));
        controls.add(choices);
        controls.add(new JLabel("Task units remaining"));
        controls.add(amount);
        add(controls, BorderLayout.NORTH);
        JPanel output = new JPanel(new GridLayout(0, 1, 0, 6));
        output.add(result);
        output.add(source);
        output.add(cannon);
        output.add(new JLabel("Preview only — recording is unchanged."));
        add(output, BorderLayout.CENTER);
        samples.setEditable(false);
        samples.setLineWrap(true);
        samples.setWrapStyleWord(true);
        JScrollPane sampleScroll = new JScrollPane(samples);
        sampleScroll.setVisible(false);
        JButton inspect = new JButton("Show estimate samples");
        inspect.addActionListener(event ->
        {
            sampleScroll.setVisible(!sampleScroll.isVisible());
            inspect.setText(sampleScroll.isVisible() ? "Hide estimate samples" : "Show estimate samples");
            java.awt.Window dialogWindow = SwingUtilities.getWindowAncestor(this);
            if (dialogWindow != null) { dialogWindow.pack(); }
        });
        JPanel details = new JPanel(new BorderLayout(0, 5));
        details.add(inspect, BorderLayout.NORTH);
        details.add(sampleScroll, BorderLayout.CENTER);
        add(details, BorderLayout.SOUTH);
        choices.addActionListener(event -> updateEstimate());
        amount.addChangeListener(event -> updateEstimate());
        updateEstimate();
    }

    private void updateEstimate()
    {
        Option option = (Option) choices.getSelectedItem();
        if (option == null)
        {
            result.setText("Complete a task to build personal history.");
            source.setText("");
            cannon.setText("");
            samples.setText("No contributing history.");
            return;
        }
        TaskEstimate estimate = option.estimate((Integer) amount.getValue());
        result.setText(estimate.getEtaMillis().isPresent()
            ? "About " + KphCalculator.formatDuration(estimate.getEtaMillis()) + " of tracked activity"
            : "No timed history for this encounter");
        source.setText(estimate.getDescription());
        samples.setText(option.samples);
        samples.setCaretPosition(0);
        cannon.setText(option.cannonUnits > 0
            ? "If cannoning: about " + KphCalculator.formatCannonballEstimate(
                KphCalculator.estimatedCannonballs((Integer) amount.getValue(),
                    (double) option.balls / option.cannonUnits)) + " cannonballs needed"
            : "No cannon-use history");
    }

    public static final class Option
    {
        private final String label;
        private final String scope;
        private final String samples;
        private final long units;
        private final long millis;
        private final int fullTasks;
        private final int balls;
        private final int cannonUnits;

        public Option(TaskStatistics stats) { this(stats, EstimateWindow.LIFETIME); }

        public Option(TaskStatistics stats, EstimateWindow window)
        {
            HistoryEstimateService service = new HistoryEstimateService();
            stats = service.select(stats, window);
            scope = service.scope(stats, window, stats.getTimingPolicy());
            label = stats.getTaskName() + " / " + stats.getEncounterProfileName()
                + " / " + (stats.getTaskLocation() == null ? "Unspecified location" : stats.getTaskLocation())
                + (stats.getTimingPolicy() == 0 ? " / Legacy" : " / Segmented");
            StringBuilder detail = new StringBuilder(window == null || window == EstimateWindow.LIFETIME
                ? "Retained records only. Lifetime totals can also include pruned records.\n"
                : "Every eligible run contributing to this estimate:\n");
            java.time.format.DateTimeFormatter date = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
            for (com.slayerspeed.model.TaskRun run : stats.getRecentRuns())
            {
                detail.append(date.format(java.time.Instant.ofEpochMilli(run.getCompletedAtMillis())
                    .atZone(java.time.ZoneId.systemDefault()))).append(" — ")
                    .append(run.getRateTaskProgressUnits()).append(" timed units, ")
                    .append(KphCalculator.formatDuration(java.util.OptionalDouble.of(run.getActiveMillis())))
                    .append(run.isFullTaskObserved() ? ", full task" : ", partial")
                    .append(run.isExcludedFromAverages() ? ", excluded" : "")
                    .append(", ").append(run.getStatus()).append("\n");
            }
            samples = detail.toString();
            units = stats.getTotalTaskProgressUnits();
            millis = stats.getTotalActiveMillis();
            fullTasks = stats.getCompletedTaskCount();
            balls = stats.getTotalCannonballsUsed();
            cannonUnits = stats.getTotalCannonRunTaskProgressUnits();
        }

        public TaskEstimate estimate(int remaining)
        {
            return new TaskEstimateService().calculate(remaining, 0, 0, units, millis,
                10, false, fullTasks, scope);
        }

        @Override public String toString() { return label; }
    }
}
