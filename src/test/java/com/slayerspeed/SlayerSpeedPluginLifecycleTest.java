package com.slayerspeed;

import com.google.gson.Gson;
import com.slayerspeed.model.ActiveTask;
import com.slayerspeed.persistence.TaskHistoryRepository;
import com.slayerspeed.tracking.*;
import com.slayerspeed.ui.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real plugin handlers, tracker, repository and panel, with RuneLite services stubbed. */
public class SlayerSpeedPluginLifecycleTest
{
    private SlayerSpeedPlugin plugin;
    private Client client;
    private SlayerPluginService slayer;
    private TaskTracker tracker;
    private TaskHistoryRepository repository;
    private OverlayManager overlays;
    private ConfigManager configManager;
    private String profile = "account-a";
    private int tick;
    private boolean started;
    private final Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
    private boolean overlayEnabled = true;
    private boolean showInfoBoxes;
    private boolean infoBoxKills;
    private boolean infoBoxXp;
    private final java.util.List<InfoBox> registeredInfoBoxes = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Before public void setUp() throws Exception
    {
        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTickCount()).thenAnswer(call -> tick);
        when(client.getItemContainer(anyInt())).thenAnswer(call ->
        {
            assertFalse("Inventory must be read on the client thread, not the EDT",
                SwingUtilities.isEventDispatchThread());
            return null;
        });
        when(client.getVarpValue(anyInt())).thenAnswer(call ->
        {
            assertFalse("Varps must be read on the client thread, not the EDT",
                SwingUtilities.isEventDispatchThread());
            return 0;
        });
        slayer = mock(SlayerPluginService.class);
        when(slayer.getTask()).thenReturn("Gargoyles");
        when(slayer.getInitialAmount()).thenReturn(100);
        when(slayer.getRemainingAmount()).thenReturn(100);
        when(slayer.getTargets()).thenReturn(Collections.emptyList());

        ConfigManager manager = mock(ConfigManager.class);
        configManager = manager;
        Map<String, String> storage = new HashMap<>();
        when(manager.getRSProfileKey()).thenAnswer(call -> profile);
        when(manager.getRSProfileConfiguration(eq(SlayerSpeedConfig.GROUP), anyString()))
            .thenAnswer(call -> storage.get(profile + ":" + call.getArgument(1)));
        doAnswer(call ->
        {
            storage.put(profile + ":" + call.getArgument(1), String.valueOf((Object) call.getArgument(2)));
            return null;
        }).when(manager).setRSProfileConfiguration(eq(SlayerSpeedConfig.GROUP), anyString(), any());

        repository = spy(new TaskHistoryRepository(manager, new Gson()));
        tracker = new TaskTracker(repository, new ActiveTimeTracker());
        plugin = new SlayerSpeedPlugin();
        SlayerSpeedConfig config = new SlayerSpeedConfig()
        {
            @Override public boolean experimentalSegmentedTiming() { return true; }
            @Override public boolean overlayEnabled() { return overlayEnabled; }
            @Override public boolean showInfoBoxes() { return showInfoBoxes; }
            @Override public boolean infoBoxKillsPerHour() { return infoBoxKills; }
            @Override public boolean infoBoxSlayerXpPerHour() { return infoBoxXp; }
        };
        InfoBoxManager infoBoxManager = mock(InfoBoxManager.class);
        doAnswer(call -> { registeredInfoBoxes.add(call.getArgument(0)); return null; })
            .when(infoBoxManager).addInfoBox(any(InfoBox.class));
        doAnswer(call -> { registeredInfoBoxes.remove(call.getArgument(0)); return null; })
            .when(infoBoxManager).removeInfoBox(any(InfoBox.class));
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(call -> { clientTasks.add(call.getArgument(0)); return null; })
            .when(clientThread).invoke(any(Runnable.class));
        overlays = mock(OverlayManager.class);
        set(plugin, "client", client);
        set(plugin, "config", config);
        set(plugin, "configManager", manager);
        set(plugin, "slayerPluginService", slayer);
        set(plugin, "taskTracker", tracker);
        set(plugin, "historyRepository", repository);
        set(plugin, "slayerXpTracker", new SlayerXpTracker());
        set(plugin, "attributionService", new KillAttributionService());
        set(plugin, "cannonballTracker", new CannonballTracker());
        set(plugin, "encounterProfileResolver", new EncounterProfileResolver());
        set(plugin, "overlay", new SlayerSpeedOverlay(plugin, config));
        set(plugin, "mortimerChoiceOverlay", mock(MortimerChoiceOverlay.class));
        set(plugin, "overlayManager", overlays);
        set(plugin, "infoBoxes", new SlayerSpeedInfoBoxes(infoBoxManager, plugin, config));
        set(plugin, "clientToolbar", mock(ClientToolbar.class));
        set(plugin, "clientThread", clientThread);
        SwingUtilities.invokeAndWait(plugin::startUp);
        started = true;
        gameTick();
    }

    @After public void tearDown() throws Exception
    {
        if (started) { SwingUtilities.invokeAndWait(plugin::shutDown); }
        flushUi();
    }

    @Test public void logoutAndDisconnectHideImmediatelyWithoutAnotherGameTick() throws Exception
    {
        for (GameState state : new GameState[] {GameState.LOGIN_SCREEN, GameState.HOPPING,
            GameState.LOGGING_IN, GameState.CONNECTION_LOST})
        {
            assertTrue(plugin.isOverlayVisible());
            changeGameState(state);
            assertFalse("Overlay must hide immediately on " + state, plugin.isOverlayVisible());
            changeGameState(GameState.LOGGED_IN);
            gameTick();
            assertTrue(plugin.isOverlayVisible());
        }
    }

    @Test public void accountSwitchClearsThePreviousTaskAndInteractionVisibility() throws Exception
    {
        assertTrue(plugin.isOverlayVisible());
        profile = "account-b";
        plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("account-a", profile));
        flushUi();
        assertNull(tracker.getActiveTask());
        assertFalse(plugin.getViewModel().isActive());
        assertFalse(plugin.isOverlayVisible());
        assertEquals("account-b", repository.getProfileIdentity());

        // A restored old assignment must not inherit another account's recent interaction.
        tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 100),
            System.currentTimeMillis() - 180_000L, 5, true, 50);
        gameTick();
        assertFalse(plugin.isOverlayVisible());
    }

    @Test public void restartRestoresCheckpointAndRegistersOneSetOfOverlays() throws Exception
    {
        enableAllInfoBoxes();
        String taskId = tracker.getActiveTask().getId();
        SwingUtilities.invokeAndWait(plugin::shutDown);
        started = false;
        assertFalse(plugin.isOverlayVisible());
        assertFalse(plugin.getViewModel().isActive());
        assertTrue(registeredInfoBoxes.isEmpty());
        SwingUtilities.invokeAndWait(plugin::startUp);
        started = true;
        gameTick();
        assertEquals(taskId, tracker.getActiveTask().getId());
        assertTrue(plugin.isOverlayVisible());
        assertEquals(3, registeredInfoBoxes.size());
        verify(overlays, times(4)).add(any(net.runelite.client.ui.overlay.Overlay.class));
        verify(overlays, times(2)).remove(any(net.runelite.client.ui.overlay.Overlay.class));
    }

    @Test public void disablingThroughRuneLiteRemovesBothOverlaysBeforeClientCleanup() throws Exception
    {
        PluginManager manager = managedPlugin();
        enableAllInfoBoxes();
        when(configManager.getConfiguration("runelite", "slayerspeedplugin")).thenReturn("false");
        clearInvocations(overlays);
        SwingUtilities.invokeAndWait(() ->
        {
            try { assertTrue(manager.stopPlugin(plugin)); }
            catch (PluginInstantiationException ex) { throw new AssertionError(ex); }
        });
        started = false;
        assertFalse(manager.isPluginActive(plugin));
        assertFalse(manager.isPluginEnabled(plugin));
        assertFalse(plugin.isRunning());
        assertFalse(plugin.isOverlayVisible());
        assertFalse(plugin.getViewModel().isActive());
        assertTrue(registeredInfoBoxes.isEmpty());
        verify(overlays).remove((SlayerSpeedOverlay) get(plugin, "overlay"));
        verify(overlays).remove((MortimerChoiceOverlay) get(plugin, "mortimerChoiceOverlay"));
        assertFalse("Client cleanup is deferred, never read inventory on the EDT", clientTasks.isEmpty());
        flushUi();
        assertTrue(tracker.getActiveTask().isSuspended());
    }

    @Test public void switchingRuneLiteConfigurationProfilesStopsThePlugin() throws Exception
    {
        PluginManager manager = managedPlugin();
        enableAllInfoBoxes();
        // A RuneLite configuration profile switch is different from an OSRS account switch.
        when(configManager.getConfiguration("runelite", "slayerspeedplugin")).thenReturn("false");
        manager.onProfileChanged(mock(ProfileChanged.class));
        SwingUtilities.invokeAndWait(() -> {});
        started = false;
        assertFalse(manager.isPluginActive(plugin));
        assertFalse(manager.isPluginEnabled(plugin));
        assertFalse(plugin.isOverlayVisible());
        assertNull(get(plugin, "panel"));
        assertTrue(registeredInfoBoxes.isEmpty());
        flushUi();
    }

    @Test public void finalInventoryReadFailureCannotLeaveTheUiOrTrackingRunning() throws Exception
    {
        enableAllInfoBoxes();
        when(client.getItemContainer(anyInt())).thenThrow(new IllegalStateException("Inventory unavailable"));
        clearInvocations(overlays);
        SwingUtilities.invokeAndWait(plugin::shutDown);
        started = false;
        assertFalse(plugin.isOverlayVisible());
        assertTrue(registeredInfoBoxes.isEmpty());
        verify(overlays, times(2)).remove(any(net.runelite.client.ui.overlay.Overlay.class));
        flushUi();
        assertTrue(tracker.getActiveTask().isSuspended());
        assertNull(get(plugin, "loadedProfileKey"));
    }

    @Test public void queuedInitializationCannotReviveADisabledPlugin() throws Exception
    {
        SwingUtilities.invokeAndWait(plugin::shutDown);
        flushUi();
        clearInvocations(client);
        SwingUtilities.invokeAndWait(() ->
        {
            plugin.startUp();
            plugin.shutDown();
        });
        started = false;
        flushUi();
        verify(client, never()).getVarpValue(anyInt());
        assertFalse(plugin.isRunning());
        assertFalse(plugin.isOverlayVisible());
        assertFalse(plugin.getViewModel().isActive());
    }

    @Test public void rapidRestartSkipsOldInitializationAndKeepsTheCheckpoint() throws Exception
    {
        String taskId = tracker.getActiveTask().getId();
        SwingUtilities.invokeAndWait(plugin::shutDown);
        flushUi();
        clearInvocations(client);
        SwingUtilities.invokeAndWait(() ->
        {
            plugin.startUp();
            plugin.shutDown();
            plugin.startUp();
        });
        gameTick();
        assertTrue(plugin.isRunning());
        assertTrue(plugin.isOverlayVisible());
        assertEquals(taskId, tracker.getActiveTask().getId());
        verify(client, times(2)).getVarpValue(anyInt());
    }

    @Test public void idleOverlayHidesWhileTrackingRemainsActiveAndProgressRestoresIt() throws Exception
    {
        makeTaskIdle();
        gameTick();
        assertFalse(plugin.isOverlayVisible());
        assertTrue(plugin.getViewModel().isActive());
        when(slayer.getRemainingAmount()).thenReturn(99);
        gameTick();
        assertTrue(plugin.isOverlayVisible());
        assertEquals(1, tracker.getActiveTask().getTaskProgressUnits());
    }

    @Test public void fightingATaskTargetKeepsTheOverlayVisibleWithoutAKill() throws Exception
    {
        makeTaskIdle();
        NPC target = mock(NPC.class);
        Player player = mock(Player.class);
        when(player.getInteracting()).thenReturn(target);
        when(client.getLocalPlayer()).thenReturn(player);
        when(slayer.getTargets()).thenReturn(Collections.singletonList(target));
        gameTick();
        assertTrue(plugin.isOverlayVisible());
        assertEquals(0, tracker.getActiveTask().getTaskProgressUnits());
    }

    @Test public void openingSidebarRestoresAnIdleOverlayWithoutChangingTaskTiming() throws Exception
    {
        makeTaskIdle();
        gameTick();
        long activity = tracker.getActiveTask().getLastActivityAtMillis();
        assertFalse(plugin.isOverlayVisible());
        SlayerSpeedPanel panel = panel();
        SwingUtilities.invokeAndWait(panel::onActivate);
        flushUi();
        assertTrue(plugin.isOverlayVisible());
        assertEquals(activity, tracker.getActiveTask().getLastActivityAtMillis());
        assertEquals(0L, tracker.getActiveTask().getActiveMillis());
    }

    @Test public void completionMessageHidesOverlayBeforeTheFinalizationGraceEnds() throws Exception
    {
        ChatMessage completion = new ChatMessage();
        completion.setType(ChatMessageType.GAMEMESSAGE);
        completion.setMessage("You have completed your task!");
        plugin.onChatMessage(completion);
        assertTrue(tracker.isCompletionPending());
        assertFalse(plugin.isOverlayVisible());
    }

    @Test public void pauseActionRefreshesOnceAndUpdatesTheTask() throws Exception
    {
        clearInvocations(repository);
        AbstractButton pause = (AbstractButton) get(panel(), "pauseButton");
        SwingUtilities.invokeAndWait(pause::doClick);
        flushUi();
        assertTrue(tracker.getActiveTask().isManualPaused());
        verify(repository, times(1)).snapshotStatistics();
    }

    @Test public void infoBoxesFollowLiveSettingsAndDoNotDuplicateAcrossTicks() throws Exception
    {
        assertTrue(registeredInfoBoxes.isEmpty());
        overlayEnabled = false;
        showInfoBoxes = true;
        changeDisplayConfig();
        assertEquals(1, registeredInfoBoxes.size());
        assertTrue(registeredInfoBoxes.get(0).render());
        enableAllInfoBoxes();
        gameTick();
        gameTick();
        assertEquals(3, registeredInfoBoxes.size());
        assertEquals(3, registeredInfoBoxes.stream().map(InfoBox::getName).distinct().count());
        overlayEnabled = false;
        showInfoBoxes = false;
        changeDisplayConfig();
        assertTrue(registeredInfoBoxes.isEmpty());
        assertTrue(plugin.getViewModel().isActive());
    }

    @Test public void infoBoxesShareIdleLogoutAndCompletionVisibility() throws Exception
    {
        enableAllInfoBoxes();
        InfoBox time = registeredInfoBoxes.get(0);
        makeTaskIdle();
        gameTick();
        assertFalse(time.render());
        when(slayer.getRemainingAmount()).thenReturn(99);
        gameTick();
        assertTrue(time.render());
        changeGameState(GameState.LOGIN_SCREEN);
        assertFalse(time.render());
        changeGameState(GameState.LOGGED_IN);
        gameTick();
        assertFalse(time.render());
        when(slayer.getRemainingAmount()).thenReturn(98);
        gameTick();
        assertTrue(time.render());
        ChatMessage completion = new ChatMessage();
        completion.setType(ChatMessageType.GAMEMESSAGE);
        completion.setMessage("You have completed your task!");
        plugin.onChatMessage(completion);
        assertFalse(time.render());
    }

    private void enableAllInfoBoxes() throws Exception
    {
        overlayEnabled = true;
        showInfoBoxes = true;
        infoBoxKills = true;
        infoBoxXp = true;
        changeDisplayConfig();
        assertEquals(3, registeredInfoBoxes.size());
    }

    private void changeDisplayConfig() throws Exception
    {
        net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
        event.setGroup(SlayerSpeedConfig.GROUP);
        event.setKey("showInfoBoxes");
        plugin.onConfigChanged(event);
        flushUi();
    }

    private void makeTaskIdle() throws Exception
    {
        ActiveTask task = tracker.getActiveTask();
        long oldActivity = System.currentTimeMillis() - 180_000L;
        set(task, "startedAtMillis", oldActivity);
        task.setLastActivityAtMillis(oldActivity);
    }

    private void gameTick() throws Exception
    {
        drainClientTasks();
        tick++;
        plugin.onGameTick(new GameTick());
        flushUi();
    }

    private void changeGameState(GameState state)
    {
        when(client.getGameState()).thenReturn(state);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        plugin.onGameStateChanged(event);
    }

    private SlayerSpeedPanel panel() throws Exception { return (SlayerSpeedPanel) get(plugin, "panel"); }
    private PluginManager managedPlugin() throws Exception
    {
        SwingUtilities.invokeAndWait(plugin::shutDown);
        started = false;
        flushUi();
        // Use RuneLite's real enable/disable and configuration-profile machinery.
        java.lang.reflect.Constructor<?> constructor = PluginManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] types = constructor.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int index = 0; index < types.length; index++)
        {
            Class<?> type = types[index];
            arguments[index] = type == boolean.class ? false : type == ConfigManager.class ? configManager
                : type == EventBus.class ? mock(EventBus.class) : mock(type);
        }
        PluginManager manager = spy((PluginManager) constructor.newInstance(arguments));
        // Configuration-proxy discovery needs a live application injector; unrelated to stopping.
        doNothing().when(manager).loadDefaultPluginConfiguration(any());
        manager.add(plugin);
        SwingUtilities.invokeAndWait(() ->
        {
            try { assertTrue(manager.startPlugin(plugin)); }
            catch (PluginInstantiationException ex) { throw new AssertionError(ex); }
        });
        started = true;
        gameTick();
        assertTrue(manager.isPluginActive(plugin));
        return manager;
    }
    private void flushUi() throws Exception
    {
        drainClientTasks();
        SwingUtilities.invokeAndWait(() -> {});
    }
    private void drainClientTasks()
    {
        Runnable task;
        while ((task = clientTasks.poll()) != null) { task.run(); }
    }
    private static Object get(Object target, String name) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
