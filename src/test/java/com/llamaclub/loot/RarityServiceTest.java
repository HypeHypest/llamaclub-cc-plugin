package com.llamaclub.loot;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
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
public class RarityServiceTest
{
	private static final int BROKEN_DRAGON_HOOK = 31961;

	@Mock
	private ItemManager itemManager;

	@Mock
	private ItemComposition brokenDragonHookComposition;

	private RarityService rarityService;

	@Before
	public void setUp()
	{
		when(itemManager.getItemComposition(BROKEN_DRAGON_HOOK)).thenReturn(brokenDragonHookComposition);
		when(brokenDragonHookComposition.getNote()).thenReturn(-1);
		when(brokenDragonHookComposition.getMembersName()).thenReturn("Broken dragon hook");

		rarityService = new RarityService(new Gson(), itemManager);
	}

	@Test
	public void resolvesGreatWhiteSharkBrokenDragonHookRarity()
	{
		OptionalDouble rarity = rarityService.getRarity("Great white shark", BROKEN_DRAGON_HOOK, 1);

		Assert.assertTrue(rarity.isPresent());
		Assert.assertEquals(1.0 / 1023.0, rarity.getAsDouble(), 0.000001);
	}

	@Test
	public void returnsEmptyForUnknownSource()
	{
		OptionalDouble rarity = rarityService.getRarity("Unknown source", BROKEN_DRAGON_HOOK, 1);

		Assert.assertFalse(rarity.isPresent());
	}
}
