package com.slayerspeed.mortimer;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MortimerTaskChoiceParserTest
{
	@Test
	public void readsOffersAndAppliesPositiveQuantityMortifier()
	{
		List<MortimerTaskChoiceParser.Element> elements = Arrays.asList(
			element(WidgetType.RECTANGLE, 0, 0, "", 10, 20, 512, 100),
			element(WidgetType.TEXT, 20, 5, "<u=ff981f>Araxytes", 30, 25, 60, 30),
			element(WidgetType.TEXT, 175, 15, "Amount: 120 to 180", 185, 35, 150, 25),
			element(WidgetType.TEXT, 155, 45, "+50 Assigned", 165, 65, 190, 25));

		List<MortimerTaskOffer> offers = MortimerTaskChoiceParser.parseElements(
			elements, new Rectangle(10, 20, 512, 300));

		assertEquals(1, offers.size());
		assertEquals("Araxytes", offers.get(0).getTaskName());
		assertEquals(170, offers.get(0).getMinimumAmount());
		assertEquals(230, offers.get(0).getMaximumAmount());
		assertEquals(new Rectangle(10, 20, 512, 100), offers.get(0).getRowBounds());
	}

	@Test
	public void appliesNegativeQuantityMortifierButIgnoresOtherModifiers()
	{
		List<MortimerTaskChoiceParser.Element> elements = Arrays.asList(
			element(WidgetType.RECTANGLE, 0, 0, "", 0, 0, 512, 100),
			element(WidgetType.TEXT, 20, 5, "Crawling hands", 20, 5, 60, 30),
			element(WidgetType.TEXT, 175, 15, "Amount: 35 to 50", 175, 15, 150, 25),
			element(WidgetType.TEXT, 155, 45, "-15 Assigned", 155, 45, 190, 25),
			element(WidgetType.RECTANGLE, 0, 100, "", 0, 100, 512, 100),
			element(WidgetType.TEXT, 20, 105, "Gargoyles", 20, 105, 60, 30),
			element(WidgetType.TEXT, 175, 115, "Amount: 120 to 180", 175, 115, 150, 25),
			element(WidgetType.TEXT, 155, 145, "(+35% Slayer XP)", 155, 145, 190, 25));

		List<MortimerTaskOffer> offers = MortimerTaskChoiceParser.parseElements(
			elements, new Rectangle(0, 0, 512, 300));

		assertEquals(2, offers.size());
		assertEquals(20, offers.get(0).getMinimumAmount());
		assertEquals(35, offers.get(0).getMaximumAmount());
		assertEquals(120, offers.get(1).getMinimumAmount());
		assertEquals(180, offers.get(1).getMaximumAmount());
	}

	@Test
	public void supportsFormattedAmountsAndFallbackRowBounds()
	{
		List<MortimerTaskChoiceParser.Element> elements = Arrays.asList(
			element(WidgetType.TEXT, 20, 205, "Abyssal demons", 20, 205, 60, 30),
			element(WidgetType.TEXT, 175, 215, "Amount: 1,200\u20131,800", 175, 215, 150, 25));

		List<MortimerTaskOffer> offers = MortimerTaskChoiceParser.parseElements(
			elements, new Rectangle(50, 60, 512, 300));

		assertEquals(1, offers.size());
		assertEquals(1_200, offers.get(0).getMinimumAmount());
		assertEquals(1_800, offers.get(0).getMaximumAmount());
		assertEquals(new Rectangle(50, 260, 512, 100), offers.get(0).getRowBounds());
	}

	private static MortimerTaskChoiceParser.Element element(
		int type,
		int relativeX,
		int relativeY,
		String text,
		int x,
		int y,
		int width,
		int height)
	{
		return new MortimerTaskChoiceParser.Element(
			type, relativeX, relativeY, text, new Rectangle(x, y, width, height));
	}
}
