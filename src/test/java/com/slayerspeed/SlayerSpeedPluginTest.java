package com.slayerspeed;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlayerSpeedPluginTest
{
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
