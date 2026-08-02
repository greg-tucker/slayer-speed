package com.slayerspeed.ui;

import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;

public final class SlayerSpeedIcon
{
	private SlayerSpeedIcon()
	{
	}

	public static BufferedImage load()
	{
		return ImageUtil.loadImageResource(SlayerSpeedIcon.class, "icon.png");
	}
}
