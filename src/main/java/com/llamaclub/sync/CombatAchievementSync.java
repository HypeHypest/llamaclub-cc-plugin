/*
 * Portions of this file are derived from or inspired by the reval-cc plugin
 * Copyright (c) 2025, Lightroom
 * Licensed under the BSD 2-Clause License
 * See LICENSES/reval-cc-LICENSE.txt for full license text
 */
package com.llamaclub.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

@Singleton
public class CombatAchievementSync
{
	private static final Map<Integer, String> TIER_ENUMS = new LinkedHashMap<>();

	static
	{
		TIER_ENUMS.put(3981, "Easy");
		TIER_ENUMS.put(3982, "Medium");
		TIER_ENUMS.put(3983, "Hard");
		TIER_ENUMS.put(3984, "Elite");
		TIER_ENUMS.put(3985, "Master");
		TIER_ENUMS.put(3986, "Grandmaster");
	}

	private static final int[] COMPLETION_VARPS = {
		3116, 3117, 3118, 3119, 3120, 3121, 3122, 3123, 3124, 3125,
		3126, 3127, 3128, 3387, 3718, 3773, 3774, 4204, 4496, 4721
	};

	private static final int FIELD_NAME = 1308;
	private static final int FIELD_TASK_ID = 1306;

	@Inject
	private Client client;

	public Map<String, Object> sync()
	{
		List<Map<String, Object>> tasks = new ArrayList<>();
		int completedCount = 0;
		int totalPoints = 0;

		for (Map.Entry<Integer, String> tierEntry : TIER_ENUMS.entrySet())
		{
			EnumComposition tierEnum = client.getEnum(tierEntry.getKey());
			if (tierEnum == null)
			{
				continue;
			}

			for (int structId : tierEnum.getIntVals())
			{
				Map<String, Object> task = loadTask(structId, tierEntry.getValue());
				if (task == null)
				{
					continue;
				}

				tasks.add(task);
				if (Boolean.TRUE.equals(task.get("completed")))
				{
					completedCount++;
					totalPoints += (int) task.get("points");
				}
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("completedTasks", completedCount);
		result.put("totalTasks", tasks.size());
		result.put("totalPoints", totalPoints);
		result.put("currentTier", calculateCurrentTier(totalPoints));
		result.put("tierProgress", buildTierProgress(tasks));
		result.put("tasks", tasks);
		return result;
	}

	private Map<String, Object> loadTask(int structId, String tier)
	{
		StructComposition struct = client.getStructComposition(structId);
		if (struct == null)
		{
			return null;
		}

		int taskId = struct.getIntValue(FIELD_TASK_ID);
		String name = struct.getStringValue(FIELD_NAME);
		if (name == null || name.isEmpty())
		{
			return null;
		}

		boolean completed = isTaskCompleted(taskId);
		int points = pointsForTier(tier);

		Map<String, Object> task = new HashMap<>();
		task.put("id", taskId);
		task.put("name", name);
		task.put("tier", tier);
		task.put("points", points);
		task.put("completed", completed);
		return task;
	}

	private boolean isTaskCompleted(int taskId)
	{
		if (taskId < 0 || taskId >= COMPLETION_VARPS.length * 32)
		{
			return false;
		}

		int varpIndex = taskId / 32;
		int bitIndex = taskId % 32;
		if (varpIndex >= COMPLETION_VARPS.length)
		{
			return false;
		}

		int varpValue = client.getVarpValue(COMPLETION_VARPS[varpIndex]);
		return (varpValue & (1 << bitIndex)) != 0;
	}

	private static int pointsForTier(String tier)
	{
		switch (tier.toLowerCase())
		{
			case "easy":
				return 1;
			case "medium":
				return 2;
			case "hard":
				return 3;
			case "elite":
				return 4;
			case "master":
				return 5;
			case "grandmaster":
				return 6;
			default:
				return 1;
		}
	}

	private static String calculateCurrentTier(int totalPoints)
	{
		if (totalPoints >= 2630)
		{
			return "Grandmaster";
		}
		if (totalPoints >= 1904)
		{
			return "Master";
		}
		if (totalPoints >= 1064)
		{
			return "Elite";
		}
		if (totalPoints >= 416)
		{
			return "Hard";
		}
		if (totalPoints >= 161)
		{
			return "Medium";
		}
		if (totalPoints >= 41)
		{
			return "Easy";
		}
		return "None";
	}

	private static Map<String, Object> buildTierProgress(List<Map<String, Object>> tasks)
	{
		Map<String, Object> tierProgress = new LinkedHashMap<>();
		for (String tier : TIER_ENUMS.values())
		{
			int completed = 0;
			int total = 0;
			for (Map<String, Object> task : tasks)
			{
				if (!tier.equals(task.get("tier")))
				{
					continue;
				}
				total++;
				if (Boolean.TRUE.equals(task.get("completed")))
				{
					completed++;
				}
			}

			Map<String, Object> tierData = new HashMap<>();
			tierData.put("completed", completed);
			tierData.put("total", total);
			tierProgress.put(tier.toLowerCase(), tierData);
		}
		return tierProgress;
	}
}
