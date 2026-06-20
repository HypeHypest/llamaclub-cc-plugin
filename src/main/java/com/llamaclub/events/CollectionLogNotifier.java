/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.collection.CollectionLogRank;
import com.llamaclub.loot.RarityService;
import com.llamaclub.loot.RecentLootDrop;
import com.llamaclub.loot.RecentLootTracker;
import com.llamaclub.pet.PetItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@Singleton
public class CollectionLogNotifier extends BaseNotifier
{
	private static final Pattern COLLECTION_LOG_REGEX = Pattern.compile(
		"New item added to your collection log: (?<itemName>(.*))",
		Pattern.CASE_INSENSITIVE
	);
	/** All 18 cosmetic chompy bird hats, e.g. "Chompy bird hat (ogre expert)". */
	private static final Pattern CHOMPY_BIRD_HAT = Pattern.compile(
		"^Chompy bird hat",
		Pattern.CASE_INSENSITIVE
	);
	private static final int POPUP_PREFIX_LENGTH = "New item:".length();
	private static final int TOTAL_ENTRIES_FALLBACK = 1_692;
	private static final Pattern LOOT_TRACKER_KILLS = Pattern.compile("\"kills\"\\s*:\\s*(\\d+)");

	private final NavigableMap<Integer, CollectionLogRank> rankByThreshold = new TreeMap<>();
	private final AtomicInteger completed = new AtomicInteger(-1);
	private final AtomicBoolean popupStarted = new AtomicBoolean(false);

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RecentLootTracker recentLootTracker;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RarityService rarityService;

	private boolean initialized = false;

	@Override
	public boolean isEnabled()
	{
		return config.notifyCollection();
	}

	@Override
	protected String getEventKind()
	{
		return "COLLECTION_LOG";
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state != GameState.HOPPING && state != GameState.LOGGED_IN)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!initialized)
		{
			clientThread.invokeLater(this::init);
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			completed.set(-1);
		}
		else if (completed.get() < 0)
		{
			int varpValue = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
			if (varpValue > 0)
			{
				completed.set(varpValue);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() != VarPlayerID.COLLECTION_COUNT || !isEnabled())
		{
			return;
		}

		completed.set(event.getValue());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled() || !isGameMessage(event))
		{
			return;
		}

		if (client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) != 1)
		{
			return;
		}

		Matcher matcher = COLLECTION_LOG_REGEX.matcher(event.getMessage());
		if (!matcher.find())
		{
			return;
		}

		String itemName = matcher.group("itemName").trim();
		clientThread.invokeLater(() -> handleNotify(itemName, "chat"));
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.NOTIFICATION_START)
		{
			popupStarted.set(true);
			return;
		}

		if (event.getScriptId() != ScriptID.NOTIFICATION_DELAY || !isEnabled())
		{
			return;
		}

		String topText = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		if (!popupStarted.getAndSet(false) || !"Collection log".equalsIgnoreCase(topText))
		{
			return;
		}

		String bottomText = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
		if (bottomText == null || bottomText.length() <= POPUP_PREFIX_LENGTH)
		{
			return;
		}

		String itemName = bottomText.substring(POPUP_PREFIX_LENGTH).trim();
		clientThread.invokeLater(() -> handleNotify(itemName, "popup"));
	}

	private void init()
	{
		int maxCount = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (maxCount <= 0)
		{
			return;
		}

		populateThresholds(maxCount);
		initialized = true;
	}

	private void populateThresholds(int maxCount)
	{
		rankByThreshold.clear();
		for (CollectionLogRank rank : CollectionLogRank.values())
		{
			try
			{
				int threshold = rank.getClogRankThreshold(client, maxCount);
				if (threshold > 0 || rank == CollectionLogRank.NONE)
				{
					rankByThreshold.put(threshold, rank);
				}
			}
			catch (RuntimeException e)
			{
				log.warn("Could not load collection log rank threshold for {}", rank.name());
			}
		}
	}

	static String normalizeItemName(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}

		return Text.removeTags(itemName).trim();
	}

	private void handleNotify(String itemName, String detectionSource)
	{
		if (!isEnabled())
		{
			return;
		}

		itemName = normalizeItemName(itemName);
		if (itemName == null || itemName.isEmpty())
		{
			return;
		}

		if (config.notifyPet() && PetItems.isPet(itemName))
		{
			return;
		}

		if (isDenylisted(itemName))
		{
			return;
		}

		if (!initialized)
		{
			populateThresholds(TOTAL_ENTRIES_FALLBACK);
		}

		int completedCount = this.completed.updateAndGet(i -> i >= 0 ? i + 1 : i);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		boolean varpValid = total > 0 && completedCount > 0;

		Map.Entry<Integer, CollectionLogRank> nextRankEntry = rankByThreshold.higherEntry(completedCount);
		Map.Entry<Integer, CollectionLogRank> prevRankEntry = rankByThreshold.floorEntry(completedCount);
		CollectionLogRank rank = prevRankEntry != null ? prevRankEntry.getValue() : null;
		CollectionLogRank nextRank = completedCount > 0 && nextRankEntry != null ? nextRankEntry.getValue() : null;
		Integer rankProgress = prevRankEntry != null ? completedCount - prevRankEntry.getKey() : null;
		Map.Entry<Integer, CollectionLogRank> justCompletedEntry = prevRankEntry != null && rankProgress == 0
			? rankByThreshold.lowerEntry(prevRankEntry.getKey())
			: null;
		CollectionLogRank justCompletedRank = justCompletedEntry != null ? justCompletedEntry.getValue() : null;
		Integer logsNeededForNextRank = completedCount > 0 && nextRankEntry != null
			? nextRankEntry.getKey() - completedCount
			: null;

		if (!varpValid)
		{
			log.debug("Collection log progress varps were invalid ({} / {})", completedCount, total);
		}

		Integer itemId = findItemId(itemName);
		Long gePrice = itemId != null ? (long) itemManager.getItemPrice(itemId) : null;

		RecentLootDrop recentDrop = itemId != null ? recentLootTracker.findDropContaining(itemId) : null;
		String dropSource = recentDrop != null ? recentDrop.getSource() : null;
		String dropSourceType = recentDrop != null ? recentDrop.getSourceType() : null;
		Integer dropSourceId = recentDrop != null ? recentDrop.getSourceId() : null;
		Integer killCount = lookupKillCount(dropSource, dropSourceType);

		Map<String, Object> data = new HashMap<>();
		data.put("itemName", itemName);
		data.put("item", itemName);
		data.put("detectionSource", detectionSource);

		if (itemId != null)
		{
			data.put("itemId", itemId);
		}
		if (gePrice != null)
		{
			data.put("gePrice", gePrice);
		}
		if (varpValid)
		{
			data.put("completed", completedCount);
			data.put("total", total);
		}
		else
		{
			data.put("totalPossible", total > 0 ? total : TOTAL_ENTRIES_FALLBACK);
		}
		if (rank != null)
		{
			data.put("rank", rank.getDisplayName());
		}
		if (rankProgress != null)
		{
			data.put("rankProgress", rankProgress);
		}
		if (logsNeededForNextRank != null)
		{
			data.put("logsNeededForNextRank", logsNeededForNextRank);
		}
		if (nextRank != null)
		{
			data.put("nextRank", nextRank.getDisplayName());
		}
		if (justCompletedRank != null)
		{
			data.put("justCompletedRank", justCompletedRank.getDisplayName());
		}
		if (dropSource != null)
		{
			data.put("dropSource", dropSource);
		}
		if (dropSourceType != null)
		{
			data.put("dropSourceType", dropSourceType);
		}
		if (dropSourceId != null)
		{
			data.put("dropSourceId", dropSourceId);
		}
		if (killCount != null)
		{
			data.put("killCount", killCount);
		}

		if (itemId != null && dropSource != null && "NPC".equals(dropSourceType))
		{
			OptionalDouble dropRate = rarityService.getRarity(dropSource, itemId, 1);
			if (dropRate.isPresent())
			{
				double probability = dropRate.getAsDouble();
				if (probability > 0 && probability < 1.0)
				{
					data.put("dropRate", probability);
				}
			}
		}

		sendEvent(data);
	}

	private Integer findItemId(String itemName)
	{
		List<ItemPrice> results = itemManager.search(itemName);
		if (results == null || results.isEmpty())
		{
			return null;
		}

		for (ItemPrice result : results)
		{
			int id = result.getId();
			if (itemName.equalsIgnoreCase(itemManager.getItemComposition(id).getName()))
			{
				return id;
			}
		}

		return results.get(0).getId();
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

	private boolean isDenylisted(String itemName)
	{
		if (CHOMPY_BIRD_HAT.matcher(itemName).find())
		{
			return true;
		}

		String denylist = config.collectionDenylist();
		if (denylist == null || denylist.isBlank())
		{
			return false;
		}

		for (String entry : denylist.split(","))
		{
			String pattern = entry.trim();
			if (pattern.isEmpty())
			{
				continue;
			}

			String regex = pattern.replace("*", ".*");
			if (itemName.matches("(?i)" + regex))
			{
				return true;
			}
		}

		return false;
	}

	private void reset()
	{
		completed.set(-1);
		popupStarted.set(false);
	}
}
