package com.llamaclub.events;

import com.google.gson.Gson;
import com.llamaclub.loot.RarityService;
import com.llamaclub.loot.ThievingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LootNotifierRarityTest
{
	private static final int BROKEN_DRAGON_HOOK = 31961;

	@Mock
	private ItemManager itemManager;

	@Mock
	private ItemComposition brokenDragonHookComposition;

	private RarityService rarityService;
	private ThievingService thievingService;

	@Before
	public void setUp()
	{
		when(itemManager.getItemComposition(BROKEN_DRAGON_HOOK)).thenReturn(brokenDragonHookComposition);
		when(brokenDragonHookComposition.getNote()).thenReturn(-1);
		when(brokenDragonHookComposition.getMembersName()).thenReturn("Broken dragon hook");

		Gson gson = new Gson();
		rarityService = new RarityService(gson, itemManager);
		thievingService = new ThievingService(gson, itemManager);
	}

	@Test
	public void selectsRarestQualifyingItemProbability()
	{
		List<Map<String, Object>> items = List.of(
			lootItem(BROKEN_DRAGON_HOOK, 1),
			lootItem(383, 1)
		);

		Double rarest = LootNotifier.findRarestProbability(
			"Great white shark",
			"NPC",
			items,
			rarityService,
			thievingService
		);

		Assert.assertNotNull(rarest);
		Assert.assertEquals(1.0 / 1023.0, rarest, 0.000001);
	}

	@Test
	public void ignoresEventSourcesWithoutRarityData()
	{
		Double rarest = LootNotifier.findRarestProbability(
			"Lunar Chest",
			"EVENT",
			List.of(lootItem(1939, 98)),
			rarityService,
			thievingService
		);

		Assert.assertNull(rarest);
	}

	private static Map<String, Object> lootItem(int itemId, int quantity)
	{
		Map<String, Object> item = new HashMap<>();
		item.put("id", itemId);
		item.put("quantity", quantity);
		return item;
	}
}
