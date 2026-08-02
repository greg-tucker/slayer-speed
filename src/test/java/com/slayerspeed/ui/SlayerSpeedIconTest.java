package com.slayerspeed.ui;

import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SlayerSpeedIconTest
{
	@Test
	public void loadsPackagedToolbarIcon()
	{
		BufferedImage icon = SlayerSpeedIcon.load();

		assertNotNull(icon);
		assertEquals(32, icon.getWidth());
		assertEquals(32, icon.getHeight());
	}
}
