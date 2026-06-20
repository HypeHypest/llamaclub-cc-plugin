/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.llamaclub.loot.RarityService;
import com.llamaclub.loot.ThievingService;
import com.llamaclub.service.BingoLootWhitelistService;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Singleton
public class LootNotifier extends BaseNotifier
{
	/**
	 * NPC IDs that fire LootReceived instead of NpcLootReceived.
	 * Handled in onLootReceived to avoid duplicate notifications.
	 */
	private static final Set<Integer> SPECIAL_LOOT_NPC_IDS = Set.of(
		NpcID.WHISPERER, NpcID.WHISPERER_MELEE, NpcID.WHISPERER_QUEST, NpcID.WHISPERER_MELEE_QUEST,
		NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD, NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING_INACTIVE,
		NpcID.YAMA,
		NpcID.HESPORI,
		NpcID.GRYPHON_BOSS,
		NpcID.GB_HILLGIANT_CHEST,
		NpcID.GB_MOSSGIANT_CHEST
	);

	/**
	 * NPC names that fire LootReceived instead of NpcLootReceived.
	 */
	private static final Set<String> SPECIAL_LOOT_NPC_NAMES = Set.of(
		"The Whisperer", "Araxxor",
		"Branda the Fire Queen", "Eldric the Ice King",
		"Crystalline Hunllef", "Corrupted Hunllef",
		"The Gauntlet", "Corrupted Gauntlet",
		"Shellbane gryphon",
		"Obor (Chest)",
		"Bryophyta (Chest)"
	);

	private static final Pattern LOOT_TRACKER_KILLS = Pattern.compile("\"kills\"\\s*:\\s*(\\d+)");

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RarityService rarityService;

	@Inject
	private ThievingService thievingService;

	@Inject
	private BingoLootWhitelistService bingoLootWhitelistService;

	@Override
	public boolean isEnabled()
	{
		return config.notifyLoot();
	}

	@Override
	protected String getEventKind()
	{
		return "LOOT";
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!isEnabled())
		{
			return;
		}

		int npcId = event.getComposition().getId();
		String name = event.getComposition().getName();

		// Yama, Hespori, and Hallowed Sepulchre use ServerNpcLoot instead of NpcLootReceived
		if (npcId != NpcID.YAMA && npcId != NpcID.HESPORI && !name.startsWith("Hallowed Sepulchre"))
		{
			return;
		}

		handleLootDrop(event.getItems(), name, "NPC", npcId, 0);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!isEnabled())
		{
			return;
		}

		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}

		int npcId = npc.getId();
		if (SPECIAL_LOOT_NPC_IDS.contains(npcId))
		{
			return;
		}

		String sourceName = "Unknown";
		int combatLevel = 0;
		NPCComposition composition = npc.getComposition();
		if (composition != null)
		{
			sourceName = composition.getName();
			combatLevel = composition.getCombatLevel();
		}

		handleLootDrop(event.getItems(), sourceName, "NPC", npcId, combatLevel);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!isEnabled())
		{
			return;
		}

		handleLootDrop(event.getItems(), event.getPlayer().getName(), "PLAYER", null, 0);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isEnabled())
		{
			return;
		}

		if (event.getType() == LootRecordType.EVENT || event.getType() == LootRecordType.PICKPOCKET)
		{
			if (isClueScrollLootSource(event.getName()))
			{
				return;
			}

			handleLootDrop(event.getItems(), event.getName(), "EVENT", null, 0);
		}
		else if (event.getType() == LootRecordType.NPC && SPECIAL_LOOT_NPC_NAMES.contains(event.getName()))
		{
			String source = event.getName();
			if ("The Gauntlet".equals(source) || "Corrupted Gauntlet".equals(source))
			{
				handleLootDrop(event.getItems(), source, "EVENT", null, 0);
			}
			else
			{
				handleLootDrop(event.getItems(), source, "NPC", null, 0);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled() || !isGameMessage(event))
		{
			return;
		}

		String message = event.getMessage();
		if ("You have found the Pharaoh's sceptre!".equals(message)
			|| "You have found a Pharaoh's sceptre!".equals(message))
		{
			handleLootDrop(
				List.of(new ItemStack(ItemID.PHARAOHS_SCEPTRE, 1)),
				"Pyramid Plunder",
				"EVENT",
				null,
				0
			);
		}
	}

	private void handleLootDrop(
		Collection<ItemStack> items,
		String source,
		String sourceType,
		Integer sourceId,
		int combatLevel
	)
	{
		ProcessedLoot processed = processItems(items);
		if (processed.items.isEmpty())
		{
			return;
		}

		if (!shouldNotify(processed))
		{
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("source", source);
		data.put("sourceType", sourceType);
		if (sourceId != null)
		{
			data.put("sourceId", sourceId);
		}
		if (combatLevel > 0)
		{
			data.put("combatLevel", combatLevel);
		}
		data.put("totalValue", processed.totalGEValue);
		data.put("totalGEValue", processed.totalGEValue);
		data.put("totalHAValue", processed.totalHAValue);
		data.put("items", processed.items);

		Integer killCount = lookupKillCount(source, sourceType);
		if (killCount != null)
		{
			data.put("killCount", killCount);
		}

		Double rarestProbability = findRarestProbability(source, sourceType, processed.items);
		if (rarestProbability != null)
		{
			data.put("rarestProbability", rarestProbability);
		}

		sendEvent(data);
	}

	private ProcessedLoot processItems(Collection<ItemStack> itemStacks)
	{
		List<Map<String, Object>> itemsList = new ArrayList<>();
		long totalGEValue = 0;
		long totalHAValue = 0;

		for (ItemStack stack : itemStacks)
		{
			int itemId = stack.getId();
			int quantity = stack.getQuantity();
			if (itemId <= 0 || quantity <= 0)
			{
				continue;
			}

			ItemComposition composition = itemManager.getItemComposition(itemId);
			int gePrice = itemManager.getItemPrice(itemId);
			int haValue = composition != null ? composition.getPrice() : 0;
			boolean untradeable = composition != null && !composition.isTradeable();

			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			itemData.put("name", composition != null ? composition.getName() : "Unknown");
			itemData.put("quantity", quantity);
			itemData.put("gePrice", gePrice);
			itemData.put("haValue", haValue);
			itemData.put("totalValue", (long) gePrice * quantity);
			itemData.put("untradeable", untradeable);
			itemsList.add(itemData);

			totalGEValue += (long) gePrice * quantity;
			totalHAValue += (long) haValue * quantity;
		}

		return new ProcessedLoot(itemsList, totalGEValue, totalHAValue);
	}

	private boolean shouldNotify(ProcessedLoot processed)
	{
		Set<String> whitelist = mergedLootWhitelist(
			parseWhitelist(config.lootWhitelist()),
			bingoLootWhitelistService.asSet()
		);

		return passesLootFilter(
			processed.items,
			config.lootIncludeUntradeables(),
			config.lootMinValue(),
			whitelist
		);
	}

	static Set<String> mergedLootWhitelist(Set<String> manualWhitelist, Set<String> bingoWhitelist)
	{
		if (manualWhitelist.isEmpty() && (bingoWhitelist == null || bingoWhitelist.isEmpty()))
		{
			return Set.of();
		}

		Set<String> merged = new HashSet<>(manualWhitelist);
		if (bingoWhitelist != null)
		{
			merged.addAll(bingoWhitelist);
		}

		return merged;
	}

	static boolean passesLootFilter(
		List<Map<String, Object>> items,
		boolean includeUntradeables,
		int minValue,
		Set<String> whitelist
	)
	{
		for (Map<String, Object> item : items)
		{
			String name = String.valueOf(item.get("name")).toLowerCase();
			if (whitelist.contains(name))
			{
				return true;
			}

			if (getItemFilterValue(item, includeUntradeables) >= minValue)
			{
				return true;
			}
		}

		return false;
	}

	static long getItemFilterValue(Map<String, Object> item, boolean includeUntradeables)
	{
		int quantity = ((Number) item.get("quantity")).intValue();
		if (Boolean.TRUE.equals(item.get("untradeable")))
		{
			if (!includeUntradeables)
			{
				return 0;
			}

			int haValue = ((Number) item.get("haValue")).intValue();
			return (long) haValue * quantity;
		}

		return ((Number) item.get("totalValue")).longValue();
	}

	private static Set<String> parseWhitelist(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return Set.of();
		}

		return Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(String::toLowerCase)
			.collect(Collectors.toCollection(HashSet::new));
	}

	static Double findRarestProbability(
		String source,
		String sourceType,
		List<Map<String, Object>> items,
		RarityService rarityService,
		ThievingService thievingService
	)
	{
		Double rarest = null;

		for (Map<String, Object> item : items)
		{
			Object idValue = item.get("id");
			Object quantityValue = item.get("quantity");
			if (!(idValue instanceof Number) || !(quantityValue instanceof Number))
			{
				continue;
			}

			int itemId = ((Number) idValue).intValue();
			int quantity = ((Number) quantityValue).intValue();

			OptionalDouble rarity;
			if ("NPC".equals(sourceType))
			{
				rarity = rarityService.getRarity(source, itemId, quantity);
			}
			else if ("PICKPOCKET".equals(sourceType))
			{
				rarity = thievingService.getRarity(source, itemId, quantity);
			}
			else
			{
				rarity = OptionalDouble.empty();
			}

			if (!rarity.isPresent())
			{
				continue;
			}

			double probability = rarity.getAsDouble();
			if (probability >= 1.0)
			{
				continue;
			}

			if (rarest == null || probability < rarest)
			{
				rarest = probability;
			}
		}

		return rarest;
	}

	private Double findRarestProbability(String source, String sourceType, List<Map<String, Object>> items)
	{
		return findRarestProbability(source, sourceType, items, rarityService, thievingService);
	}

	private Integer lookupKillCount(String source, String sourceType)
	{
		if (source == null || source.isBlank())
		{
			return null;
		}

		Integer lootTrackerKills = lookupLootTrackerKills(sourceType, source);
		if (lootTrackerKills != null)
		{
			return lootTrackerKills;
		}

		if (!"NPC".equals(sourceType))
		{
			return null;
		}

		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return null;
		}

		Integer killCount = configManager.getRSProfileConfiguration("killcount", source.toLowerCase(), int.class);
		if (killCount != null)
		{
			return killCount;
		}

		if (source.toLowerCase().startsWith("the "))
		{
			return configManager.getRSProfileConfiguration(
				"killcount",
				source.substring(4).toLowerCase(),
				int.class
			);
		}

		return null;
	}

	private Integer lookupLootTrackerKills(String sourceType, String source)
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return null;
		}

		String json = configManager.getConfiguration(
			LootTrackerConfig.GROUP,
			profile,
			"drops_" + sourceType + "_" + source
		);
		if (json == null || json.isBlank())
		{
			return null;
		}

		Matcher matcher = LOOT_TRACKER_KILLS.matcher(json);
		if (!matcher.find())
		{
			return null;
		}

		try
		{
			return Integer.parseInt(matcher.group(1));
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static boolean isClueScrollLootSource(String source)
	{
		if (source == null || source.isBlank())
		{
			return false;
		}

		return source.regionMatches(true, 0, "Clue Scroll", 0, "Clue Scroll".length());
	}

	private static final class ProcessedLoot
	{
		private final List<Map<String, Object>> items;
		private final long totalGEValue;
		private final long totalHAValue;

		private ProcessedLoot(List<Map<String, Object>> items, long totalGEValue, long totalHAValue)
		{
			this.items = items;
			this.totalGEValue = totalGEValue;
			this.totalHAValue = totalHAValue;
		}
	}
}
