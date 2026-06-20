package com.llamaclub.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

@Singleton
public class QuestSync
{
	@Inject
	private Client client;

	public Map<String, Object> sync()
	{
		List<Map<String, Object>> quests = new ArrayList<>();
		int completed = 0;
		int total = 0;

		for (Quest quest : Quest.values())
		{
			if (quest.getName() == null || quest.getName().isEmpty())
			{
				continue;
			}

			total++;
			QuestState state = quest.getState(client);
			boolean isComplete = state == QuestState.FINISHED;
			if (isComplete)
			{
				completed++;
			}

			Map<String, Object> questData = new HashMap<>();
			questData.put("id", quest.getId());
			questData.put("name", quest.getName());
			questData.put("state", state.name());
			questData.put("completed", isComplete);
			quests.add(questData);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("completedCount", completed);
		result.put("totalCount", total);
		result.put("quests", quests);
		return result;
	}
}
