package com.llamaclub.events;

import org.junit.Assert;
import org.junit.Test;

public class CollectionLogNotifierTest
{
	@Test
	public void normalizeItemNameStripsPopupMarkup()
	{
		Assert.assertEquals(
			"Mooleta",
			CollectionLogNotifier.normalizeItemName("<br><br><col=ffffff>Mooleta</col>")
		);
	}

	@Test
	public void normalizeItemNameStripsChatColorTags()
	{
		Assert.assertEquals(
			"Cow slippers",
			CollectionLogNotifier.normalizeItemName("<col=ffffff>Cow slippers</col>")
		);
	}

	@Test
	public void normalizeItemNameReturnsNullForNullInput()
	{
		Assert.assertNull(CollectionLogNotifier.normalizeItemName(null));
	}
}
