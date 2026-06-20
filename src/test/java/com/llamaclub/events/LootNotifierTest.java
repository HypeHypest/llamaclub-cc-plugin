package com.llamaclub.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class LootNotifierTest
{
	@Test
	public void skipsLowValueNpcLootWhenUntradeableHasNoHaValue()
	{
		List<Map<String, Object>> items = List.of(
			item("Potato seed", 3, 8, 1, false),
			item("Bull bones", 1, 0, 0, true),
			item("Raw t-bone steak", 1, 77, 77, false)
		);

		Assert.assertFalse(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			Set.of()
		));
	}

	@Test
	public void notifiesWhenSingleItemMeetsMinimum()
	{
		List<Map<String, Object>> items = List.of(
			item("Raw t-bone steak", 1, 300, 77, false)
		);

		Assert.assertTrue(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			Set.of()
		));
	}

	@Test
	public void skipsWhenTotalMeetsMinimumButNoSingleItemDoes()
	{
		List<Map<String, Object>> items = List.of(
			item("Potato seed", 10, 13, 1, false),
			item("Raw t-bone steak", 1, 130, 77, false)
		);

		Assert.assertFalse(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			Set.of()
		));
	}

	@Test
	public void includesUntradeableHaValueForSingleItemCheck()
	{
		List<Map<String, Object>> items = List.of(
			item("Dragon defender", 1, 0, 200, true)
		);

		Assert.assertFalse(LootNotifier.passesLootFilter(
			items,
			false,
			250,
			Set.of()
		));

		Assert.assertFalse(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			Set.of()
		));

		Assert.assertTrue(LootNotifier.passesLootFilter(
			items,
			true,
			200,
			Set.of()
		));
	}

	@Test
	public void whitelistBypassesMinimumValue()
	{
		List<Map<String, Object>> items = List.of(
			item("Bull bones", 1, 0, 0, true)
		);

		Assert.assertTrue(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			Set.of("bull bones")
		));
	}

	@Test
	public void mergedBingoWhitelistBypassesMinimumValue()
	{
		List<Map<String, Object>> items = List.of(
			item("Pendant of ates", 1, 0, 0, true)
		);

		Set<String> merged = LootNotifier.mergedLootWhitelist(
			Set.of(),
			Set.of("pendant of ates")
		);

		Assert.assertTrue(LootNotifier.passesLootFilter(
			items,
			true,
			250,
			merged
		));
	}

	private static Map<String, Object> item(
		String name,
		int quantity,
		int gePrice,
		int haValue,
		boolean untradeable
	)
	{
		Map<String, Object> item = new HashMap<>();
		item.put("name", name);
		item.put("quantity", quantity);
		item.put("gePrice", gePrice);
		item.put("haValue", haValue);
		item.put("totalValue", (long) gePrice * quantity);
		item.put("untradeable", untradeable);
		return item;
	}
}
