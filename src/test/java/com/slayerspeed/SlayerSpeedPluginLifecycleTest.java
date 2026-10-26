package com.slayerspeed;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.slayerspeed.persistence.TaskHistoryRepository;
import com.slayerspeed.tracking.*;
import com.slayerspeed.ui.*;
import java.util.*;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Tests our lifecycle callbacks with injected services, not RuneLite's private machinery. */
public class SlayerSpeedPluginLifecycleTest
{
    private final SlayerSpeedPlugin plugin = new SlayerSpeedPlugin();
    private final Client client = mock(Client.class);
    private final SlayerPluginService slayer = mock(SlayerPluginService.class);
    private final ConfigManager configManager = mock(ConfigManager.class);
    private final OverlayManager overlays = mock(OverlayManager.class);
    private final ClientToolbar toolbar = mock(ClientToolbar.class);
    private final ClientThread clientThread = mock(ClientThread.class);
    private final InfoBoxManager infoBoxManager = mock(InfoBoxManager.class);
    private final List<InfoBox> boxes = new ArrayList<>();
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final TaskHistoryRepository repository = new TaskHistoryRepository(configManager, new Gson());
    private final TaskTracker tracker = new TaskTracker(repository, new ActiveTimeTracker());
    private String profile = "account-a";
    private int tick;
    private boolean showInfoBoxes = true;
    private NavigationButton navigation;

    @Before public void setUp() throws Exception
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTickCount()).thenAnswer(call -> tick);
        when(client.getItemContainer(anyInt())).thenAnswer(call ->
        {
            assertFalse("Inventory reads must not run on the EDT", SwingUtilities.isEventDispatchThread());
            return null;
        });
        when(client.getVarpValue(anyInt())).thenAnswer(call ->
        {
            assertFalse("Varp reads must not run on the EDT", SwingUtilities.isEventDispatchThread());
            return 0;
        });
        when(slayer.getTask()).thenReturn("Gargoyles");
        when(slayer.getInitialAmount()).thenReturn(100);
        when(slayer.getRemainingAmount()).thenReturn(100);
        when(slayer.getTargets()).thenReturn(Collections.emptyList());
        Map<String, String> storage = new HashMap<>();
        when(configManager.getRSProfileKey()).thenAnswer(call -> profile);
        when(configManager.getRSProfileConfiguration(eq(SlayerSpeedConfig.GROUP), anyString()))
            .thenAnswer(call -> storage.get(profile + ":" + call.getArgument(1)));
        doAnswer(call ->
        {
            storage.put(profile + ":" + call.getArgument(1), String.valueOf((Object) call.getArgument(2)));
            return null;
        }).when(configManager).setRSProfileConfiguration(eq(SlayerSpeedConfig.GROUP), anyString(), any());
        doAnswer(call -> { clientTasks.add(call.getArgument(0)); return null; })
            .when(clientThread).invoke(any(Runnable.class));
        doAnswer(call -> { navigation = call.getArgument(0); return null; })
            .when(toolbar).addNavigation(any(NavigationButton.class));
        doAnswer(call -> { boxes.add(call.getArgument(0)); return null; })
            .when(infoBoxManager).addInfoBox(any(InfoBox.class));
        doAnswer(call -> { boxes.remove(call.getArgument(0)); return null; })
            .when(infoBoxManager).removeInfoBox(any(InfoBox.class));
        SlayerSpeedConfig config = new SlayerSpeedConfig()
        {
            @Override public boolean showInfoBoxes() { return showInfoBoxes; }
            @Override public boolean infoBoxKillsPerHour() { return true; }
            @Override public boolean infoBoxSlayerXpPerHour() { return true; }
        };
        Guice.createInjector(new AbstractModule()
        {
            @Override protected void configure()
            {
                bind(SlayerSpeedPlugin.class).toInstance(plugin);
                bind(Client.class).toInstance(client);
                bind(SlayerSpeedConfig.class).toInstance(config);
                bind(ConfigManager.class).toInstance(configManager);
                bind(SlayerPluginService.class).toInstance(slayer);
                bind(TaskTracker.class).toInstance(tracker);
                bind(TaskHistoryRepository.class).toInstance(repository);
                bind(OverlayManager.class).toInstance(overlays);
                bind(ClientToolbar.class).toInstance(toolbar);
                bind(ClientThread.class).toInstance(clientThread);
                bind(InfoBoxManager.class).toInstance(infoBoxManager);
                bind(MortimerChoiceOverlay.class).toInstance(mock(MortimerChoiceOverlay.class));
            }
        });
        SwingUtilities.invokeAndWait(plugin::startUp);
        flush();
        // Seed an old assignment through the tracker's public time-aware API.
        tracker.observe(TaskSnapshot.active("Gargoyles", null, 100, 100),
            System.currentTimeMillis() - 180_000L, 5, true, 50);
        gameTick();
    }

    @After public void tearDown() throws Exception
    {
        if (plugin.isRunning()) { SwingUtilities.invokeAndWait(plugin::shutDown); }
        flush();
    }

    @Test public void shutdownRemovesDisplaysBeforeCleanupEvenIfInventoryIsUnavailable() throws Exception
    {
        progress();
        assertTrue(plugin.isOverlayVisible());
        when(client.getItemContainer(anyInt())).thenThrow(new IllegalStateException("Inventory unavailable"));
        SwingUtilities.invokeAndWait(plugin::shutDown);
        assertFalse(plugin.isRunning());
        assertFalse(plugin.isOverlayVisible());
        assertFalse(plugin.getViewModel().isActive());
        assertTrue(boxes.isEmpty());
        verify(overlays, times(2)).remove(any(net.runelite.client.ui.overlay.Overlay.class));
        verify(toolbar).removeNavigation(navigation);
        assertFalse(clientTasks.isEmpty());
        flush();
        assertTrue(tracker.getActiveTask().isSuspended());
    }

    @Test public void logoutAndAccountSwitchCannotDisplayThePreviousTask() throws Exception
    {
        progress();
        for (GameState state : new GameState[] {GameState.LOGIN_SCREEN, GameState.HOPPING, GameState.CONNECTION_LOST})
        {
            GameStateChanged event = new GameStateChanged();
            event.setGameState(state);
            when(client.getGameState()).thenReturn(state);
            plugin.onGameStateChanged(event);
            assertFalse(plugin.isOverlayVisible());
            assertTrue(boxes.stream().noneMatch(InfoBox::render));
        }
        profile = "account-b";
        plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("account-a", profile));
        flush();
        assertNull(tracker.getActiveTask());
        assertFalse(plugin.getViewModel().isActive());
        assertEquals(profile, repository.getProfileIdentity());
    }

    @Test public void idleDisplaysReturnOnProgressAndHideOnCompletion() throws Exception
    {
        assertFalse(plugin.isOverlayVisible());
        assertTrue(plugin.getViewModel().isActive());
        assertTrue(boxes.stream().noneMatch(InfoBox::render));
        progress();
        assertTrue(plugin.isOverlayVisible());
        assertTrue(boxes.stream().allMatch(InfoBox::render));
        ChatMessage completion = new ChatMessage();
        completion.setType(ChatMessageType.GAMEMESSAGE);
        completion.setMessage("You have completed your task!");
        plugin.onChatMessage(completion);
        assertTrue(tracker.isCompletionPending());
        assertFalse(plugin.isOverlayVisible());
        assertTrue(boxes.stream().noneMatch(InfoBox::render));
    }

    @Test public void sidebarInteractionRestoresVisibilityWithoutInventingTaskProgress() throws Exception
    {
        SwingUtilities.invokeAndWait(navigation.getPanel()::onActivate);
        flush();
        assertTrue(plugin.isOverlayVisible());
        assertEquals(0, tracker.getActiveTask().getTaskProgressUnits());
        assertEquals(0L, tracker.getActiveTask().getActiveMillis());
    }

    @Test public void targetCombatKeepsAnIdleDisplayVisibleWithoutAKill() throws Exception
    {
        assertFalse(plugin.isOverlayVisible());
        NPC target = mock(NPC.class);
        Player player = mock(Player.class);
        when(player.getInteracting()).thenReturn(target);
        when(client.getLocalPlayer()).thenReturn(player);
        when(slayer.getTargets()).thenReturn(Collections.singletonList(target));
        gameTick();
        assertTrue(plugin.isOverlayVisible());
        assertEquals(0, tracker.getActiveTask().getTaskProgressUnits());
    }

    @Test public void infoboxSettingsApplyWithoutDuplicateRegistration() throws Exception
    {
        gameTick();
        assertEquals(3, boxes.size());
        assertEquals(3, boxes.stream().map(InfoBox::getName).distinct().count());
        ConfigChanged event = new ConfigChanged();
        event.setGroup(SlayerSpeedConfig.GROUP);
        event.setKey("showInfoBoxes");
        showInfoBoxes = false;
        plugin.onConfigChanged(event);
        flush();
        assertTrue(boxes.isEmpty());
        assertTrue(plugin.getViewModel().isActive());
        showInfoBoxes = true;
        plugin.onConfigChanged(event);
        flush();
        assertEquals(3, boxes.size());
    }

    @Test public void rapidRestartDiscardsStaleInitializationAndPreservesCheckpoint() throws Exception
    {
        String taskId = tracker.getActiveTask().getId();
        SwingUtilities.invokeAndWait(plugin::shutDown);
        flush();
        clearInvocations(client);
        SwingUtilities.invokeAndWait(() ->
        {
            plugin.startUp();
            plugin.shutDown();
            plugin.startUp();
        });
        gameTick();
        assertTrue(plugin.isRunning());
        assertEquals(taskId, tracker.getActiveTask().getId());
        assertEquals(3, boxes.size());
        verify(client, times(2)).getVarpValue(anyInt());
    }

    private void progress() throws Exception
    {
        when(slayer.getRemainingAmount()).thenReturn(99);
        gameTick();
    }
    private void gameTick() throws Exception
    {
        drain();
        tick++;
        plugin.onGameTick(new GameTick());
        flush();
    }
    private void flush() throws Exception { drain(); SwingUtilities.invokeAndWait(() -> {}); }
    private void drain()
    {
        Runnable task;
        while ((task = clientTasks.poll()) != null) { task.run(); }
    }
}
