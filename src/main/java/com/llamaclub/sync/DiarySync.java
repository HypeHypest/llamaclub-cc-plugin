/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.sync;

import com.llamaclub.diary.DiaryDefinitions;
import com.llamaclub.diary.DiaryDefinitions.DiaryTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

@Singleton
public class DiarySync
{
	@Inject
	private Client client;

	public Map<String, Object> sync()
	{
		List<Map<String, Object>> diaries = new ArrayList<>();

		for (Map.Entry<String, List<DiaryTier>> regionEntry : DiaryDefinitions.syncTierGroups().entrySet())
		{
			Map<String, Object> diaryData = new HashMap<>();
			diaryData.put("region", regionEntry.getKey());

			for (DiaryTier tier : regionEntry.getValue())
			{
				int completionValue = client.getVarbitValue(tier.varbitId());
				boolean complete = DiaryDefinitions.isComplete(tier.varbitId(), completionValue);
				int completed = client.getVarbitValue(tier.countVarbitId());

				if (completed < 0)
				{
					completed = 0;
				}

				if (complete)
				{
					completed = tier.totalTasks();
				}
				else
				{
					completed = Math.min(completed, tier.totalTasks());
				}

				Map<String, Object> progress = new HashMap<>();
				progress.put("complete", complete);
				progress.put("completed", completed);
				progress.put("total", tier.totalTasks());
				diaryData.put(tier.tierKey(), progress);
			}

			diaries.add(diaryData);
		}

		client.runScript(DiaryDefinitions.COMPLETED_TASKS_SCRIPT_ID);
		int completedTasks = client.getIntStack()[0];
		client.runScript(DiaryDefinitions.TOTAL_TASKS_SCRIPT_ID);
		int totalTasks = client.getIntStack()[0];

		Map<String, Object> result = new HashMap<>();
		result.put("diaries", diaries);
		result.put("completedTasks", completedTasks);
		result.put("totalTasks", totalTasks);
		return result;
	}
}
