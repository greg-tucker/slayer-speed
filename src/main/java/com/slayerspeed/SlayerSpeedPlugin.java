package com.slayerspeed;

import com.google.inject.Provides;
import com.slayerspeed.calculation.Confidence;
import com.slayerspeed.calculation.KphCalculator;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.model.EncounterProfile;
import com.slayerspeed.model.TaskKey;
import com.slayerspeed.model.TaskRun;
import com.slayerspeed.model.TaskRunStatus;
import com.slayerspeed.model.TaskStatistics;
import com.slayerspeed.persistence.TaskHistoryRepository;
import com.slayerspeed.tracking.AttributionResult;
import com.slayerspeed.tracking.CannonballTracker;
import com.slayerspeed.tracking.EncounterProfileResolver;
import com.slayerspeed.tracking.KillAttributionService;
import com.slayerspeed.tracking.SlayerXpTracker;
import com.slayerspeed.tracking.TaskSnapshot;
import com.slayerspeed.tracking.TaskTracker;
import com.slayerspeed.tracking.TaskUpdate;
import com.slayerspeed.ui.SlayerSpeedIcon;
import com.slayerspeed.ui.EncounterProfileOption;
import com.slayerspeed.ui.MortimerChoiceOverlay;
import com.slayerspeed.ui.SlayerSpeedOverlay;
import com.slayerspeed.ui.SlayerSpeedPanel;
import com.slayerspeed.ui.SlayerSpeedViewModel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Task Speed",
	description = "Tracks Slayer task kills per hour (KPH), XP rates, cannonball use and personal stats to estimate completion time",
	tags = {"slayer", "task", "assignment", "kph", "kills", "xp", "experience", "eta", "timer", "speed", "tracker", "stats", "history", "average", "calculator", "estimate", "estimator", "cannon", "cannonballs", "supplies", "boss", "araxxor", "araxyte", "araxytes", "mortimer"}
)
@PluginDependency(SlayerPlugin.class)
public class SlayerSpeedPlugin extends Plugin
{
	private static final DateTimeFormatter FINISH_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	@Inject
	private Client client;

	@Inject
	private SlayerSpeedConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SlayerPluginService slayerPluginService;

	@Inject
	private TaskTracker taskTracker;

	@Inject
	private TaskHistoryRepository historyRepository;

	@Inject
	private SlayerXpTracker slayerXpTracker;

	@Inject
	private KillAttributionService attributionService;

	@Inject
	private CannonballTracker cannonballTracker;

	@Inject
	private EncounterProfileResolver encounterProfileResolver;

	@Inject
	private SlayerSpeedOverlay overlay;

	@Inject
	private MortimerChoiceOverlay mortimerChoiceOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	private SlayerSpeedPanel panel;
	private NavigationButton navigationButton;
	private String loadedProfileKey;
	private String lastCompletionSummary;
	private volatile SlayerSpeedViewModel viewModel = SlayerSpeedViewModel.noTask();

	@Override
	protected void startUp()
	{
		panel = new SlayerSpeedPanel(
			() -> clientThread.invoke(this::resetCurrentTaskHistory),
			() -> clientThread.invoke(this::resetAllHistory),
			(run, excluded) -> clientThread.invoke(() -> setRunExcluded(run, excluded)),
			run -> clientThread.invoke(() -> deleteRun(run)),
			profileId -> clientThread.invoke(() -> selectEncounterProfile(profileId)),
			config);
		navigationButton = NavigationButton.builder()
			.tooltip("Slayer Task Speed")
			.icon(SlayerSpeedIcon.load())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(overlay);
		overlayManager.add(mortimerChoiceOverlay);
		ensureProfileLoaded();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			resetCannonballTracker();
		}
		refreshView();
		log.debug("SlayerSpeed started");
	}

	@Override
	protected void shutDown()
	{
		flushCannonballs();
		taskTracker.pause();
		attributionService.reset();
		slayerXpTracker.reset();
		cannonballTracker.clear();
		overlayManager.remove(overlay);
		overlayManager.remove(mortimerChoiceOverlay);
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		panel = null;
		navigationButton = null;
		loadedProfileKey = null;
		lastCompletionSummary = null;
		viewModel = SlayerSpeedViewModel.noTask();
		log.debug("SlayerSpeed stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		attributionService.advanceTick(tick);
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ensureProfileLoaded();
		if (!slayerXpTracker.hasBaseline())
		{
			slayerXpTracker.setBaseline(client.getSkillExperience(Skill.SLAYER));
		}
		String taskName = slayerPluginService.getTask();
		TaskSnapshot snapshot = taskName == null || taskName.trim().isEmpty()
			? TaskSnapshot.none()
			: TaskSnapshot.active(
				taskName,
				slayerPluginService.getTaskLocation(),
				slayerPluginService.getInitialAmount(),
				slayerPluginService.getRemainingAmount());
		flushCannonballs(snapshot);

		TaskUpdate update = taskTracker.observe(
			snapshot,
			System.currentTimeMillis(),
			config.idleTimeoutMinutes(),
			config.separateByLocation(),
			config.maximumRecentRuns());
		if (update.getEndedRun() != null
			&& update.getEndedRun().getStatus() == TaskRunStatus.COMPLETED
			&& config.showCompletionSummary())
		{
			lastCompletionSummary = createCompletionSummary(update.getEndedRun());
		}
		if (update.isTaskStarted() || update.getEndedRun() != null)
		{
			attributionService.reset();
		}
		if (update.getProgressDelta() > 0)
		{
			applyAttribution(attributionService.onTaskProgress(update.getProgressDelta(), tick));
		}
		refreshView();
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC) || taskTracker.getActiveTask() == null)
		{
			return;
		}

		NPC npc = (NPC) actor;
		if (!slayerPluginService.getTargets().contains(npc))
		{
			return;
		}

		Actor localPlayer = client.getLocalPlayer();
		boolean interacted = localPlayer != null
			&& (localPlayer.getInteracting() == npc || npc.getInteracting() == localPlayer);
		applyAttribution(attributionService.onCandidateDeath(
			npc,
			npc.getId(),
			npc.getName(),
			interacted,
			client.getTickCount()));
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.SLAYER)
		{
			return;
		}

		OptionalInt delta = slayerXpTracker.observe(event.getXp());
		if (delta.isPresent() && taskTracker.getActiveTask() != null)
		{
			int tick = client.getTickCount();
			AttributionResult result = attributionService.onSlayerXp(
				delta.getAsInt(), tick, !taskTracker.isCompletionPending());
			applyAttribution(result);
			if (result.getSlayerXp() == 0 && taskTracker.isCompletionPending())
			{
				int bonusXp = attributionService.consumePendingXpForCompletion(tick);
				if (bonusXp > 0)
				{
					taskTracker.applyBonusXp(
						bonusXp, System.currentTimeMillis(), config.idleTimeoutMinutes());
					refreshView();
				}
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.DROPCANNON)
		{
			cannonballTracker.setCannonPlaced(event.getValue() == 4);
			return;
		}
		if (event.getVarpId() != VarPlayerID.ROCKTHROWER)
		{
			return;
		}

		cannonballTracker.observeLoaded(
			event.getValue(),
			config.trackCannonballs() && taskTracker.getActiveTask() != null);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption());
		String target = Text.removeTags(event.getMenuTarget());
		if ("Empty".equalsIgnoreCase(option)
			&& target != null
			&& target.toLowerCase(Locale.ENGLISH).contains("dwarf multicannon"))
		{
			cannonballTracker.beginEmptying(cannonballsInInventory(), client.getTickCount());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if ((event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
			|| !isTaskCompletionMessage(Text.removeTags(event.getMessage())))
		{
			return;
		}

		int progress = taskTracker.confirmCompletion(
			System.currentTimeMillis(), config.idleTimeoutMinutes());
		if (progress > 0)
		{
			applyAttribution(attributionService.onTaskProgress(progress, client.getTickCount()));
		}
		refreshView();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (taskTracker.getActiveTask() == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		applyAttribution(attributionService.onLoot(npc, npc.getId(), client.getTickCount()));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case HOPPING:
			case LOGGING_IN:
			case CONNECTION_LOST:
				flushCannonballs();
				taskTracker.pause();
				attributionService.reset();
				cannonballTracker.clear();
				break;
			case LOGGED_IN:
				ensureProfileLoaded();
				slayerXpTracker.setBaseline(client.getSkillExperience(Skill.SLAYER));
				resetCannonballTracker();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		taskTracker.pause();
		loadedProfileKey = null;
		lastCompletionSummary = null;
		attributionService.reset();
		slayerXpTracker.reset();
		cannonballTracker.clear();
		ensureProfileLoaded();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			slayerXpTracker.setBaseline(client.getSkillExperience(Skill.SLAYER));
			resetCannonballTracker();
		}
		refreshView();
	}

	public SlayerSpeedViewModel getViewModel()
	{
		return viewModel;
	}

	private void applyAttribution(AttributionResult result)
	{
		if (result.getLiteralKills() == 0 && result.getSlayerXp() == 0)
		{
			return;
		}
		ActiveTask active = taskTracker.getActiveTask();
		if (active != null)
		{
			for (String npcName : result.getConfirmedNpcNames())
			{
				active.observeEncounter(encounterProfileResolver.resolve(active.getTaskName(), npcName));
			}
		}
		taskTracker.applyAttribution(
			result.getLiteralKills(),
			result.getSlayerXp(),
			System.currentTimeMillis(),
			config.idleTimeoutMinutes());
		refreshView();
	}

	private void selectEncounterProfile(String profileId)
	{
		ActiveTask active = taskTracker.getActiveTask();
		if (active == null)
		{
			return;
		}
		if (EncounterProfileOption.AUTO_ID.equals(profileId))
		{
			active.setManualEncounterProfile(null);
		}
		else
		{
			EncounterSelection selection = createEncounterSelection(active);
			active.setManualEncounterProfile(selection.profilesById.get(profileId));
		}
		taskTracker.checkpoint();
		refreshView();
	}

	private void ensureProfileLoaded()
	{
		String profileKey = configManager.getRSProfileKey();
		if (profileKey != null && !profileKey.equals(loadedProfileKey))
		{
			taskTracker.loadProfile();
			loadedProfileKey = profileKey;
		}
	}

	static boolean isTaskCompletionMessage(String message)
	{
		return message != null
			&& message.trim().toLowerCase(Locale.ENGLISH).startsWith("you have completed your task");
	}

	private void resetCurrentTaskHistory()
	{
		ActiveTask active = taskTracker.getActiveTask();
		if (active != null)
		{
			historyRepository.deleteTask(
				active.taskKey(config.separateByLocation()), config.separateByLocation());
			refreshView();
		}
	}

	private void setRunExcluded(TaskRun run, boolean excluded)
	{
		historyRepository.setRunExcluded(run, excluded);
		refreshView();
	}

	private void deleteRun(TaskRun run)
	{
		historyRepository.deleteRun(run);
		refreshView();
	}

	private void resetAllHistory()
	{
		historyRepository.deleteAll();
		lastCompletionSummary = null;
		taskTracker.checkpoint();
		refreshView();
	}

	private void refreshView()
	{
		viewModel = createViewModel();
		if (panel != null)
		{
			SlayerSpeedViewModel currentView = viewModel;
			Collection<TaskStatistics> history = new ArrayList<>(
				historyRepository.allStatistics(config.separateByLocation()));
			Collection<TaskStatistics> exactStoredHistory = new ArrayList<>(
				historyRepository.allStatistics(true));
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.update(currentView, history, exactStoredHistory);
				}
			});
		}
	}

	private EncounterSelection createEncounterSelection(ActiveTask active)
	{
		TaskKey assignment = active.taskKey(config.separateByLocation());
		Collection<TaskStatistics> histories = historyRepository.profilesForTask(
			assignment, config.separateByLocation());
		LinkedHashMap<String, EncounterProfile> profiles = new LinkedHashMap<>();
		for (EncounterProfile suggestion : encounterProfileResolver.suggestionsForTask(active.getTaskName()))
		{
			profiles.put(suggestion.getId(), suggestion);
		}
		for (TaskStatistics statistics : histories)
		{
			EncounterProfile profile = new EncounterProfile(
				statistics.getEncounterProfileId(), statistics.getEncounterProfileName());
			if (profile.isKnown())
			{
				profiles.putIfAbsent(profile.getId(), profile);
			}
		}
		if (active.getDetectedEncounterProfileId() != null
			&& !active.getDetectedEncounterProfileId().isEmpty())
		{
			profiles.putIfAbsent(
				active.getDetectedEncounterProfileId(),
				new EncounterProfile(
					active.getDetectedEncounterProfileId(), active.getDetectedEncounterProfileName()));
		}
		if (active.hasManualEncounterProfile())
		{
			profiles.putIfAbsent(
				active.getManualEncounterProfileId(),
				new EncounterProfile(
					active.getManualEncounterProfileId(), active.getManualEncounterProfileName()));
		}

		EncounterProfile selected = null;
		if (active.hasManualEncounterProfile())
		{
			selected = profiles.get(active.getManualEncounterProfileId());
		}
		else if (active.getDetectedEncounterProfileId() != null)
		{
			selected = profiles.get(active.getDetectedEncounterProfileId());
		}
		if (selected == null)
		{
			for (TaskStatistics statistics : histories)
			{
				if (statistics.getEncounterProfileId() != null
					&& !statistics.getEncounterProfileId().isEmpty())
				{
					selected = profiles.get(statistics.getEncounterProfileId());
					break;
				}
			}
		}
		if (selected == null && !profiles.isEmpty())
		{
			selected = profiles.values().iterator().next();
		}
		if (selected == null)
		{
			selected = new EncounterProfile(
				EncounterProfile.UNKNOWN_ID, EncounterProfile.UNKNOWN_DISPLAY_NAME);
		}

		List<EncounterProfileOption> options = new ArrayList<>();
		options.add(new EncounterProfileOption(
			EncounterProfileOption.AUTO_ID,
			selected.isKnown() ? "Auto: " + selected.getDisplayName() : "Auto: waiting for a kill"));
		for (EncounterProfile profile : profiles.values())
		{
			options.add(new EncounterProfileOption(profile.getId(), profile.getDisplayName()));
		}
		String note;
		if (active.hasManualEncounterProfile())
		{
			note = "Manual estimate for this task";
		}
		else if (active.getDetectedEncounterProfileId() != null
			&& !active.getDetectedEncounterProfileId().isEmpty())
		{
			note = "Auto-detected from confirmed kills";
		}
		else
		{
			note = "Auto switches after a confirmed kill";
		}
		return new EncounterSelection(selected, profiles, options, profiles.size() > 1, note);
	}

	private SlayerSpeedViewModel createViewModel()
	{
		ActiveTask active = taskTracker.getActiveTask();
		if (active == null)
		{
			return SlayerSpeedViewModel.noTask(lastCompletionSummary);
		}

		EncounterSelection encounter = createEncounterSelection(active);
		TaskKey key = new TaskKey(
			active.getTaskName(),
			config.separateByLocation() ? active.getTaskLocation() : null,
			encounter.selectedProfile.getId());
		TaskStatistics historical = historyRepository.find(key, config.separateByLocation());
		long historicalMillis = historical == null ? 0L : historical.getTotalActiveMillis();
		int historicalUnits = historical == null ? 0 : historical.getTotalTaskProgressUnits();
		int historicalKills = historical == null ? 0 : historical.getTotalActualKills();
		int historicalXp = historical == null ? 0 : historical.getTotalSlayerXp();
		int completedTasks = historical == null ? 0 : historical.getCompletedTaskCount();
		int historicalCannonballs = historical == null ? 0 : historical.getTotalCannonballsUsed();
		int historicalCannonKills = historical == null ? 0 : historical.getTotalCannonRunActualKills();
		int historicalCannonUnits = historical == null ? 0 : historical.getTotalCannonRunTaskProgressUnits();

		OptionalDouble currentLiteral = KphCalculator.literalKph(active.getActualKills(), active.getActiveMillis());
		OptionalDouble currentEffective = KphCalculator.effectiveKph(active.getTaskProgressUnits(), active.getActiveMillis());
		OptionalDouble currentXp = KphCalculator.slayerXpPerHour(active.getTotalSlayerXp(), active.getActiveMillis());
		OptionalDouble historicalLiteral = KphCalculator.literalKph(historicalKills, historicalMillis);
		OptionalDouble historicalEffective = KphCalculator.effectiveKph(historicalUnits, historicalMillis);
		OptionalDouble historicalXpRate = KphCalculator.slayerXpPerHour(historicalXp, historicalMillis);
		OptionalDouble historicalAverageDuration = historical != null && completedTasks > 0
			? OptionalDouble.of((double) historical.getTotalCompletedTaskMillis() / completedTasks)
			: OptionalDouble.empty();
		OptionalDouble historicalBallsPerKill = KphCalculator.cannonballsPerKill(
			historicalCannonballs, historicalCannonKills);
		OptionalDouble historicalBallsPerUnit = KphCalculator.cannonballsPerTaskUnit(
			historicalCannonballs, historicalCannonUnits);
		OptionalDouble currentBallsPerKill = KphCalculator.cannonballsPerKill(
			active.getCannonballsUsed(), active.getActualKills());
		OptionalDouble currentBallsPerUnit = KphCalculator.cannonballsPerTaskUnit(
			active.getCannonballsUsed(), active.getTaskProgressUnits());

		boolean currentMatchesEstimate = active.getDetectedEncounterProfileId() == null
			|| active.getDetectedEncounterProfileId().isEmpty()
			|| active.getDetectedEncounterProfileId().equals(encounter.selectedProfile.getId());
		OptionalDouble estimatedEffective;
		if (currentMatchesEstimate
			&& active.getTaskProgressUnits() >= config.currentRateMinimumUnits())
		{
			estimatedEffective = KphCalculator.effectiveKph(
				historicalUnits + active.getTaskProgressUnits(),
				historicalMillis + active.getActiveMillis());
		}
		else
		{
			estimatedEffective = historicalEffective;
			if (!estimatedEffective.isPresent() && currentMatchesEstimate)
			{
				estimatedEffective = currentEffective;
			}
		}

		OptionalDouble eta = estimatedEffective.isPresent()
			? KphCalculator.etaMillis(active.getLastRemainingAmount(), estimatedEffective.getAsDouble())
			: OptionalDouble.empty();
		OptionalDouble displayedBallsPerKill = historicalBallsPerKill;
		String cannonRateLabel = "Average / kill";
		OptionalDouble estimateBallsPerUnit = historicalBallsPerUnit;
		if (currentMatchesEstimate
			&& active.getTaskProgressUnits() >= config.currentRateMinimumUnits()
			&& active.getCannonballsUsed() > 0)
		{
			displayedBallsPerKill = KphCalculator.cannonballsPerKill(
				historicalCannonballs + active.getCannonballsUsed(),
				historicalCannonKills + active.getActualKills());
			estimateBallsPerUnit = KphCalculator.cannonballsPerTaskUnit(
				historicalCannonballs + active.getCannonballsUsed(),
				historicalCannonUnits + active.getTaskProgressUnits());
			cannonRateLabel = historicalBallsPerKill.isPresent() ? "Blended / kill" : "This task / kill";
		}
		if (!displayedBallsPerKill.isPresent() && currentMatchesEstimate)
		{
			displayedBallsPerKill = currentBallsPerKill;
			cannonRateLabel = "This task / kill";
		}
		if (!estimateBallsPerUnit.isPresent() && currentMatchesEstimate)
		{
			estimateBallsPerUnit = currentBallsPerUnit;
		}
		int taskSize = active.getInitialAmount() > 0
			? active.getInitialAmount()
			: active.getObservedStartAmount();
		OptionalDouble estimatedTaskCannonballs = estimateBallsPerUnit.isPresent()
			? KphCalculator.estimatedCannonballs(taskSize, estimateBallsPerUnit.getAsDouble())
			: OptionalDouble.empty();
		OptionalDouble estimatedRemainingCannonballs = estimateBallsPerUnit.isPresent()
			? KphCalculator.estimatedCannonballs(
				active.getLastRemainingAmount(), estimateBallsPerUnit.getAsDouble())
			: OptionalDouble.empty();
		Confidence confidence = Confidence.fromSample(completedTasks, historicalUnits);
		String paceComparison = currentMatchesEstimate
			? formatPaceComparison(active.getTaskProgressUnits(), currentEffective, historicalEffective)
			: "";
		String finishTime = formatFinishTime(eta);
		boolean historyAvailable = historicalUnits > 0 && historicalMillis > 0L;
		String sample;
		if (historyAvailable)
		{
			sample = "<html><center>" + completedTasks + " full tasks<br>"
				+ historicalUnits + " observed units</center></html>";
		}
		else if (config.showLearningProgress()
			&& active.getTaskProgressUnits() < config.currentRateMinimumUnits())
		{
			sample = "<html><center>Learning this task<br>" + active.getTaskProgressUnits() + " / "
				+ config.currentRateMinimumUnits() + " task units</center></html>";
		}
		else if (config.showLearningProgress())
		{
			sample = "<html><center>First task<br>Average ready<br>after completion</center></html>";
		}
		else
		{
			sample = "<html><center>No completed history<br>Current task excluded<br>until completion</center></html>";
		}
		String taskDisplay = active.getTaskLocation() == null || active.getTaskLocation().isEmpty()
			? active.getTaskName()
			: active.getTaskName() + " (" + active.getTaskLocation() + ")";

		return new SlayerSpeedViewModel(
			true,
			taskDisplay,
			Integer.toString(active.getLastRemainingAmount()),
			active.getActualKills() + " kills · " + active.getTaskProgressUnits() + " task units",
			KphCalculator.formatRate(currentLiteral),
			KphCalculator.formatRate(currentEffective),
			currentLiteral.isPresent() && currentEffective.isPresent()
				&& Math.abs(currentLiteral.getAsDouble() - currentEffective.getAsDouble()) >= 0.5,
			KphCalculator.formatXpRate(currentXp),
			KphCalculator.formatDuration(eta),
			paceComparison,
			finishTime,
			historyAvailable,
			KphCalculator.formatRate(historicalLiteral),
			KphCalculator.formatRate(historicalEffective),
			KphCalculator.formatXpRate(historicalXpRate),
			KphCalculator.formatDuration(historicalAverageDuration),
			sample,
			confidence.getDisplayName(),
			active.getCannonballsUsed() > 0 || historicalCannonballs > 0,
			Integer.toString(active.getCannonballsUsed()),
			cannonRateLabel,
			KphCalculator.formatRate(displayedBallsPerKill),
			KphCalculator.formatCannonballEstimate(estimatedTaskCannonballs),
			KphCalculator.formatCannonballEstimate(estimatedRemainingCannonballs),
			null,
			encounter.selectorRelevant,
			encounter.selectedProfile.isKnown()
				? encounter.selectedProfile.getDisplayName()
				: "Waiting for first kill",
			encounter.selectionNote,
			active.hasManualEncounterProfile()
				? active.getManualEncounterProfileId()
				: EncounterProfileOption.AUTO_ID,
			encounter.options);
	}

	private String createCompletionSummary(TaskRun run)
	{
		TaskStatistics statistics = historyRepository.find(
			run.taskKey(config.separateByLocation()), config.separateByLocation());
		boolean personalBest = statistics != null && statistics.isPersonalBest(run);
		String taskName = run.getTaskLocation() == null || run.getTaskLocation().isEmpty()
			? run.getTaskName()
			: run.getTaskName() + " (" + run.getTaskLocation() + ")";
		String encounterSummary = run.getEncounterProfileId().isEmpty()
			? ""
			: "<br>Estimate profile: " + run.getEncounterProfileName();
		String cannonSummary = config.showCannonMetrics() && run.getCannonballsUsed() > 0
			? "<br>Cannonballs: " + run.getCannonballsUsed()
			: "";
		return String.format(
			"<html><b>%s complete%s</b>%s<br>%s · %s task units/hr · %s XP/hr%s</html>",
			taskName,
			personalBest ? " — New PB!" : "",
			encounterSummary,
			KphCalculator.formatDuration(OptionalDouble.of(run.getActiveMillis())),
			KphCalculator.formatRate(KphCalculator.effectiveKph(
				run.getTaskProgressUnits(), run.getActiveMillis())),
			KphCalculator.formatXpRate(KphCalculator.slayerXpPerHour(
				run.getTotalSlayerXp(), run.getActiveMillis())),
			cannonSummary);
	}

	private String formatPaceComparison(
		int currentProgressUnits,
		OptionalDouble currentEffective,
		OptionalDouble historicalEffective)
	{
		if (!config.showPaceComparison()
			|| currentProgressUnits < config.currentRateMinimumUnits()
			|| !currentEffective.isPresent()
			|| !historicalEffective.isPresent()
			|| historicalEffective.getAsDouble() <= 0.0)
		{
			return "";
		}
		double percent = (currentEffective.getAsDouble() / historicalEffective.getAsDouble() - 1.0) * 100.0;
		if (Math.abs(percent) < 0.5)
		{
			return "On pace";
		}
		return String.format("%.0f%% %s", Math.abs(percent), percent > 0 ? "faster" : "slower");
	}

	private String formatFinishTime(OptionalDouble eta)
	{
		if (!config.showEstimatedFinishTime() || !eta.isPresent())
		{
			return "";
		}
		long finishAtMillis = System.currentTimeMillis() + Math.max(0L, Math.round(eta.getAsDouble()));
		return FINISH_TIME_FORMAT.format(
			Instant.ofEpochMilli(finishAtMillis).atZone(ZoneId.systemDefault()));
	}

	private void resetCannonballTracker()
	{
		cannonballTracker.reset(
			client.getVarpValue(VarPlayerID.ROCKTHROWER),
			client.getVarpValue(VarPlayerID.DROPCANNON) == 4);
	}

	private void flushCannonballs()
	{
		flushCannonballs(null);
	}

	private void flushCannonballs(TaskSnapshot currentSnapshot)
	{
		ActiveTask active = taskTracker.getActiveTask();
		boolean assignmentMatches = active != null
			&& (currentSnapshot == null
				|| !currentSnapshot.hasTask()
				|| new TaskKey(active.getTaskName(), active.getTaskLocation()).equals(
					new TaskKey(currentSnapshot.getTaskName(), currentSnapshot.getTaskLocation())));
		int consumed = cannonballTracker.drainConsumed(
			config.trackCannonballs() && assignmentMatches,
			cannonballsInInventory(),
			client.getTickCount());
		if (consumed > 0)
		{
			taskTracker.applyCannonballsUsed(consumed, System.currentTimeMillis());
		}
	}

	private int cannonballsInInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		return inventory.count(ItemID.MCANNONBALL) + inventory.count(ItemID.GRANITE_CANNONBALL);
	}

	private static final class EncounterSelection
	{
		private final EncounterProfile selectedProfile;
		private final Map<String, EncounterProfile> profilesById;
		private final List<EncounterProfileOption> options;
		private final boolean selectorRelevant;
		private final String selectionNote;

		private EncounterSelection(
			EncounterProfile selectedProfile,
			Map<String, EncounterProfile> profilesById,
			List<EncounterProfileOption> options,
			boolean selectorRelevant,
			String selectionNote)
		{
			this.selectedProfile = selectedProfile;
			this.profilesById = profilesById;
			this.options = options;
			this.selectorRelevant = selectorRelevant;
			this.selectionNote = selectionNote;
		}
	}

	@Provides
	SlayerSpeedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerSpeedConfig.class);
	}
}
