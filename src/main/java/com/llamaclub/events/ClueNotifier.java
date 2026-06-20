/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.clue.ClueTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class ClueNotifier extends BaseNotifier
{
	private static final Pattern CLUE_SCROLL_REGEX = Pattern.compile(
		"You have completed (?<scrollCount>\\d+) (?<scrollType>\\w+) Treasure Trails?\\."
	);

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	private final AtomicInteger badTicks = new AtomicInteger();

	private volatile int clueCount = -1;
	private volatile String clueType = "";

	@Override
	public boolean isEnabled()
	{
		return config.notifyClue();
	}

	@Override
	protected String getEventKind()
	{
		return "CLUE";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled() || !isGameMessage(event))
		{
			return;
		}

		Map.Entry<String, Integer> parsed = parse(event.getMessage());
		if (parsed == null || !checkClueTier(parsed.getKey()))
		{
			return;
		}

		clueCount = parsed.getValue();
		clueType = parsed.getKey();
		badTicks.set(0);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!isEnabled() || event.getGroupId() != InterfaceID.TRAIL_REWARDSCREEN || clueType.isEmpty())
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			Widget itemsWidget = client.getWidget(InterfaceID.TrailRewardscreen.ITEMS);
			if (itemsWidget == null)
			{
				return;
			}

			Widget[] children = itemsWidget.getChildren();
			if (children == null)
			{
				return;
			}

			Map<Integer, Integer> clueItems = new HashMap<>();
			for (Widget child : children)
			{
				if (child == null)
				{
					continue;
				}

				int itemId = child.getItemId();
				int quantity = child.getItemQuantity();
				if (itemId > 0 && quantity > 0)
				{
					clueItems.merge(itemId, quantity, Integer::sum);
				}
			}

			handleNotify(clueItems);
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!clueType.isEmpty())
		{
			badTicks.getAndIncrement();
		}

		if (badTicks.get() > 1)
		{
			reset();
		}
	}

	private void handleNotify(Map<Integer, Integer> clueItems)
	{
		List<Map<String, Object>> items = new ArrayList<>();
		long totalGeValue = 0;

		for (Map.Entry<Integer, Integer> entry : clueItems.entrySet())
		{
			int itemId = entry.getKey();
			int quantity = entry.getValue();
			ItemComposition composition = itemManager.getItemComposition(itemId);
			int gePrice = itemManager.getItemPrice(itemId);
			long lineValue = (long) gePrice * quantity;
			totalGeValue += lineValue;

			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			itemData.put("name", composition != null ? composition.getName() : "Unknown");
			itemData.put("quantity", quantity);
			itemData.put("gePrice", gePrice);
			itemData.put("priceEach", gePrice);
			itemData.put("totalValue", lineValue);
			items.add(itemData);
		}

		if (totalGeValue < config.clueMinValue())
		{
			reset();
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("tier", clueType);
		data.put("clueType", clueType);
		data.put("count", clueCount);
		data.put("numberCompleted", clueCount);
		data.put("items", items);
		data.put("totalGEValue", totalGeValue);
		data.put("totalValue", totalGeValue);

		boolean includeScreenshot = config.sendEventScreenshots()
			&& totalGeValue >= config.clueScreenshotMinValue();
		sendEvent(data, includeScreenshot);
		reset();
	}

	private boolean checkClueTier(String tierName)
	{
		ClueTier tier = ClueTier.parse(tierName);
		if (tier == null)
		{
			log.warn("Failed to parse clue tier: {}", tierName);
			return true;
		}
		return tier.isEnabled(config);
	}

	private static Map.Entry<String, Integer> parse(String message)
	{
		Matcher matcher = CLUE_SCROLL_REGEX.matcher(message);
		if (!matcher.find())
		{
			return null;
		}

		return Map.entry(matcher.group("scrollType"), Integer.parseInt(matcher.group("scrollCount")));
	}

	private void reset()
	{
		clueCount = -1;
		clueType = "";
		badTicks.set(0);
	}
}
