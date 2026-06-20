/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.util.QuestUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class QuestNotifier extends BaseNotifier
{
	private static final int DEDUP_TICKS = 15;

	@Inject
	private ClientThread clientThread;

	private String lastSentQuestKey = null;
	private int lastSentTick = -1000;

	@Override
	public boolean isEnabled()
	{
		return config.notifyQuest();
	}

	@Override
	protected String getEventKind()
	{
		return "QUEST_COMPLETED";
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!isEnabled() || event.getGroupId() != InterfaceID.QUESTSCROLL)
		{
			return;
		}

		Widget questTitle = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
		if (questTitle == null)
		{
			return;
		}

		String questText = questTitle.getText();
		clientThread.invokeLater(() -> handleQuestCompletion(questText, "scroll"));
	}

	private void handleQuestCompletion(String questText, String detectionSource)
	{
		String questName = QuestUtils.parseQuestWidget(questText);
		if (questName == null || questName.isEmpty() || isDuplicate(questName))
		{
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("questName", questName);
		data.put("questPoints", client.getVarpValue(VarPlayerID.QP));
		data.put("completedQuests", client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT));
		data.put("totalQuests", client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT));
		data.put("detectionSource", detectionSource);
		if (questText != null)
		{
			data.put("message", questText);
		}
		sendEvent(data);
	}

	private boolean isDuplicate(String questName)
	{
		String questKey = QuestUtils.normalizeQuestKey(questName);
		int tick = client.getTickCount();
		if (lastSentQuestKey != null
			&& lastSentQuestKey.equals(questKey)
			&& tick - lastSentTick < DEDUP_TICKS)
		{
			return true;
		}

		lastSentQuestKey = questKey;
		lastSentTick = tick;
		return false;
	}
}
