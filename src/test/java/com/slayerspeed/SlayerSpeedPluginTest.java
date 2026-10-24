package com.slayerspeed;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlayerSpeedPluginTest
{
    @Test
    public void overlayExpiresAtTimeoutAndReturnsOnFreshActivity()
    {
        long activity = 1_000_000L;
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(activity, activity, 2));
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(activity + 119_999L, activity, 2));
        assertFalse(SlayerSpeedPlugin.isOverlayRecent(activity + 120_000L, activity, 2));
        assertFalse(SlayerSpeedPlugin.isOverlayRecent(activity + 900_000L, activity, 2));
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(activity + 900_000L, activity + 900_000L, 2));
    }

    @Test
    public void overlayTimeoutCanBeConfiguredWithoutChangingTrackingTimeout()
    {
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(900_000L, 1L, 0));
        assertFalse(SlayerSpeedPlugin.isOverlayRecent(900_000L, 1L, 1));
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(900_000L, 1L, 30));
        assertFalse(SlayerSpeedPlugin.isOverlayRecent(900_000L, 0L, 2));
        assertTrue(SlayerSpeedPlugin.isOverlayRecent(900_000L, 900_001L, 2));
    }

	@Test
	public void recognizesOnlyExplicitTaskCompletionMessages()
	{
		assertTrue(SlayerSpeedPlugin.isTaskCompletionMessage("You have completed your task!"));
		assertTrue(SlayerSpeedPlugin.isTaskCompletionMessage("  YOU HAVE COMPLETED YOUR TASK!  "));
		assertFalse(SlayerSpeedPlugin.isTaskCompletionMessage("You have completed a combat task!"));
		assertFalse(SlayerSpeedPlugin.isTaskCompletionMessage(null));
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerSpeedPlugin.class);
		RuneLite.main(args);
	}
}
