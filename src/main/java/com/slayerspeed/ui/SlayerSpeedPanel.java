package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.SlayerSpeedDisplayMode;
import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.JProgressBar;
import javax.swing.JDialog;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class SlayerSpeedPanel extends PluginPanel
{
	private static final DateTimeFormatter RUN_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm");

	private final JLabel storageStatus = new JLabel();
    private String recoveryPayload;
    private final JPanel completionActions = new JPanel(new GridLayout(0, 1, 0, 4));
    private final JButton copyCompletion = new JButton("Copy result");
    private final JButton reviewCompletion = new JButton("Review");
    private final JButton dismissCompletion = new JButton("Dismiss");
    private CompletionResult completionResult;
    private Runnable dismissCompletionAction = () -> {};
    public void setDismissCompletionAction(Runnable action) { dismissCompletionAction = action; }
    public String getActionContext() { return previewContext; }
    public void updateCompletion(CompletionResult result)
    {
        completionResult = result;
        completionActions.setVisible(config.showCompletionSummary() && result != null);
    }
    private Consumer<String> dataActions = action -> {};
    private boolean undoAvailable;

    public void setDataActions(Consumer<String> actions) { dataActions = actions; }
    public void setUndoAvailable(boolean available) { undoAvailable = available; }
    private final JLabel taskLabel = new JLabel("No active Slayer task", SwingConstants.CENTER);
	private final JLabel taskStatusLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel completionSummaryLabel = new JLabel("", SwingConstants.CENTER);
	private final JPanel encounterSection = new JPanel();
	private final JComboBox<EncounterProfileOption> encounterSelector = new JComboBox<>();
	private final JLabel encounterNoteLabel = new JLabel("", SwingConstants.CENTER);
	private boolean updatingEncounterSelector;

	private final JLabel timingStatus = new JLabel();
    private final JButton pauseButton = new JButton("Pause");
    private Runnable pauseAction = () -> {};
    public void setPauseAction(Runnable action) { pauseAction = action; }
    public void updateTiming(String status, boolean enabled, boolean paused)
    {
        timingStatus.setText("<html><center>" + status + "</center></html>");
        timingStatus.setVisible(!status.isEmpty());
        pauseButton.setVisible(enabled);
        pauseButton.setText(paused ? "Resume" : "Pause");
    }

    private final JProgressBar progress = new JProgressBar();
    private JDialog previewDialog;
    private String previewContext = "";
    private int previewRemaining = 100;
    private final JPanel summaryCard = metricCard();
	private final JLabel remainingValue = valueLabel();
	private final JLabel etaValue = valueLabel();
	private final JLabel finishValue = valueLabel();
	private final JLabel finishLabel;

	private final JLabel currentTitle = sectionTitle("This task");
	private final JPanel currentCard = metricCard();
	private final JLabel currentKillsValue = valueLabel();
	private final JLabel currentUnitsValue = valueLabel();
	private final JLabel currentXpValue = valueLabel();
	private final JLabel paceValue = valueLabel();
	private final JLabel xpNoteLabel = new JLabel("Superior XP included");
	private final JLabel currentKillsLabel;
	private final JLabel currentUnitsLabel;
	private final JLabel currentXpLabel;
	private final JLabel paceLabel;

	private final JLabel averageTitle = sectionTitle("Your average");
	private final JPanel averageCard = metricCard();
	private final JLabel averageUnitsValue = valueLabel();
	private final JLabel averageDurationValue = valueLabel();
	private final JLabel averageKillsValue = valueLabel();
	private final JLabel averageXpValue = valueLabel();
	private final JLabel averageDurationLabel;
	private final JLabel averageKillsLabel;
	private final JLabel averageXpLabel;
	private final JLabel historyMessageLabel = new JLabel("Complete a task to build your personal averages.");
	private final JLabel confidenceLabel = new JLabel("");

	private final JLabel cannonTitle = sectionTitle("Cannon");
	private final JPanel cannonSection = sectionPanel();
	private final JPanel cannonCard = metricCard();
	private final JLabel cannonUsedValue = valueLabel();
	private final JLabel cannonRateValue = valueLabel();
	private final JLabel cannonRemainingValue = valueLabel();
	private final JLabel cannonTotalValue = valueLabel();
	private final JLabel cannonRateLabel;
	private final JLabel cannonTotalLabel;

	private final JPanel historyPanel = fullWidthBoxPanel(ColorScheme.DARK_GRAY_COLOR);
	private final Set<String> expandedHistoryKeys = new HashSet<>();
	private final Set<String> showAllHistoryKeys = new HashSet<>();
    private final JPanel historyControls = fullWidthBoxPanel(ColorScheme.DARK_GRAY_COLOR);
    private final javax.swing.JTextField historySearch = new javax.swing.JTextField();
    private final javax.swing.JCheckBox currentTaskOnly = new javax.swing.JCheckBox("Current task only");
    private final JComboBox<String> historySort = new JComboBox<>(new String[] {"Most recent", "Task name", "Fastest pace"});
    private Collection<TaskStatistics> displayedHistory = Collections.emptyList();
    private String activeHistoryTask = "";
    public void updateHistoryContext(String taskName)
    {
        if (!Objects.equals(activeHistoryTask, taskName))
        {
            activeHistoryTask = taskName;
            historySignature = Long.MIN_VALUE;
        }
    }
    private long historySignature = Long.MIN_VALUE;
	private List<TaskStatistics> storedStatistics = Collections.emptyList();

	private final Runnable resetCurrentHistory;
	private final Runnable resetAllHistory;
	private final BiConsumer<TaskRun, Boolean> setRunExcluded;
	private final Consumer<TaskRun> deleteRun;
	private final Consumer<String> selectEncounterProfile;
	private final SlayerSpeedConfig config;

	public SlayerSpeedPanel(
		Runnable resetCurrentHistory,
		Runnable resetAllHistory,
		BiConsumer<TaskRun, Boolean> setRunExcluded,
		Consumer<TaskRun> deleteRun,
		Consumer<String> selectEncounterProfile,
		SlayerSpeedConfig config)
	{
		this.resetCurrentHistory = resetCurrentHistory;
		this.resetAllHistory = resetAllHistory;
		this.setRunExcluded = setRunExcluded;
		this.deleteRun = deleteRun;
		this.selectEncounterProfile = selectEncounterProfile;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		taskLabel.setForeground(Color.WHITE);
		taskLabel.setAlignmentX(CENTER_ALIGNMENT);
        storageStatus.setForeground(new Color(255, 190, 100));
        storageStatus.setAlignmentX(CENTER_ALIGNMENT);
        storageStatus.setVisible(false);
        add(storageStatus);
        add(taskLabel);
		taskStatusLabel.setForeground(Color.LIGHT_GRAY);
		taskStatusLabel.setAlignmentX(CENTER_ALIGNMENT);
		add(taskStatusLabel);
		completionSummaryLabel.setForeground(Color.LIGHT_GRAY);
		completionSummaryLabel.setAlignmentX(CENTER_ALIGNMENT);
		completionSummaryLabel.setVisible(false);
        add(completionSummaryLabel);
        completionActions.add(copyCompletion);
        completionActions.add(reviewCompletion);
        completionActions.add(dismissCompletion);
        reviewCompletion.addActionListener(event ->
        {
            if (completionResult == null) { return; }
            currentTaskOnly.setSelected(false);
            historySearch.setText("");
            for (TaskStatistics stats : displayedHistory)
            {
                if (stats.getRecentRuns().stream().anyMatch(run -> run.getId().equals(completionResult.getRunId())))
                {
                    expandedHistoryKeys.add(historyKey(stats));
                    showAllHistoryKeys.add(historyKey(stats));
                    historySearch.setText(stats.getTaskName());
                }
            }
            rebuildHistory(displayedHistory);
            historyPanel.scrollRectToVisible(new java.awt.Rectangle(0, 0, historyPanel.getWidth(), 60));
        });
        completionActions.setVisible(false);
        add(completionActions);
        copyCompletion.addActionListener(event ->
        {
            if (completionResult != null)
            {
                try
                {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(completionResult.getText()), null);
                    copyCompletion.setToolTipText("Result copied");
                }
                catch (IllegalStateException ex) { copyCompletion.setToolTipText("Clipboard busy — try again"); }
            }
        });
        dismissCompletion.addActionListener(event -> dismissCompletionAction.run());

		encounterSection.setLayout(new BoxLayout(encounterSection, BoxLayout.Y_AXIS));
		encounterSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		encounterSection.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		JLabel encounterTitle = sectionTitle("Record this task as");
		encounterTitle.setAlignmentX(CENTER_ALIGNMENT);
		encounterSection.add(encounterTitle);
		encounterSection.add(Box.createRigidArea(new Dimension(0, 3)));
		encounterSelector.setAlignmentX(CENTER_ALIGNMENT);
		encounterSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, encounterSelector.getPreferredSize().height));
		encounterSelector.setToolTipText(
			"Auto follows confirmed kills. A manual choice records the whole run under that encounter; use Compare for previews.");
		encounterSelector.addActionListener(event ->
		{
			if (!updatingEncounterSelector)
			{
				EncounterProfileOption selected = (EncounterProfileOption) encounterSelector.getSelectedItem();
				if (selected != null)
				{
					selectEncounterProfile.accept(selected.getId());
				}
			}
		});
		encounterSection.add(encounterSelector);
		encounterNoteLabel.setForeground(Color.GRAY);
		encounterNoteLabel.setAlignmentX(CENTER_ALIGNMENT);
		encounterSection.add(encounterNoteLabel);
		encounterSection.setVisible(false);
		add(encounterSection);
		addGap(8);

		addMetric(summaryCard, "Remaining", remainingValue, "Monsters or task units remaining");
		addMetric(summaryCard, "ETA", etaValue, "Estimated time until the task finishes");
		finishLabel = addMetric(summaryCard, "Est. finish", finishValue,
			"If you continue at this estimated pace; future breaks are not forecast");
        etaValue.setFont(etaValue.getFont().deriveFont(Font.BOLD, 19f));
        add(summaryCard);
        progress.setStringPainted(true);
        progress.getAccessibleContext().setAccessibleName("Assignment progress");
        progress.setVisible(false);
        progress.setAlignmentX(CENTER_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        add(progress);
        timingStatus.setAlignmentX(CENTER_ALIGNMENT);
        timingStatus.setForeground(Color.LIGHT_GRAY);
        add(timingStatus);
        pauseButton.setAlignmentX(CENTER_ALIGNMENT);
        pauseButton.setVisible(false);
        pauseButton.addActionListener(event -> pauseAction.run());
        add(pauseButton);
        JButton compare = new JButton("Compare estimates...");
        compare.setName("compareEstimates");
        compare.setAlignmentX(CENTER_ALIGNMENT);
        compare.addActionListener(event -> showPreview());
        add(compare);

		addGap(10);
		add(currentTitle);
		addGap(4);
		currentKillsLabel = addMetric(currentCard, "Kills/hr", currentKillsValue,
			"Confirmed physical task kills per hour during this task");
		currentUnitsLabel = addMetric(currentCard, "Task units/hr", currentUnitsValue,
			"Task-counter progress per hour during this task; this drives ETA");
		currentXpLabel = addMetric(currentCard, "Slayer XP/hr", currentXpValue,
			"Includes superior monsters, so short samples can spike");
		paceLabel = addMetric(currentCard, "Vs. average", paceValue,
			"Current task-unit rate compared with your learned average");
		add(currentCard);
		xpNoteLabel.setForeground(Color.GRAY);
		xpNoteLabel.setToolTipText("Slayer XP/hr includes XP from superior monsters");
		xpNoteLabel.setAlignmentX(CENTER_ALIGNMENT);
		add(xpNoteLabel);

		addGap(10);
		add(averageTitle);
		addGap(4);
		addMetric(averageCard, "Task units/hr", averageUnitsValue, "Your weighted historical task-unit rate");
		averageDurationLabel = addMetric(averageCard, "Average task time", averageDurationValue,
			"Average active duration of fully observed completed tasks");
		averageKillsLabel = addMetric(averageCard, "Kills/hr", averageKillsValue,
			"Your weighted historical confirmed-kill rate");
		averageXpLabel = addMetric(averageCard, "Slayer XP/hr", averageXpValue,
			"Your weighted historical Slayer XP rate, including superior monsters");
		add(averageCard);
		historyMessageLabel.setForeground(Color.LIGHT_GRAY);
		historyMessageLabel.setAlignmentX(CENTER_ALIGNMENT);
		add(historyMessageLabel);
		confidenceLabel.setForeground(Color.LIGHT_GRAY);
		confidenceLabel.setAlignmentX(CENTER_ALIGNMENT);
		add(confidenceLabel);

		cannonSection.add(cannonTitle, BorderLayout.NORTH);
		addMetric(cannonCard, "Used this task", cannonUsedValue,
			"Cannonballs fired during this assignment; reloads are not counted");
		cannonRateLabel = addMetric(cannonCard, "Average / kill", cannonRateValue,
			"Cannonballs consumed per confirmed Slayer kill");
		addMetric(cannonCard, "Cannonballs needed", cannonRemainingValue,
			"Estimated cannonballs needed for the remaining task units");
		cannonTotalLabel = addMetric(cannonCard, "Est. total balls", cannonTotalValue,
			"Estimated cannonballs for the full assignment size");
		cannonSection.add(cannonCard, BorderLayout.CENTER);
		cannonSection.setBorder(BorderFactory.createEmptyBorder(10, 0, 12, 0));
		add(cannonSection);

		JLabel historyTitle = sectionTitle("Task history");
        add(historyTitle);
        historySearch.setName("historySearch");
        historySearch.getAccessibleContext().setAccessibleName("Search task, encounter or location");
        historySearch.setToolTipText("Search task, encounter or location");
        historySearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        historySearch.putClientProperty("JTextField.placeholderText", "Search history");
        JLabel searchLabel = new JLabel("Search history");
        searchLabel.setForeground(Color.LIGHT_GRAY);
        historyControls.add(searchLabel);
        historyControls.add(historySearch);
        currentTaskOnly.setAlignmentX(LEFT_ALIGNMENT);
        currentTaskOnly.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        historyControls.add(currentTaskOnly);
        historySort.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        historyControls.add(historySort);
        historyControls.setVisible(false);
        add(historyControls);
        historySearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { rebuildHistory(displayedHistory); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { rebuildHistory(displayedHistory); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { rebuildHistory(displayedHistory); }
        });
        currentTaskOnly.addActionListener(event -> rebuildHistory(displayedHistory));
        historySort.addActionListener(event -> rebuildHistory(displayedHistory));
		addGap(5);
		add(historyPanel, BorderLayout.CENTER);

		addGap(10);

		JButton dataManagement = new JButton("Help & data...");
		dataManagement.setAlignmentX(CENTER_ALIGNMENT);
		dataManagement.addActionListener(event -> showDataManagementMenu(dataManagement));
		add(dataManagement);
	}

	public void update(SlayerSpeedViewModel model, Collection<TaskStatistics> history)
	{
		update(model, history, history);
	}

	public void update(
		SlayerSpeedViewModel model,
		Collection<TaskStatistics> history,
		Collection<TaskStatistics> exactStoredStatistics)
	{
		storedStatistics = exactStoredStatistics == null
			? Collections.emptyList()
			: new ArrayList<>(exactStoredStatistics);
		boolean detailed = config.displayMode() == SlayerSpeedDisplayMode.DETAILED;
		taskLabel.setText(model.getTask());
		taskStatusLabel.setText(model.isActive() ? model.getObservedCounts() : "");
		taskStatusLabel.setVisible(detailed && model.isActive());
		updateEncounterSelector(model);
		summaryCard.setVisible(model.isActive());
		remainingValue.setText(model.getRemaining());
		etaValue.setText("--".equals(model.getEta()) ? "Waiting" : "~" + model.getEta());
		finishValue.setText(model.getEstimatedFinishTime());
		setMetricVisible(finishLabel, finishValue,
			config.showEstimatedFinishTime() && model.hasEstimatedFinishTime());

		currentKillsValue.setText(model.getCurrentLiteralKph());
		currentUnitsValue.setText(model.getCurrentEffectiveKph());
		currentXpValue.setText(model.getCurrentSlayerXpPerHour());
		paceValue.setText(model.getPaceComparison());
		boolean hasCurrentRates = !"--".equals(model.getCurrentLiteralKph())
			|| !"--".equals(model.getCurrentEffectiveKph())
			|| !"--".equals(model.getCurrentSlayerXpPerHour());
		currentTitle.setVisible(detailed && model.isActive() && hasCurrentRates);
		currentCard.setVisible(detailed && model.isActive() && hasCurrentRates);
		setMetricVisible(currentKillsLabel, currentKillsValue, !"--".equals(model.getCurrentLiteralKph()));
		setMetricVisible(currentUnitsLabel, currentUnitsValue,
			!"--".equals(model.getCurrentEffectiveKph())
				&& (detailed || model.isCurrentRatesDiffer() || "--".equals(model.getCurrentLiteralKph())));
		setMetricVisible(currentXpLabel, currentXpValue, !"--".equals(model.getCurrentSlayerXpPerHour()));
		xpNoteLabel.setVisible(detailed && model.isActive() && !"--".equals(model.getCurrentSlayerXpPerHour()));
		setMetricVisible(paceLabel, paceValue,
			config.showPaceComparison() && model.hasPaceComparison());

		averageUnitsValue.setText(model.getHistoricalEffectiveKph());
		averageDurationValue.setText(model.getHistoricalAverageDuration());
		averageKillsValue.setText(model.getHistoricalLiteralKph());
		averageXpValue.setText(model.getHistoricalSlayerXpPerHour());
		averageTitle.setText(model.isHistoryAvailable() ? "Your average" : "First estimate");
		averageTitle.setVisible(detailed && model.isActive());
		averageCard.setVisible(detailed && model.isActive() && model.isHistoryAvailable());
		setMetricVisible(averageDurationLabel, averageDurationValue,
			model.isHistoryAvailable() && !"--".equals(model.getHistoricalAverageDuration()));
		setMetricVisible(averageKillsLabel, averageKillsValue, detailed && model.isHistoryAvailable());
		setMetricVisible(averageXpLabel, averageXpValue, detailed && model.isHistoryAvailable());
		historyMessageLabel.setText(model.getHistorySample());
		historyMessageLabel.setVisible(model.isActive() && detailed);
		confidenceLabel.setText("<html><div style='width:150px;text-align:center'>" + model.getEstimateDescription() + "</div></html>");
        confidenceLabel.setVisible(model.isActive());

		boolean showCannon = config.showCannonMetrics() && model.isCannonRelevant();
		cannonSection.setVisible(showCannon);
		cannonUsedValue.setText(model.getCannonballsUsed());
        cannonTitle.setText("0".equals(model.getCannonballsUsed()) ? "If using a cannon" : "Cannon");
		cannonRateLabel.setText(model.getCannonRateLabel());
		cannonRateValue.setText(model.getCannonballsPerKill());
		cannonRemainingValue.setText(model.getEstimatedRemainingCannonballs());
		cannonTotalValue.setText(model.getEstimatedTotalCannonballs());
		setMetricVisible(cannonRateLabel, cannonRateValue,
			showCannon && detailed && !"--".equals(model.getCannonballsPerKill()));
		setMetricVisible(cannonTotalLabel, cannonTotalValue,
			showCannon && detailed && !"--".equals(model.getEstimatedTotalCannonballs()));

		String completionSummary = model.getCompletionSummary();
		boolean showSummary = config.showCompletionSummary()
			&& completionSummary != null && !completionSummary.isEmpty();
		completionSummaryLabel.setText(showSummary ? completionSummary : "");
		completionSummaryLabel.setVisible(showSummary);

		long signature = Objects.hash(
			config.showRecentRuns(), config.recentRunsShown(), config.showCannonMetrics(), config.displayMode());

		if (signature != historySignature || displayedHistory != history)
		{
			historySignature = signature;
            displayedHistory = history;
			rebuildHistory(history);
		}
	}

	private void updateEncounterSelector(SlayerSpeedViewModel model)
	{
		boolean visible = model.isActive()
			&& config.showEncounterSelector()
			&& model.isEncounterSelectorRelevant();
		encounterSection.setVisible(visible);
		if (!visible)
		{
			return;
		}

		boolean optionsChanged = encounterSelector.getItemCount() != model.getEncounterOptions().size();
		if (!optionsChanged)
		{
			for (int index = 0; index < encounterSelector.getItemCount(); index++)
			{
				EncounterProfileOption existing = encounterSelector.getItemAt(index);
				EncounterProfileOption updated = model.getEncounterOptions().get(index);
				if (!existing.getId().equals(updated.getId())
					|| !existing.toString().equals(updated.toString()))
				{
					optionsChanged = true;
					break;
				}
			}
		}

		updatingEncounterSelector = true;
		try
		{
			if (optionsChanged)
			{
				encounterSelector.removeAllItems();
				for (EncounterProfileOption option : model.getEncounterOptions())
				{
					encounterSelector.addItem(option);
				}
			}
			for (int index = 0; index < encounterSelector.getItemCount(); index++)
			{
				EncounterProfileOption option = encounterSelector.getItemAt(index);
				if (option.getId().equals(model.getSelectedEncounterOptionId()))
				{
					encounterSelector.setSelectedIndex(index);
					break;
				}
			}
		}
		finally
		{
			updatingEncounterSelector = false;
		}
		encounterNoteLabel.setText(model.getEncounterSelectionNote());
	}

	private void rebuildHistory(Collection<TaskStatistics> history)
	{
		historyPanel.removeAll();
        historyControls.setVisible(!history.isEmpty());
		Set<String> availableKeys = new HashSet<>();
		if (history.isEmpty())
		{
			historyPanel.add(createHistoryOnboarding());
		}
		else
		{
            List<TaskStatistics> filtered = new ArrayList<>();
            String query = historySearch.getText().trim().toLowerCase(java.util.Locale.ENGLISH);
            for (TaskStatistics statistics : history)
            {
                availableKeys.add(historyKey(statistics));
                String searchable = (statistics.getTaskName() + " " + statistics.getEncounterProfileName()
                    + " " + Objects.toString(statistics.getTaskLocation(), "")).toLowerCase(java.util.Locale.ENGLISH);
                if (searchable.contains(query) && (!currentTaskOnly.isSelected()
                    || statistics.getTaskName().equalsIgnoreCase(activeHistoryTask))) { filtered.add(statistics); }
            }
            java.util.Comparator<TaskStatistics> order = java.util.Comparator
                .comparingLong(TaskStatistics::getLastUpdatedAtMillis).reversed();
            if (historySort.getSelectedIndex() == 1) { order = java.util.Comparator.comparing(TaskStatistics::getTaskName); }
            if (historySort.getSelectedIndex() == 2)
            {
                order = java.util.Comparator.comparingDouble((TaskStatistics stats) ->
                    KphCalculator.effectiveKph(stats.getTotalTaskProgressUnits(), stats.getTotalActiveMillis()).orElse(0)).reversed();
            }
            filtered.sort(order.thenComparing(SlayerSpeedPanel::historyKey));
            if (filtered.isEmpty()) { historyPanel.add(new JLabel("No matching history")); }
            for (TaskStatistics statistics : filtered)
            {
                String historyKey = historyKey(statistics);
				availableKeys.add(historyKey);
				historyPanel.add(createHistoryCard(statistics, historyKey));
				historyPanel.add(Box.createRigidArea(new Dimension(0, 6)));
			}
		}
		expandedHistoryKeys.retainAll(availableKeys);
		showAllHistoryKeys.retainAll(availableKeys);
		historyPanel.revalidate();
		historyPanel.repaint();
	}

	private JPanel createHistoryOnboarding()
	{
		JPanel card = fullWidthBoxPanel(ColorScheme.DARKER_GRAY_COLOR);
		card.setName("historyOnboarding");
		card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Getting started");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		card.add(leftTextRow(title, ColorScheme.DARKER_GRAY_COLOR));
		card.add(Box.createRigidArea(new Dimension(0, 5)));

		JLabel introduction = new JLabel(
			"<html><div style='width:145px'>Live estimates from your pace.<br>Personal history for next time.</div></html>");
		introduction.setForeground(Color.LIGHT_GRAY);
		card.add(leftTextRow(introduction, ColorScheme.DARKER_GRAY_COLOR));
		card.add(Box.createRigidArea(new Dimension(0, 9)));

		card.add(createOnboardingStep(1, "Start a Slayer task", "Tracking starts automatically."));
		card.add(Box.createRigidArea(new Dimension(0, 7)));
		card.add(createOnboardingStep(2, "See your live estimate", "Based on timed task activity."));
		card.add(Box.createRigidArea(new Dimension(0, 7)));
		card.add(createOnboardingStep(3, "Build personal history", "Save your pace for next time."));
		card.add(Box.createRigidArea(new Dimension(0, 9)));

		String historyHelp = config.showRecentRuns()
			? "<html>Saved locally. Expand tasks for details.<br>"
				+ "Exclude or delete runs when needed.</html>"
			: "<html>Saved locally. Enable individual runs in<br>"
				+ "plugin settings for review controls.</html>";
		JLabel note = new JLabel(historyHelp);
		note.setForeground(Color.GRAY);
		note.setFont(note.getFont().deriveFont(Math.max(9f, note.getFont().getSize2D() - 1f)));
		card.add(leftTextRow(note, ColorScheme.DARKER_GRAY_COLOR));
		return card;
	}

	private static JPanel createOnboardingStep(int step, String titleText, String descriptionText)
	{
		JPanel row = fullWidthPanel(new BorderLayout(8, 0), ColorScheme.DARKER_GRAY_COLOR);
		JLabel number = new JLabel(Integer.toString(step), SwingConstants.CENTER);
		number.setOpaque(true);
		number.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		number.setForeground(Color.WHITE);
		number.setFont(number.getFont().deriveFont(Font.BOLD));
		number.setPreferredSize(new Dimension(22, 22));
		number.setMinimumSize(new Dimension(22, 22));
		number.setMaximumSize(new Dimension(22, 22));
		row.add(number, BorderLayout.WEST);

		JPanel copy = fullWidthBoxPanel(ColorScheme.DARKER_GRAY_COLOR);
		JLabel title = new JLabel("<html><div style='width:120px'>" + titleText + "</div></html>");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		title.setAlignmentX(LEFT_ALIGNMENT);
		copy.add(title);
		JLabel description = new JLabel("<html><div style=\"width:120px\">" + descriptionText + "</div></html>");
		description.setForeground(Color.LIGHT_GRAY);
		description.setAlignmentX(LEFT_ALIGNMENT);
		copy.add(description);
		row.add(copy, BorderLayout.CENTER);
		return row;
	}

	private JPanel createHistoryCard(TaskStatistics statistics, String historyKey)
	{
		JPanel card = fullWidthBoxPanel(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		String location = statistics.getTaskLocation();
		String taskName = location == null || location.isEmpty()
			? statistics.getTaskName()
			: statistics.getTaskName() + " (" + location + ")";
		String profileName = statistics.getEncounterProfileName();

		JPanel heading = fullWidthPanel(new BorderLayout(6, 0), ColorScheme.DARKER_GRAY_COLOR);
		JPanel titles = fullWidthBoxPanel(ColorScheme.DARKER_GRAY_COLOR);
		JLabel name = new JLabel(taskName);
		name.setForeground(Color.WHITE);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setAlignmentX(LEFT_ALIGNMENT);
		titles.add(name);
        JLabel timing = new JLabel(statistics.getTimingPolicy() == 0 ? "Legacy timing" : "Segmented timing");
        timing.setForeground(Color.GRAY);
        titles.add(timing);
		if (shouldShowProfileName(
			statistics.getTaskName(), statistics.getEncounterProfileId(), profileName))
		{
			JLabel profile = new JLabel(profileName);
			profile.setForeground(Color.GRAY);
			profile.setFont(profile.getFont().deriveFont(Math.max(9f, profile.getFont().getSize2D() - 1f)));
			profile.setAlignmentX(LEFT_ALIGNMENT);
			titles.add(profile);
		}
		heading.add(titles, BorderLayout.CENTER);

		OptionalDouble effective = KphCalculator.effectiveKph(
			statistics.getTotalTaskProgressUnits(), statistics.getTotalActiveMillis());
		OptionalDouble literal = KphCalculator.literalKph(
			statistics.getTotalActualKills(), statistics.getTotalActiveMillis());
		OptionalDouble xp = KphCalculator.slayerXpPerHour(
			statistics.getTotalSlayerXp(), statistics.getTotalActiveMillis());
		OptionalDouble averageDuration = statistics.getCompletedTaskCount() > 0
			? OptionalDouble.of((double) statistics.getTotalCompletedTaskMillis() / statistics.getCompletedTaskCount())
			: OptionalDouble.empty();

		List<TaskRun> recentRuns = statistics.getRecentRuns();
		JPanel runDetails = config.showRecentRuns() && !recentRuns.isEmpty()
			? createRunDetails(statistics, historyKey, recentRuns)
			: null;
		if (runDetails != null)
		{
			boolean expanded = expandedHistoryKeys.contains(historyKey);
			JButton toggle = new JButton(historyToggleText(recentRuns.size(), expanded));
			toggle.setName("historyRunsToggle");
			toggle.setToolTipText(expanded ? "Hide individual task runs" : "Show individual task runs");
			toggle.setMargin(new Insets(2, 6, 2, 6));
			toggle.addActionListener(event ->
			{
				boolean show = !runDetails.isVisible();
				runDetails.setVisible(show);
				if (show)
				{
					expandedHistoryKeys.add(historyKey);
				}
				else
				{
					expandedHistoryKeys.remove(historyKey);
				}
				toggle.setText(historyToggleText(recentRuns.size(), show));
				toggle.setToolTipText(show ? "Hide individual task runs" : "Show individual task runs");
				historyPanel.revalidate();
				historyPanel.repaint();
			});
			heading.add(centeredButtonWrapper(toggle, ColorScheme.DARKER_GRAY_COLOR), BorderLayout.EAST);
		}
		card.add(heading);
		card.add(Box.createRigidArea(new Dimension(0, 7)));

		JPanel metrics = fullWidthPanel(new GridLayout(1, 2, 6, 0), ColorScheme.DARKER_GRAY_COLOR);
		metrics.add(createHistoryMetric(
			"KPH",
			KphCalculator.formatRate(literal),
			"Confirmed kills per active hour. Task units/hr: " + KphCalculator.formatRate(effective)));
		metrics.add(createHistoryMetric(
			"Slayer XP/hr",
			KphCalculator.formatXpRate(xp),
			"Average Slayer XP per active hour, including superior monsters"));
		card.add(metrics);
		card.add(Box.createRigidArea(new Dimension(0, 6)));

		String taskCount = statistics.getCompletedTaskCount() == 1
			? "1 full task"
			: statistics.getCompletedTaskCount() + " full tasks";
		JLabel sample = new JLabel(
			"Avg " + KphCalculator.formatDuration(averageDuration) + " \u00B7 " + taskCount);
		sample.setForeground(Color.LIGHT_GRAY);
		sample.setToolTipText(statistics.getTotalTaskProgressUnits() + " observed task units");
		card.add(leftTextRow(sample, ColorScheme.DARKER_GRAY_COLOR));

		if (config.displayMode() == SlayerSpeedDisplayMode.DETAILED || ratesDiffer(literal, effective))
		{
			JLabel taskRate = new JLabel("Task units/hr " + KphCalculator.formatRate(effective));
			taskRate.setForeground(Color.GRAY);
			card.add(leftTextRow(taskRate, ColorScheme.DARKER_GRAY_COLOR));
		}

		if (config.showCannonMetrics() && statistics.getTotalCannonballsUsed() > 0)
		{
			OptionalDouble cannonballsPerKill = KphCalculator.cannonballsPerKill(
				statistics.getTotalCannonballsUsed(), statistics.getTotalCannonRunActualKills());
			JLabel cannon = new JLabel("Cannon " + KphCalculator.formatRate(cannonballsPerKill) + "/kill");
			cannon.setForeground(Color.GRAY);
			card.add(leftTextRow(cannon, ColorScheme.DARKER_GRAY_COLOR));
		}

		if (runDetails != null)
		{
			runDetails.setVisible(expandedHistoryKeys.contains(historyKey));
			card.add(runDetails);
		}
		return card;
	}

	private JPanel createRunDetails(
		TaskStatistics statistics,
		String historyKey,
		List<TaskRun> recentRuns)
	{
		JPanel details = fullWidthBoxPanel(ColorScheme.DARKER_GRAY_COLOR);
		details.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
		JSeparator separator = new JSeparator();
		separator.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		JPanel separatorRow = fullWidthPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR);
		separatorRow.add(separator, BorderLayout.CENTER);
		details.add(separatorRow);
		details.add(Box.createRigidArea(new Dimension(0, 6)));

		JLabel title = new JLabel("Individual runs");
		title.setForeground(Color.LIGHT_GRAY);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		details.add(leftTextRow(title, ColorScheme.DARKER_GRAY_COLOR));
		details.add(Box.createRigidArea(new Dimension(0, 4)));

		int initialLimit = Math.min(Math.max(1, config.recentRunsShown()), recentRuns.size());
		boolean showAll = showAllHistoryKeys.contains(historyKey);
		List<JPanel> runSlots = new ArrayList<>();
		for (int index = 0; index < recentRuns.size(); index++)
		{
			TaskRun run = recentRuns.get(index);
			JPanel slot = fullWidthPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR);
			slot.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
			slot.add(createRecentRunRow(run, statistics.isPersonalBest(run)), BorderLayout.CENTER);
			slot.setVisible(showAll || index < initialLimit);
			runSlots.add(slot);
			details.add(slot);
		}

		if (recentRuns.size() > initialLimit)
		{
			JButton showAllButton = new JButton();
			showAllButton.setName("historyShowAllRuns");
			showAllButton.setMargin(new Insets(2, 6, 2, 6));
			updateShowAllButton(showAllButton, showAll, recentRuns.size() - initialLimit);
			showAllButton.addActionListener(event ->
			{
				boolean showEveryRun = !showAllHistoryKeys.contains(historyKey);
				if (showEveryRun)
				{
					showAllHistoryKeys.add(historyKey);
				}
				else
				{
					showAllHistoryKeys.remove(historyKey);
				}
				for (int index = initialLimit; index < runSlots.size(); index++)
				{
					runSlots.get(index).setVisible(showEveryRun);
				}
				updateShowAllButton(
					showAllButton, showEveryRun, recentRuns.size() - initialLimit);
				historyPanel.revalidate();
				historyPanel.repaint();
			});
			details.add(centeredButtonWrapper(showAllButton, ColorScheme.DARKER_GRAY_COLOR));
		}
		return details;
	}

	private JPanel createRecentRunRow(TaskRun run, boolean personalBest)
	{
		JPanel row = fullWidthPanel(new BorderLayout(6, 0), ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		String status = run.isExcludedFromAverages()
			? "Excluded from averages"
			: formatRunStatus(run);
		String best = personalBest ? " \u00B7 PB" : "";
		String cannon = config.showCannonMetrics() && run.getCannonballsUsed() > 0
			? " \u00B7 " + run.getCannonballsUsed() + " balls"
			: "";
		String assignment = run.getInitialAmount() > 0
			? " \u00B7 " + run.getInitialAmount() + " assigned"
			: "";
		String date = RUN_DATE_FORMAT.format(
			Instant.ofEpochMilli(run.getCompletedAtMillis()).atZone(ZoneId.systemDefault()));
		OptionalDouble runKph = KphCalculator.literalKph(run.getRateActualKills(), run.getActiveMillis());
		OptionalDouble runXp = KphCalculator.slayerXpPerHour(run.getRateSlayerXp(), run.getActiveMillis());
		JLabel details = new JLabel(String.format(
			"<html><b>%s%s</b><br>%s \u00B7 %d kills \u00B7 %d units"
				+ "<br>KPH %s \u00B7 XP/hr %s<br>%s%s%s</html>",
			date,
			assignment,
			KphCalculator.formatDuration(OptionalDouble.of(run.getActiveMillis())),
			run.getActualKills(),
			run.getTaskProgressUnits(),
			KphCalculator.formatRate(runKph),
			KphCalculator.formatXpRate(runXp),
			status,
			cannon,
			best));
		details.setToolTipText("Kill and task-unit counts can differ due to bracelets, multi-unit targets, or attribution evidence");
		details.setForeground(run.isExcludedFromAverages() ? Color.GRAY : Color.LIGHT_GRAY);
		row.add(details, BorderLayout.CENTER);

		JButton actions = new JButton("...");
		actions.setName("historyRunActions");
		actions.setToolTipText("Run actions");
		actions.setMargin(new Insets(1, 4, 1, 4));
		actions.setPreferredSize(new Dimension(28, 24));
		actions.setMinimumSize(new Dimension(28, 24));
		actions.setMaximumSize(new Dimension(28, 24));
		actions.addActionListener(event -> showRunActions(actions, run));
		row.add(centeredButtonWrapper(actions, ColorScheme.DARK_GRAY_COLOR), BorderLayout.EAST);
		return row;
	}

	private static JPanel createHistoryMetric(String labelText, String valueText, String tooltip)
	{
		JPanel metric = fullWidthBoxPanel(ColorScheme.DARK_GRAY_COLOR);
		metric.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		JLabel label = new JLabel(labelText);
		label.setForeground(Color.GRAY);
		label.setFont(label.getFont().deriveFont(Math.max(9f, label.getFont().getSize2D() - 1f)));
		label.setToolTipText(tooltip);
		label.setAlignmentX(LEFT_ALIGNMENT);
		metric.add(label);
		JLabel value = new JLabel(valueText);
		value.setForeground(Color.WHITE);
		value.setFont(value.getFont().deriveFont(Font.BOLD));
		value.setToolTipText(tooltip);
		value.setAlignmentX(LEFT_ALIGNMENT);
		metric.add(value);
		return metric;
	}

	private static JPanel centeredButtonWrapper(JButton button, Color background)
	{
		JPanel wrapper = fullWidthPanel(new GridBagLayout(), background);
		wrapper.add(button);
		return wrapper;
	}

	private static JPanel leftTextRow(JLabel label, Color background)
	{
		JPanel row = fullWidthPanel(new BorderLayout(), background);
		row.add(label, BorderLayout.WEST);
		return row;
	}

	private static String historyKey(TaskStatistics statistics)
	{
		return nullToEmpty(statistics.getTaskName()) + '\u001F'
			+ nullToEmpty(statistics.getTaskLocation()) + '\u001F'
			+ nullToEmpty(statistics.getEncounterProfileId()) + ':' + statistics.getTimingPolicy();
	}

	private static String nullToEmpty(String value)
	{
		return value == null ? "" : value;
	}

	private static String historyToggleText(int runCount, boolean expanded)
	{
		return expanded ? "Hide \u25BE" : "Runs (" + runCount + ") \u25B8";
	}

	private static void updateShowAllButton(JButton button, boolean showingAll, int hiddenCount)
	{
		button.setText(showingAll ? "Show fewer" : "Show " + hiddenCount + " older");
		button.setToolTipText(showingAll
			? "Return to the configured number of recent runs"
			: "Show every retained run for this task");
	}

	private static boolean shouldShowProfileName(
		String taskName,
		String profileId,
		String profileName)
	{
		if (profileId == null || profileId.isEmpty() || profileName == null || profileName.trim().isEmpty())
		{
			return false;
		}
		return !normalizeHistoryName(taskName).equals(normalizeHistoryName(profileName));
	}

	private static String normalizeHistoryName(String value)
	{
		String normalized = nullToEmpty(value).toLowerCase().replaceAll("[^a-z0-9]", "");
		return normalized.endsWith("s") && normalized.length() > 1
			? normalized.substring(0, normalized.length() - 1)
			: normalized;
	}

	private static boolean ratesDiffer(OptionalDouble literal, OptionalDouble effective)
	{
		if (literal.isPresent() != effective.isPresent())
		{
			return true;
		}
		return literal.isPresent() && Math.abs(literal.getAsDouble() - effective.getAsDouble()) >= 0.05;
	}

	private static String formatRunStatus(TaskRun run)
	{
		if (run.getStatus() == TaskRunStatus.COMPLETED)
		{
			return run.isFullTaskObserved() ? "Completed" : "Completed (partial observation)";
		}
		String text = run.getStatus().name().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private void showRunActions(JButton anchor, TaskRun run)
	{
		JPopupMenu menu = new JPopupMenu();
		if (run.getStatus() == TaskRunStatus.COMPLETED)
		{
			JMenuItem includeToggle = new JMenuItem(run.isExcludedFromAverages()
				? "Include in averages"
				: "Exclude from averages");
			includeToggle.addActionListener(event ->
				setRunExcluded.accept(run, !run.isExcludedFromAverages()));
			menu.add(includeToggle);
		}
		JMenuItem delete = new JMenuItem("Delete run...");
		delete.addActionListener(event -> confirmDeleteRun(run));
		menu.add(delete);
		menu.show(anchor, 0, anchor.getHeight());
	}

    private void addDataAction(JPopupMenu menu, String label, String action)
    {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> dataActions.accept(action));
        menu.add(item);
    }

    public void updateProgress(String context, int initial, int observedStart, int remaining)
    {
        if (!Objects.equals(previewContext, context))
        {
            if (previewDialog != null) { previewDialog.dispose(); previewDialog = null; }
            previewContext = context;
        }
        previewRemaining = remaining > 0 ? remaining : 100;
        progress.setVisible(initial > 0 && remaining >= 0 && remaining <= initial);
        progress.setMaximum(Math.max(1, initial));
        progress.setValue(Math.max(0, initial - remaining));
        progress.setString(observedStart != initial
            ? "Tracked since " + observedStart + " remaining"
            : Math.max(0, initial - remaining) + " / " + initial + " task units");
    }

    private void showPreview()
    {
        JOptionPane pane = new JOptionPane(new TaskPreviewPanel(storedStatistics, previewRemaining, config.estimateWindow()),
            JOptionPane.PLAIN_MESSAGE);
        previewDialog = pane.createDialog(this, "Compare personal task estimates");
        previewDialog.setModal(false);
        previewDialog.setVisible(true);
    }

    public void updateStorageStatus(String message, String originalPayload)
    {
        recoveryPayload = originalPayload;
        storageStatus.setText("<html><div style='width:210px'>" + message + "</div></html>");
        storageStatus.setVisible(!message.isEmpty());
    }

    private void showDataManagementMenu(JButton anchor)
	{
        JPopupMenu menu = new JPopupMenu();
        JMenuItem stored = new JMenuItem("View stored stats...");
        stored.setName("storedStatsDebugButton");
        stored.addActionListener(event -> showStoredStatsDebug());
        menu.add(stored);
        addDataAction(menu, "Export history...", "export");
        addDataAction(menu, "Export recovery backup...", "exportBackup");
        addDataAction(menu, "Import and replace history...", "replace");
        addDataAction(menu, "Merge complete retained history...", "merge");
        addDataAction(menu, "Restore last backup...", "backup");
        addDataAction(menu, "Recover checkpoint from file...", "recoverCheckpoint");
        if (undoAvailable) { addDataAction(menu, "Undo last deletion", "undo"); }
        if (recoveryPayload != null)
        {
            JMenuItem recover = new JMenuItem("View original data for recovery...");
            recover.addActionListener(event ->
            {
                JTextArea text = new JTextArea(recoveryPayload, 18, 60);
                text.setEditable(false);
                text.selectAll();
                JOptionPane.showMessageDialog(this, new JScrollPane(text),
                    "Original data — select and copy to preserve", JOptionPane.INFORMATION_MESSAGE);
            });
            menu.add(recover);
            addDataAction(menu, "Export original then start fresh...", "fresh");
            menu.show(anchor, 0, anchor.getHeight());
            return;
        }
        JMenuItem resetCurrent = new JMenuItem("Reset current task history...");
		resetCurrent.addActionListener(event -> confirmReset(
			"Reset the saved history for the current task?", resetCurrentHistory));
		menu.add(resetCurrent);
		JMenuItem resetAll = new JMenuItem("Reset all history...");
		resetAll.addActionListener(event -> confirmReset(
			"Reset all Slayer Task Speed history? A recovery backup will be kept.", resetAllHistory));
		menu.add(resetAll);
		menu.show(anchor, 0, anchor.getHeight());
	}

	private void showStoredStatsDebug()
	{
		JTextArea text = new JTextArea(StoredStatsDebugFormatter.format(storedStatistics));
		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setForeground(Color.WHITE);
		text.setCaretColor(Color.WHITE);
		text.setCaretPosition(0);

		JScrollPane scroll = new JScrollPane(text);
		scroll.setPreferredSize(new Dimension(720, 440));
		JButton copyAll = new JButton("Copy all");
		copyAll.addActionListener(event ->
		{
			text.selectAll();
			text.copy();
			text.setCaretPosition(0);
		});
		JPanel actions = new JPanel(new BorderLayout());
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel readOnlyNote = new JLabel("Read-only saved task records");
		readOnlyNote.setForeground(Color.LIGHT_GRAY);
		actions.add(readOnlyNote, BorderLayout.WEST);
		actions.add(copyAll, BorderLayout.EAST);
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(scroll, BorderLayout.CENTER);
		content.add(actions, BorderLayout.SOUTH);
		JOptionPane.showMessageDialog(
			this,
			content,
			"Stored task stats",
			JOptionPane.INFORMATION_MESSAGE);
	}

	private static JPanel fullWidthPanel(LayoutManager layout, Color background)
	{
		JPanel panel = new JPanel(layout)
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension preferred = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, preferred.height);
			}
		};
		panel.setBackground(background);
		panel.setAlignmentX(CENTER_ALIGNMENT);
		return panel;
	}

	private static JPanel fullWidthBoxPanel(Color background)
	{
		JPanel panel = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension preferred = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, preferred.height);
			}
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(background);
		panel.setAlignmentX(CENTER_ALIGNMENT);
		return panel;
	}

	private static JPanel metricCard()
	{
		JPanel panel = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension preferred = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, preferred.height);
			}
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		return panel;
	}

	private static JPanel sectionPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4))
		{
			@Override
			public Dimension getMaximumSize()
			{
				Dimension preferred = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, preferred.height);
			}
		};
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		return label;
	}

	private static JLabel addMetric(JPanel panel, String name, JLabel value, String tooltip)
	{
		JLabel label = new JLabel(name);
		label.setForeground(Color.LIGHT_GRAY);
		label.setToolTipText(tooltip);
		value.setToolTipText(tooltip);
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		row.add(label, BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		panel.add(row);
		return label;
	}

	private static void setMetricVisible(JLabel label, JLabel value, boolean visible)
	{
		label.getParent().setVisible(visible);
		label.setVisible(visible);
		value.setVisible(visible);
	}

	private static JLabel valueLabel()
	{
		JLabel label = new JLabel("--", SwingConstants.RIGHT);
		label.setForeground(Color.WHITE);
		return label;
	}

	private void addGap(int height)
	{
		add(Box.createRigidArea(new Dimension(0, height)));
	}

    private void confirmReset(String message, Runnable action)
    {
        String context = previewContext;
		int choice = JOptionPane.showConfirmDialog(
			this, message, "Confirm reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION && Objects.equals(context, previewContext))
		{
			action.run();
		}
	}

    private void confirmDeleteRun(TaskRun run)
    {
        String context = previewContext;
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Delete this recorded task run? Undo is available in Help & data for 30 seconds, until history changes.",
			"Delete task run",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION && Objects.equals(context, previewContext))
		{
			deleteRun.accept(run);
		}
	}
}
