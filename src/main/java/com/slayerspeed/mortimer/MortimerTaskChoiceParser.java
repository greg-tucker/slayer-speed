package com.slayerspeed.mortimer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

public class MortimerTaskChoiceParser
{
	// RuneLite 1.12.33 predates the generated SlayerTaskChoice gameval class. Keep this
	// compatibility ID isolated here until the named constant is available in latest.release.
	private static final int CONTENT_PACKED_ID = (236 << 16) | 3;
	private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
		"(?i)^Amount:\\s*([\\d,]+)\\s*(?:to|-|\\u2013)\\s*([\\d,]+)$");
	private static final Pattern QUANTITY_PATTERN = Pattern.compile(
		"(?i)^\\(?([+-])\\s*([\\d,]+)\\s+Assigned\\)?$");

	private final Client client;

	@Inject
	public MortimerTaskChoiceParser(Client client)
	{
		this.client = client;
	}

	public List<MortimerTaskOffer> readOffers()
	{
		Widget content = client.getWidget(CONTENT_PACKED_ID);
		if (content == null || content.isHidden())
		{
			return new ArrayList<>();
		}

		Widget[] children = content.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return new ArrayList<>();
		}

		List<Element> elements = new ArrayList<>();
		for (Widget child : children)
		{
			if (child != null && !child.isHidden())
			{
				elements.add(new Element(
					child.getType(), child.getRelativeX(), child.getRelativeY(),
					child.getText(), child.getBounds()));
			}
		}
		return parseElements(elements, content.getBounds());
	}

	static List<MortimerTaskOffer> parseElements(List<Element> elements, Rectangle contentBounds)
	{
		List<MortimerTaskOffer> offers = new ArrayList<>();
		for (Element amountElement : elements)
		{
			if (amountElement.type != WidgetType.TEXT)
			{
				continue;
			}
			Matcher amountMatcher = AMOUNT_PATTERN.matcher(cleanText(amountElement.text));
			if (!amountMatcher.matches())
			{
				continue;
			}

			Element taskElement = findTaskElement(elements, amountElement);
			if (taskElement == null)
			{
				continue;
			}
			int minimum = parseNumber(amountMatcher.group(1));
			int maximum = parseNumber(amountMatcher.group(2));
			int adjustment = findQuantityAdjustment(elements, amountElement.relativeY + 30);
			minimum = Math.max(1, minimum + adjustment);
			maximum = Math.max(minimum, maximum + adjustment);
			Rectangle rowBounds = findRowBounds(elements, amountElement, contentBounds);
			offers.add(new MortimerTaskOffer(
				cleanText(taskElement.text), minimum, maximum, rowBounds));
		}
		offers.sort(Comparator.comparingInt(offer -> offer.getRowBounds().y));
		return offers;
	}

	private static Element findTaskElement(List<Element> elements, Element amountElement)
	{
		Element best = null;
		int expectedY = amountElement.relativeY - 10;
		int bestDistance = Integer.MAX_VALUE;
		for (Element candidate : elements)
		{
			String text = cleanText(candidate.text);
			if (candidate.type != WidgetType.TEXT
				|| candidate.relativeX >= amountElement.relativeX
				|| text.isEmpty()
				|| AMOUNT_PATTERN.matcher(text).matches()
				|| QUANTITY_PATTERN.matcher(text).find())
			{
				continue;
			}
			int distance = Math.abs(candidate.relativeY - expectedY);
			if (distance <= 15 && distance < bestDistance)
			{
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static int findQuantityAdjustment(List<Element> elements, int expectedY)
	{
		for (Element element : elements)
		{
			if (element.type != WidgetType.TEXT || Math.abs(element.relativeY - expectedY) > 8)
			{
				continue;
			}
			Matcher matcher = QUANTITY_PATTERN.matcher(cleanText(element.text));
			if (matcher.find())
			{
				int amount = parseNumber(matcher.group(2));
				return "-".equals(matcher.group(1)) ? -amount : amount;
			}
		}
		return 0;
	}

	private static Rectangle findRowBounds(
		List<Element> elements,
		Element amountElement,
		Rectangle contentBounds)
	{
		int rowRelativeY = amountElement.relativeY - 15;
		for (Element element : elements)
		{
			if (element.type == WidgetType.RECTANGLE
				&& element.relativeX == 0
				&& Math.abs(element.relativeY - rowRelativeY) <= 2
				&& element.bounds.width > 0
				&& element.bounds.height > 0)
			{
				return new Rectangle(element.bounds);
			}
		}

		Rectangle content = contentBounds == null ? new Rectangle() : contentBounds;
		int rowHeight = Math.max(1, content.height / 3);
		return new Rectangle(
			content.x,
			content.y + Math.max(0, rowRelativeY),
			content.width,
			rowHeight);
	}

	private static int parseNumber(String value)
	{
		try
		{
			return Integer.parseInt(value.replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	static String cleanText(String value)
	{
		if (value == null)
		{
			return "";
		}
		return TAG_PATTERN.matcher(value)
			.replaceAll("")
			.replace('\u00a0', ' ')
			.trim()
			.replaceAll("\\s+", " ");
	}

	static final class Element
	{
		private final int type;
		private final int relativeX;
		private final int relativeY;
		private final String text;
		private final Rectangle bounds;

		Element(int type, int relativeX, int relativeY, String text, Rectangle bounds)
		{
			this.type = type;
			this.relativeX = relativeX;
			this.relativeY = relativeY;
			this.text = text;
			this.bounds = bounds == null ? new Rectangle() : new Rectangle(bounds);
		}
	}
}
