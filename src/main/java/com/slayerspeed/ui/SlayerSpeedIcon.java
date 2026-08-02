package com.slayerspeed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class SlayerSpeedIcon
{
	private SlayerSpeedIcon()
	{
	}

	public static BufferedImage create()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(220, 80, 45));
			graphics.fillOval(3, 3, 26, 26);
			graphics.setColor(Color.WHITE);
			graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawLine(10, 22, 22, 10);
			graphics.drawLine(11, 10, 22, 10);
			graphics.drawLine(22, 10, 22, 21);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}
}

