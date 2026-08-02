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
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
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
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class SlayerSpeedPanel extends PluginPanel
{
	private static final DateTimeFormatter RUN_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm");

	private final JLabel taskLabel = new JLabel("No active Slayer task", SwingConstants.CENTER);
	private final JLabel taskStatusLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel completionSummaryLabel = new JLabel("", SwingConstants.CENTER);
	private final JPanel encounterSection = new JPanel();
	private final JComboBox<EncounterProfileOption> encounterSelector = new JComboBox<>();
	private final JLabel encounterNoteLabel = new JLabel("", SwingConstants.CENTER);
	private boolean updatingEncounterSelector;

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

	private final JPanel historyPanel = new JPanel();
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
		add(taskLabel);
		taskStatusLabel.setForeground(Color.LIGHT_GRAY);
		taskStatusLabel.setAlignmentX(CENTER_ALIGNMENT);
		add(taskStatusLabel);
		completionSummaryLabel.setForeground(Color.LIGHT_GRAY);
		completionSummaryLabel.setAlignmentX(CENTER_ALIGNMENT);
		completionSummaryLabel.setVisible(false);
		add(completionSummaryLabel);

		encounterSection.setLayout(new BoxLayout(encounterSection, BoxLayout.Y_AXIS));
		encounterSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		encounterSection.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		JLabel encounterTitle = sectionTitle("Estimate for");
		encounterTitle.setAlignmentX(CENTER_ALIGNMENT);
		encounterSection.add(encounterTitle);
		encounterSection.add(Box.createRigidArea(new Dimension(0, 3)));
		encounterSelector.setAlignmentX(CENTER_ALIGNMENT);
		encounterSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, encounterSelector.getPreferredSize().height));
		encounterSelector.setToolTipText(
			"Auto follows confirmed kills; choose a monster to preview or force that estimate for this task");
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
			"Estimated local clock time when the task will finish");
		add(summaryCard);

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
		addMetric(cannonCard, "Est. balls remaining", cannonRemainingValue,
			"Estimated cannonballs needed for the remaining task units");
		cannonTotalLabel = addMetric(cannonCard, "Est. total balls", cannonTotalValue,
			"Estimated cannonballs for the full assignment size");
		cannonSection.add(cannonCard, BorderLayout.CENTER);
		cannonSection.setBorder(BorderFactory.createEmptyBorder(10, 0, 12, 0));
		add(cannonSection);

		JLabel historyTitle = sectionTitle("Task history");
		add(historyTitle);
		addGap(5);
		historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
		historyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(historyPanel, BorderLayout.CENTER);

		addGap(10);
		JButton debugStoredStats = new JButton("Debug stored stats...");
		debugStoredStats.setName("storedStatsDebugButton");
		debugStoredStats.setToolTipText("View and copy every exact stored task, location, profile, and run record");
		debugStoredStats.setAlignmentX(CENTER_ALIGNMENT);
		debugStoredStats.addActionListener(event -> showStoredStatsDebug());
		add(debugStoredStats);
		addGap(6);
		JButton dataManagement = new JButton("Data management...");
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
		taskStatusLabel.setVisible(model.isActive());
		updateEncounterSelector(model);
		summaryCard.setVisible(model.isActive());
		remainingValue.setText(model.getRemaining());
		etaValue.setText("--".equals(model.getEta()) ? "Learning" : model.getEta());
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
		currentTitle.setVisible(model.isActive() && hasCurrentRates);
		currentCard.setVisible(model.isActive() && hasCurrentRates);
		setMetricVisible(currentKillsLabel, currentKillsValue, !"--".equals(model.getCurrentLiteralKph()));
		setMetricVisible(currentUnitsLabel, currentUnitsValue,
			!"--".equals(model.getCurrentEffectiveKph())
				&& (detailed || model.isCurrentRatesDiffer() || "--".equals(model.getCurrentLiteralKph())));
		setMetricVisible(currentXpLabel, currentXpValue, !"--".equals(model.getCurrentSlayerXpPerHour()));
		xpNoteLabel.setVisible(model.isActive() && !"--".equals(model.getCurrentSlayerXpPerHour()));
		setMetricVisible(paceLabel, paceValue,
			config.showPaceComparison() && model.hasPaceComparison());

		averageUnitsValue.setText(model.getHistoricalEffectiveKph());
		averageDurationValue.setText(model.getHistoricalAverageDuration());
		averageKillsValue.setText(model.getHistoricalLiteralKph());
		averageXpValue.setText(model.getHistoricalSlayerXpPerHour());
		averageTitle.setVisible(model.isActive());
		averageCard.setVisible(model.isActive() && model.isHistoryAvailable());
		setMetricVisible(averageDurationLabel, averageDurationValue,
			model.isHistoryAvailable() && !"--".equals(model.getHistoricalAverageDuration()));
		setMetricVisible(averageKillsLabel, averageKillsValue, detailed && model.isHistoryAvailable());
		setMetricVisible(averageXpLabel, averageXpValue, detailed && model.isHistoryAvailable());
		historyMessageLabel.setText(model.getHistorySample());
		historyMessageLabel.setVisible(model.isActive() && (!model.isHistoryAvailable() || detailed));
		confidenceLabel.setText(model.isHistoryAvailable()
			? "Confidence: " + model.getConfidence()
			: "");
		confidenceLabel.setVisible(model.isActive() && model.isHistoryAvailable());

		boolean showCannon = config.showCannonMetrics() && model.isCannonRelevant();
		cannonSection.setVisible(showCannon);
		cannonUsedValue.setText(model.getCannonballsUsed());
		cannonRateLabel.setText(model.getCannonRateLabel());
		cannonRateValue.setText(model.getCannonballsPerKill());
		cannonRemainingValue.setText(model.getEstimatedRemainingCannonballs());
		cannonTotalValue.setText(model.getEstimatedTotalCannonballs());
		setMetricVisible(cannonRateLabel, cannonRateValue,
			showCannon && !"--".equals(model.getCannonballsPerKill()));
		setMetricVisible(cannonTotalLabel, cannonTotalValue,
			showCannon && detailed && !"--".equals(model.getEstimatedTotalCannonballs()));

		String completionSummary = model.getCompletionSummary();
		boolean showSummary = config.showCompletionSummary()
			&& completionSummary != null && !completionSummary.isEmpty();
		completionSummaryLabel.setText(showSummary ? completionSummary : "");
		completionSummaryLabel.setVisible(showSummary);

		long signature = Objects.hash(
			config.showRecentRuns(), config.recentRunsShown(), config.showCannonMetrics(), config.displayMode());
		for (TaskStatistics statistics : history)
		{
			signature = 31L * signature + Objects.hash(
				statistics.getTaskName(),
				statistics.getTaskLocation(),
				statistics.getLastUpdatedAtMillis(),
				statistics.getTotalTaskProgressUnits(),
				statistics.getTotalCannonballsUsed(),
				statistics.getCompletedTaskCount());
			for (TaskRun run : statistics.getRecentRuns())
			{
				signature = 31L * signature + Objects.hash(
					run.getId(), run.getCompletedAtMillis(), run.getStatus(), run.isExcludedFromAverages());
			}
		}
		if (signature != historySignature)
		{
			historySignature = signature;
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
		if (history.isEmpty())
		{
			JLabel empty = new JLabel("No completed tasks yet.");
			empty.setForeground(Color.GRAY);
			historyPanel.add(empty);
		}
		else
		{
			for (TaskStatistics statistics : history)
			{
				historyPanel.add(createHistoryRow(statistics));
				historyPanel.add(Box.createRigidArea(new Dimension(0, 5)));
			}
		}
		historyPanel.revalidate();
		historyPanel.repaint();
	}

	private JPanel createHistoryRow(TaskStatistics statistics)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));

		String location = statistics.getTaskLocation();
		String taskName = location == null || location.isEmpty()
			? statistics.getTaskName()
			: statistics.getTaskName() + " (" + location + ")";
		String profileName = statistics.getEncounterProfileName();
		JLabel name = new JLabel("<html>" + taskName + "<br>" + profileName + "</html>");
		name.setForeground(Color.WHITE);
		row.add(name);

		OptionalDouble effective = KphCalculator.effectiveKph(
			statistics.getTotalTaskProgressUnits(), statistics.getTotalActiveMillis());
		OptionalDouble literal = KphCalculator.literalKph(
			statistics.getTotalActualKills(), statistics.getTotalActiveMillis());
		OptionalDouble xp = KphCalculator.slayerXpPerHour(
			statistics.getTotalSlayerXp(), statistics.getTotalActiveMillis());
		OptionalDouble averageDuration = statistics.getCompletedTaskCount() > 0
			? OptionalDouble.of((double) statistics.getTotalCompletedTaskMillis() / statistics.getCompletedTaskCount())
			: OptionalDouble.empty();
		String detailLine = config.displayMode() == SlayerSpeedDisplayMode.DETAILED
			? String.format("<br>Kills/hr %s · XP/hr %s",
				KphCalculator.formatRate(literal), KphCalculator.formatXpRate(xp))
			: "";
		JLabel rates = new JLabel(String.format(
			"<html>Task units/hr %s · Avg time %s%s<br>%d full tasks · %d observed units</html>",
			KphCalculator.formatRate(effective),
			KphCalculator.formatDuration(averageDuration),
			detailLine,
			statistics.getCompletedTaskCount(),
			statistics.getTotalTaskProgressUnits()));
		rates.setForeground(Color.LIGHT_GRAY);
		row.add(rates);

		if (config.showCannonMetrics() && statistics.getTotalCannonballsUsed() > 0)
		{
			OptionalDouble cannonballsPerKill = KphCalculator.cannonballsPerKill(
				statistics.getTotalCannonballsUsed(), statistics.getTotalCannonRunActualKills());
			JLabel cannon = new JLabel("Cannon average " + KphCalculator.formatRate(cannonballsPerKill) + "/kill");
			cannon.setForeground(Color.LIGHT_GRAY);
			row.add(cannon);
		}

		if (config.showRecentRuns())
		{
			int shown = 0;
			for (TaskRun run : statistics.getRecentRuns())
			{
				if (shown++ >= config.recentRunsShown())
				{
					break;
				}
				row.add(createRecentRunRow(run, statistics.isPersonalBest(run)));
			}
		}
		return row;
	}

	private JPanel createRecentRunRow(TaskRun run, boolean personalBest)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		String status = run.isExcludedFromAverages()
			? "Excluded"
			: run.getStatus().name().replace('_', ' ');
		String best = personalBest ? " · PB" : "";
		String cannon = config.showCannonMetrics() && run.getCannonballsUsed() > 0
			? " · " + run.getCannonballsUsed() + " balls"
			: "";
		String assignment = run.getInitialAmount() > 0 ? " · " + run.getInitialAmount() + " assigned" : "";
		String date = RUN_DATE_FORMAT.format(
			Instant.ofEpochMilli(run.getCompletedAtMillis()).atZone(ZoneId.systemDefault()));
		JLabel details = new JLabel(String.format(
			"<html>%s%s<br>%s · %d kills / %d units<br>%s%s%s</html>",
			date,
			assignment,
			KphCalculator.formatDuration(OptionalDouble.of(run.getActiveMillis())),
			run.getActualKills(),
			run.getTaskProgressUnits(),
			status,
			cannon,
			best));
		details.setToolTipText("Kill and task-unit counts can differ due to bracelets, multi-unit targets, or attribution evidence");
		details.setForeground(run.isExcludedFromAverages() ? Color.GRAY : Color.LIGHT_GRAY);
		row.add(details, BorderLayout.CENTER);

		JButton actions = new JButton("...");
		actions.setToolTipText("Run actions");
		actions.setMargin(new Insets(2, 6, 2, 6));
		actions.addActionListener(event -> showRunActions(actions, run));
		row.add(actions, BorderLayout.EAST);
		return row;
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

	private void showDataManagementMenu(JButton anchor)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem resetCurrent = new JMenuItem("Reset current task history...");
		resetCurrent.addActionListener(event -> confirmReset(
			"Reset the saved history for the current task?", resetCurrentHistory));
		menu.add(resetCurrent);
		JMenuItem resetAll = new JMenuItem("Reset all history...");
		resetAll.addActionListener(event -> confirmReset(
			"Reset all Slayer Task Speed history? This cannot be undone.", resetAllHistory));
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
		JLabel readOnlyNote = new JLabel("Read-only exact stored records");
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
			"Stored stats debug",
			JOptionPane.INFORMATION_MESSAGE);
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
		int choice = JOptionPane.showConfirmDialog(
			this, message, "Confirm reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			action.run();
		}
	}

	private void confirmDeleteRun(TaskRun run)
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Delete this recorded task run? This cannot be undone.",
			"Delete task run",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			deleteRun.accept(run);
		}
	}
}
