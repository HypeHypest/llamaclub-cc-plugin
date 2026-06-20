/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.diary.DiaryDefinitions;
import com.llamaclub.diary.DiaryDefinitions.DiaryTier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class DiaryNotifier extends BaseNotifier
{
	private static final Pattern DIARY_COMPLETE = Pattern.compile(
		"Congratulations! You have completed all of the (Easy|Medium|Hard|Elite) (?:Achievement )?tasks in the (.+?) area\\.",
		Pattern.CASE_INSENSITIVE
	);

	private static final int INIT_DELAY_TICKS = 4;
	private static final int DEDUP_TICKS = 15;

	@Inject
	private ClientThread clientThread;

	private final Map<Integer, Integer> diaryCompletionById = new ConcurrentHashMap<>();
	private int initDelayTicks = 0;
	private String lastSentKey = null;
	private int lastSentTick = -1000;

	@Override
	public boolean isEnabled()
	{
		return config.notifyDiary();
	}

	@Override
	protected String getEventKind()
	{
		return "DIARY";
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (initDelayTicks > 0)
		{
			initDelayTicks--;
			if (initDelayTicks == 0)
			{
				initializeDiaries();
			}
		}
		else if (diaryCompletionById.isEmpty() && isEnabled())
		{
			initDelayTicks = INIT_DELAY_TICKS;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (id < 0)
		{
			return;
		}

		DiaryTier tier = DiaryDefinitions.varbitMap().get(id);
		if (tier == null || !isEnabled())
		{
			return;
		}

		if (diaryCompletionById.isEmpty())
		{
			if (client.getGameState() == GameState.LOGGED_IN && DiaryDefinitions.isComplete(id, event.getValue()))
			{
				return;
			}
			return;
		}

		int value = event.getValue();
		Integer previous = diaryCompletionById.get(id);

		if (previous == null)
		{
			reset();
			return;
		}

		if (value < previous)
		{
			reset();
			return;
		}

		if (value > previous)
		{
			diaryCompletionById.put(id, value);

			if (DiaryDefinitions.isComplete(id, value))
			{
				clientThread.invokeLater(() ->
				{
					sendDiaryCompletion(tier, id, "varbit", null);
					return true;
				});
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

		Matcher matcher = DIARY_COMPLETE.matcher(event.getMessage());
		if (!matcher.find())
		{
			return;
		}

		String tierDisplay = matcher.group(1);
		String region = matcher.group(2).trim();
		DiaryTier tier = findTier(region, tierDisplay);

		clientThread.invokeLater(() ->
		{
			DiaryTier resolved = tier != null
				? tier
				: DiaryTier.unknown(region, tierDisplay);
			Integer varbitId = tier != null ? tier.varbitId() : null;
			sendDiaryCompletion(resolved, varbitId, "chat", event.getMessage());
			return true;
		});
	}

	private void sendDiaryCompletion(DiaryTier tier, Integer varbitId, String detectionSource, String message)
	{
		String dedupKey = tier.region().toLowerCase() + "|" + tier.tierDisplay().toLowerCase();
		if (isDuplicate(dedupKey))
		{
			return;
		}

		client.runScript(DiaryDefinitions.COMPLETED_TASKS_SCRIPT_ID);
		int completedTasks = client.getIntStack()[0];

		client.runScript(DiaryDefinitions.TOTAL_TASKS_SCRIPT_ID);
		int totalTasks = client.getIntStack()[0];

		Map<String, Object> data = new HashMap<>();
		data.put("region", tier.region());
		data.put("tier", tier.tierDisplay());
		data.put("area", tier.region());
		data.put("difficulty", tier.tierDisplay());
		data.put("detectionSource", detectionSource);
		data.put("completedTasks", completedTasks);
		data.put("totalTasks", totalTasks);
		data.put("totalDiariesCompleted", getTotalCompleted());

		if (varbitId != null && varbitId >= 0)
		{
			data.put("varbitId", varbitId);
		}

		if (message != null)
		{
			data.put("message", message);
		}

		sendEvent(data);
	}

	private DiaryTier findTier(String region, String tierDisplay)
	{
		for (DiaryTier tier : DiaryDefinitions.varbitMap().values())
		{
			if (!tier.tierDisplay().equalsIgnoreCase(tierDisplay))
			{
				continue;
			}

			if (regionsMatch(tier.region(), region))
			{
				return tier;
			}
		}

		return null;
	}

	private static boolean regionsMatch(String definedRegion, String chatRegion)
	{
		if (definedRegion.equalsIgnoreCase(chatRegion))
		{
			return true;
		}

		String defined = definedRegion.toLowerCase();
		String chat = chatRegion.toLowerCase();
		return defined.startsWith(chat) || chat.startsWith(defined);
	}

	private void initializeDiaries()
	{
		if (!isEnabled())
		{
			return;
		}

		diaryCompletionById.clear();

		for (Integer varbitId : DiaryDefinitions.varbitMap().keySet())
		{
			int value = client.getVarbitValue(varbitId);
			if (value >= 0)
			{
				diaryCompletionById.put(varbitId, value);
			}
		}
	}

	private int getTotalCompleted()
	{
		int count = 0;
		for (Map.Entry<Integer, Integer> entry : diaryCompletionById.entrySet())
		{
			if (DiaryDefinitions.isComplete(entry.getKey(), entry.getValue()))
			{
				count++;
			}
		}
		return count;
	}

	private boolean isDuplicate(String dedupKey)
	{
		int tick = client.getTickCount();
		if (dedupKey.equals(lastSentKey) && tick - lastSentTick < DEDUP_TICKS)
		{
			return true;
		}

		lastSentKey = dedupKey;
		lastSentTick = tick;
		return false;
	}

	private void reset()
	{
		diaryCompletionById.clear();
		initDelayTicks = 0;
	}
}
