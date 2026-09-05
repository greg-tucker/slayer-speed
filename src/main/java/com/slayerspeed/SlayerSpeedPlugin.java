package com.slayerspeed;

import com.google.inject.Provides;
import com.slayerspeed.calculation.TaskEstimate;
import com.slayerspeed.calculation.TaskEstimateService;
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
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ConfigChanged;
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
	description = "Estimate remaining Slayer task time from your own pace, with personal history, XP rates and cannonball estimates",
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
	private int lastActivityTick = Integer.MIN_VALUE;
    private long tickActivityTime;
    private long activityTimeMillis()
    {
        int tick = client.getTickCount();
        if (tick != lastActivityTick || tickActivityTime == 0)
        {
            lastActivityTick = tick;
            tickActivityTime = System.currentTimeMillis();
        }
        return tickActivityTime;
    }

    private volatile String loadedProfileKey;
    private final java.util.concurrent.atomic.AtomicBoolean uiQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Runnable pendingUi;
    private long cachedHistoryRevision = -1;
    private boolean cachedSeparateLocations;
    private Collection<TaskStatistics> cachedHistory = java.util.Collections.emptyList();
	private com.slayerspeed.ui.CompletionResult lastCompletionResult;
	private volatile SlayerSpeedViewModel viewModel = SlayerSpeedViewModel.noTask();

	@Override
	protected void startUp()
	{
		panel = new SlayerSpeedPanel(
			() -> dispatchPanelAction(this::resetCurrentTaskHistory),
			() -> dispatchPanelAction(this::resetAllHistory),
			(run, excluded) -> dispatchPanelAction(() -> setRunExcluded(run, excluded)),
			run -> dispatchPanelAction(() -> deleteRun(run)),
			profileId -> dispatchPanelAction(() -> selectEncounterProfile(profileId)),
			config);
        panel.setPauseAction(() -> dispatchPanelAction(() ->
        {
            taskTracker.toggleManualPause(activityTimeMillis());
            refreshView();
        }));
        panel.setDismissCompletionAction(() -> dispatchPanelAction(() ->
        {
            lastCompletionResult = null;
            refreshView();
        }));
        panel.setDataActions(new com.slayerspeed.ui.HistoryFileActions(panel, clientThread,
            historyRepository, config, this::refreshView, taskTracker::loadProfile));
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
		lastCompletionResult = null;
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

        taskTracker.setSegmentedTiming(config.experimentalSegmentedTiming());
        TaskUpdate update = taskTracker.observe(
			snapshot,
			activityTimeMillis(),
			config.idleTimeoutMinutes(),
			config.separateByLocation(),
			config.maximumRecentRuns());
		if (update.getEndedRun() != null
			&& update.getEndedRun().getStatus() == TaskRunStatus.COMPLETED
			&& config.showCompletionSummary())
		{
			lastCompletionResult = createCompletionResult(update.getEndedRun());
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
						bonusXp, activityTimeMillis(), config.idleTimeoutMinutes());
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
	public void onChatMessage(ChatMessage event)
	{
		if ((event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
			|| !isTaskCompletionMessage(Text.removeTags(event.getMessage())))
		{
			return;
		}

		int progress = taskTracker.confirmCompletion(
			activityTimeMillis(), config.idleTimeoutMinutes());
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
			case LOGIN_SCREEN:
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
		lastCompletionResult = null;
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
			activityTimeMillis(),
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
		lastCompletionResult = null;
		taskTracker.checkpoint();
		refreshView();
	}

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (SlayerSpeedConfig.GROUP.equals(event.getGroup()))
        {
            clientThread.invoke(this::refreshView);
        }
    }

    private void dispatchPanelAction(Runnable action)
    {
        SlayerSpeedPanel target = panel;
        String expected = target == null ? "" : target.getActionContext();
        clientThread.invoke(() ->
        {
            ActiveTask active = taskTracker.getActiveTask();
            String current = loadedProfileKey + ":" + (active == null ? "" : active.getId());
            if (target == panel && expected.equals(current)
                && java.util.Objects.equals(loadedProfileKey, historyRepository.getProfileIdentity()))
            {
                action.run();
            }
        });
    }

    private void refreshView()
    {
        viewModel = createViewModel();
        if (panel == null) { return; }
        SlayerSpeedPanel targetPanel = panel;
        SlayerSpeedViewModel currentView = viewModel;
        String profile = loadedProfileKey;
        String storageMessage = historyRepository.getStorageMessage();
        String recoveryPayload = historyRepository.getRecoveryPayload();
        boolean undoAvailable = historyRepository.canUndoDeletion();
        Collection<TaskStatistics> exactHistory = historyRepository.snapshotStatistics();
        if (cachedHistoryRevision != historyRepository.getHistoryRevision()
            || cachedSeparateLocations != config.separateByLocation())
        {
            cachedHistoryRevision = historyRepository.getHistoryRevision();
            cachedSeparateLocations = config.separateByLocation();
            if (cachedSeparateLocations) { cachedHistory = exactHistory; }
            else
            {
                Map<String, TaskStatistics> combined = new LinkedHashMap<>();
                for (TaskStatistics stats : exactHistory)
                {
                    TaskKey key = new TaskKey(stats.getTaskName(), null, stats.getEncounterProfileId(), stats.getTimingPolicy());
                    TaskStatistics group = combined.computeIfAbsent(key.asStorageKey(), ignored ->
                        new TaskStatistics(stats.getTaskName(), null, stats.getEncounterProfileId(), stats.getEncounterProfileName(), stats.getTimingPolicy()));
                    group.merge(stats);
                }
                cachedHistory = java.util.Collections.unmodifiableCollection(new ArrayList<>(combined.values()));
            }
        }
        Collection<TaskStatistics> history = cachedHistory;
        ActiveTask active = taskTracker.getActiveTask();
        com.slayerspeed.ui.CompletionResult completion = lastCompletionResult;
        String activeName = active == null ? "" : active.getTaskName();
        String context = profile + ":" + (active == null ? "" : active.getId());
        int initial = active == null ? 0 : active.getInitialAmount();
        int observedStart = active == null ? 0 : active.getObservedStartAmount();
        int remaining = active == null ? 0 : active.getLastRemainingAmount();
        boolean pauseEnabled = active != null && active.getTimingPolicy() != 0;
        boolean manuallyPaused = active != null && active.isManualPaused();
        String timingStatus = active == null ? "" : manuallyPaused ? "Paused — resumes on task activity"
            : active.isSuspended() ? "Suspended — waiting for task activity"
            : active.getLastActivityAtMillis() <= 0 ? "Waiting for activity"
            : "Tracking — capped activity gaps";
        pendingUi = () ->
        {
            if (panel == targetPanel && java.util.Objects.equals(profile, loadedProfileKey))
            {
                targetPanel.updateHistoryContext(activeName);
                targetPanel.update(currentView, history, exactHistory);
                targetPanel.updateCompletion(completion);
                targetPanel.updateStorageStatus(storageMessage, recoveryPayload);
                targetPanel.setUndoAvailable(undoAvailable);
                targetPanel.updateProgress(context, initial, observedStart, remaining);
                targetPanel.updateTiming(timingStatus, pauseEnabled, manuallyPaused);
            }
        };
        if (uiQueued.compareAndSet(false, true))
        {
            SwingUtilities.invokeLater(() ->
            {
                Runnable update = pendingUi;
                uiQueued.set(false);
                if (update != null) { update.run(); }
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
			note = "Recording override for the whole task";
		}
		else if (active.getDetectedEncounterProfileId() != null
			&& !active.getDetectedEncounterProfileId().isEmpty())
		{
			note = "Auto-detected from confirmed kills";
		}
		else
		{
			note = "Assumed until a confirmed kill";
		}
		return new EncounterSelection(selected, profiles, options, profiles.size() > 1, note);
	}

	private SlayerSpeedViewModel createViewModel()
	{
		ActiveTask active = taskTracker.getActiveTask();
		if (active == null)
		{
			return SlayerSpeedViewModel.noTask(lastCompletionResult == null ? null : lastCompletionResult.getHtml());
		}

		EncounterSelection encounter = createEncounterSelection(active);
		TaskKey key = new TaskKey(
			active.getTaskName(),
			config.separateByLocation() ? active.getTaskLocation() : null,
			encounter.selectedProfile.getId(), active.getTimingPolicy());
        com.slayerspeed.calculation.HistoryEstimateService historyEstimates =
            new com.slayerspeed.calculation.HistoryEstimateService();
        com.slayerspeed.calculation.HistoryEstimateService.Selection selection = historyEstimates.selectWithFallback(
            historyRepository.find(key, config.separateByLocation()),
            active.getTimingPolicy() == 0 ? null : historyRepository.find(new TaskKey(active.getTaskName(),
                config.separateByLocation() ? active.getTaskLocation() : null, encounter.selectedProfile.getId(), 0),
                config.separateByLocation()), config.estimateWindow());
        TaskStatistics historical = selection.statistics;
		long historicalMillis = historical == null ? 0L : historical.getTotalActiveMillis();
		int historicalUnits = historical == null ? 0 : historical.getTotalTaskProgressUnits();
		int historicalKills = historical == null ? 0 : historical.getTotalActualKills();
		int historicalXp = historical == null ? 0 : historical.getTotalSlayerXp();
		int completedTasks = historical == null ? 0 : historical.getCompletedTaskCount();
		int historicalCannonballs = historical == null ? 0 : historical.getTotalCannonballsUsed();
		int historicalCannonKills = historical == null ? 0 : historical.getTotalCannonRunActualKills();
		int historicalCannonUnits = historical == null ? 0 : historical.getTotalCannonRunTaskProgressUnits();

		OptionalDouble currentLiteral = KphCalculator.literalKph(active.getRateActualKills(), active.getActiveMillis());
		OptionalDouble currentEffective = KphCalculator.effectiveKph(active.getRateTaskProgressUnits(), active.getActiveMillis());
		OptionalDouble currentXp = KphCalculator.slayerXpPerHour(active.getRateSlayerXp(), active.getActiveMillis());
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

		boolean currentMatchesEstimate = !selection.legacyFallback && (active.getDetectedEncounterProfileId() == null
			|| active.getDetectedEncounterProfileId().isEmpty()
			|| active.getDetectedEncounterProfileId().equals(encounter.selectedProfile.getId()));
        TaskEstimate estimate = new TaskEstimateService().calculate(
            active.getLastRemainingAmount(), active.getRateTaskProgressUnits(), active.getActiveMillis(),
            historicalUnits, historicalMillis, config.currentRateMinimumUnits(), currentMatchesEstimate,
            completedTasks, selection.legacyFallback ? selection.getScope()
                : historyEstimates.scope(historical, config.estimateWindow(), active.getTimingPolicy()));
        OptionalDouble eta = estimate.getEtaMillis();
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

		String paceComparison = currentMatchesEstimate
			? formatPaceComparison(active.getTaskProgressUnits(), currentEffective, historicalEffective)
			: "";
		String finishTime = active.isManualPaused() || active.isSuspended() ? "" : formatFinishTime(eta);
		boolean historyAvailable = historicalUnits > 0 && historicalMillis > 0L;
        String sample = historyAvailable
            ? "<html><center>" + completedTasks + " fully observed tasks<br>"
                + (selection.window == com.slayerspeed.calculation.EstimateWindow.LIFETIME
                    ? "Total rate-sample count unavailable" : estimate.getScope()) + "</center></html>"
            : "<html><center>" + estimate.getDescription()
                + (config.showLearningProgress() ? "<br>Completed tasks build your history." : "")
                + "</center></html>";
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
			estimate.getDescription(),
			active.getCannonballsUsed() > 0 || historicalCannonballs > 0,
			Integer.toString(active.getCannonballsUsed()),
			cannonRateLabel,
			KphCalculator.formatRate(displayedBallsPerKill),
			KphCalculator.formatCannonballEstimate(estimatedTaskCannonballs),
			KphCalculator.formatCannonballEstimate(estimatedRemainingCannonballs),
            lastCompletionResult == null ? null : lastCompletionResult.getHtml(),
			encounter.selectorRelevant,
			encounter.selectedProfile.isKnown()
				? encounter.selectedProfile.getDisplayName()
				: "Waiting for first kill",
			encounter.selectionNote,
			active.hasManualEncounterProfile()
				? active.getManualEncounterProfileId()
				: EncounterProfileOption.AUTO_ID,
			encounter.options, estimate);
	}

    private com.slayerspeed.ui.CompletionResult createCompletionResult(TaskRun run)
    {
        TaskStatistics statistics = historyRepository.find(
            run.taskKey(config.separateByLocation()), config.separateByLocation());
        boolean first = run.isFullTaskObserved() && statistics != null && statistics.getCompletedTaskCount() == 1;
        boolean best = !first && statistics != null && statistics.isPersonalBest(run);
        return new com.slayerspeed.ui.CompletionResult(run, historyRepository.isPersistenceAvailable(), best, first);
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
			client.getVarpValue(VarPlayerID.DROPCANNON) == 4,
			cannonballsInInventory());
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
			cannonballsInInventory());
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
