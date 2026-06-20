/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.diary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarbitID;

public final class DiaryDefinitions
{
	public static final int COMPLETED_TASKS_SCRIPT_ID = 3971;
	public static final int TOTAL_TASKS_SCRIPT_ID = 3980;

	private static final Map<Integer, DiaryTier> VARBIT_TO_TIER = buildVarbitMap();
	private static final Map<String, List<DiaryTier>> SYNC_TIER_GROUPS = buildSyncTierGroups();

	private DiaryDefinitions()
	{
	}

	public static Map<Integer, DiaryTier> varbitMap()
	{
		return VARBIT_TO_TIER;
	}

	public static Map<String, List<DiaryTier>> syncTierGroups()
	{
		return SYNC_TIER_GROUPS;
	}

	public static boolean isComplete(int varbitId, int value)
	{
		if (varbitId == Varbits.DIARY_KARAMJA_EASY
			|| varbitId == Varbits.DIARY_KARAMJA_MEDIUM
			|| varbitId == Varbits.DIARY_KARAMJA_HARD)
		{
			return value > 1;
		}

		return value > 0;
	}

	private static Map<Integer, DiaryTier> buildVarbitMap()
	{
		Map<Integer, DiaryTier> map = new HashMap<>();
		addRegion(map, "Ardougne",
			tier("easy", "Easy", Varbits.DIARY_ARDOUGNE_EASY, VarbitID.ARDOUGNE_EASY_COUNT, 10),
			tier("medium", "Medium", Varbits.DIARY_ARDOUGNE_MEDIUM, VarbitID.ARDOUGNE_MED_COUNT, 12),
			tier("hard", "Hard", Varbits.DIARY_ARDOUGNE_HARD, VarbitID.ARDOUGNE_HARD_COUNT, 12),
			tier("elite", "Elite", Varbits.DIARY_ARDOUGNE_ELITE, VarbitID.ARDOUGNE_ELITE_COUNT, 8));
		addRegion(map, "Desert",
			tier("easy", "Easy", Varbits.DIARY_DESERT_EASY, VarbitID.DESERT_EASY_COUNT, 11),
			tier("medium", "Medium", Varbits.DIARY_DESERT_MEDIUM, VarbitID.DESERT_MED_COUNT, 12),
			tier("hard", "Hard", Varbits.DIARY_DESERT_HARD, VarbitID.DESERT_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_DESERT_ELITE, VarbitID.DESERT_ELITE_COUNT, 6));
		addRegion(map, "Falador",
			tier("easy", "Easy", Varbits.DIARY_FALADOR_EASY, VarbitID.FALADOR_EASY_COUNT, 11),
			tier("medium", "Medium", Varbits.DIARY_FALADOR_MEDIUM, VarbitID.FALADOR_MED_COUNT, 14),
			tier("hard", "Hard", Varbits.DIARY_FALADOR_HARD, VarbitID.FALADOR_HARD_COUNT, 11),
			tier("elite", "Elite", Varbits.DIARY_FALADOR_ELITE, VarbitID.FALADOR_ELITE_COUNT, 6));
		addRegion(map, "Fremennik",
			tier("easy", "Easy", Varbits.DIARY_FREMENNIK_EASY, VarbitID.FREMENNIK_EASY_COUNT, 10),
			tier("medium", "Medium", Varbits.DIARY_FREMENNIK_MEDIUM, VarbitID.FREMENNIK_MED_COUNT, 9),
			tier("hard", "Hard", Varbits.DIARY_FREMENNIK_HARD, VarbitID.FREMENNIK_HARD_COUNT, 9),
			tier("elite", "Elite", Varbits.DIARY_FREMENNIK_ELITE, VarbitID.FREMENNIK_ELITE_COUNT, 6));
		addRegion(map, "Kandarin",
			tier("easy", "Easy", Varbits.DIARY_KANDARIN_EASY, VarbitID.KANDARIN_EASY_COUNT, 11),
			tier("medium", "Medium", Varbits.DIARY_KANDARIN_MEDIUM, VarbitID.KANDARIN_MED_COUNT, 14),
			tier("hard", "Hard", Varbits.DIARY_KANDARIN_HARD, VarbitID.KANDARIN_HARD_COUNT, 11),
			tier("elite", "Elite", Varbits.DIARY_KANDARIN_ELITE, VarbitID.KANDARIN_ELITE_COUNT, 7));
		addRegion(map, "Karamja",
			tier("easy", "Easy", Varbits.DIARY_KARAMJA_EASY, VarbitID.KARAMJA_EASY_COUNT, 10),
			tier("medium", "Medium", Varbits.DIARY_KARAMJA_MEDIUM, VarbitID.KARAMJA_MED_COUNT, 19),
			tier("hard", "Hard", Varbits.DIARY_KARAMJA_HARD, VarbitID.KARAMJA_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_KARAMJA_ELITE, VarbitID.KARAMJA_ELITE_COUNT, 5));
		addRegion(map, "Kourend & Kebos",
			tier("easy", "Easy", Varbits.DIARY_KOUREND_EASY, VarbitID.KOUREND_EASY_COUNT, 12),
			tier("medium", "Medium", Varbits.DIARY_KOUREND_MEDIUM, VarbitID.KOUREND_MED_COUNT, 13),
			tier("hard", "Hard", Varbits.DIARY_KOUREND_HARD, VarbitID.KOUREND_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_KOUREND_ELITE, VarbitID.KOUREND_ELITE_COUNT, 8));
		addRegion(map, "Lumbridge & Draynor",
			tier("easy", "Easy", Varbits.DIARY_LUMBRIDGE_EASY, VarbitID.LUMBRIDGE_EASY_COUNT, 12),
			tier("medium", "Medium", Varbits.DIARY_LUMBRIDGE_MEDIUM, VarbitID.LUMBRIDGE_MED_COUNT, 12),
			tier("hard", "Hard", Varbits.DIARY_LUMBRIDGE_HARD, VarbitID.LUMBRIDGE_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_LUMBRIDGE_ELITE, VarbitID.LUMBRIDGE_ELITE_COUNT, 6));
		addRegion(map, "Morytania",
			tier("easy", "Easy", Varbits.DIARY_MORYTANIA_EASY, VarbitID.MORYTANIA_EASY_COUNT, 11),
			tier("medium", "Medium", Varbits.DIARY_MORYTANIA_MEDIUM, VarbitID.MORYTANIA_MED_COUNT, 11),
			tier("hard", "Hard", Varbits.DIARY_MORYTANIA_HARD, VarbitID.MORYTANIA_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_MORYTANIA_ELITE, VarbitID.MORYTANIA_ELITE_COUNT, 6));
		addRegion(map, "Varrock",
			tier("easy", "Easy", Varbits.DIARY_VARROCK_EASY, VarbitID.VARROCK_EASY_COUNT, 14),
			tier("medium", "Medium", Varbits.DIARY_VARROCK_MEDIUM, VarbitID.VARROCK_MED_COUNT, 13),
			tier("hard", "Hard", Varbits.DIARY_VARROCK_HARD, VarbitID.VARROCK_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_VARROCK_ELITE, VarbitID.VARROCK_ELITE_COUNT, 5));
		addRegion(map, "Western Provinces",
			tier("easy", "Easy", Varbits.DIARY_WESTERN_EASY, VarbitID.WESTERN_EASY_COUNT, 11),
			tier("medium", "Medium", Varbits.DIARY_WESTERN_MEDIUM, VarbitID.WESTERN_MED_COUNT, 13),
			tier("hard", "Hard", Varbits.DIARY_WESTERN_HARD, VarbitID.WESTERN_HARD_COUNT, 13),
			tier("elite", "Elite", Varbits.DIARY_WESTERN_ELITE, VarbitID.WESTERN_ELITE_COUNT, 7));
		addRegion(map, "Wilderness",
			tier("easy", "Easy", Varbits.DIARY_WILDERNESS_EASY, VarbitID.WILDERNESS_EASY_COUNT, 12),
			tier("medium", "Medium", Varbits.DIARY_WILDERNESS_MEDIUM, VarbitID.WILDERNESS_MED_COUNT, 11),
			tier("hard", "Hard", Varbits.DIARY_WILDERNESS_HARD, VarbitID.WILDERNESS_HARD_COUNT, 10),
			tier("elite", "Elite", Varbits.DIARY_WILDERNESS_ELITE, VarbitID.WILDERNESS_ELITE_COUNT, 7));
		return Collections.unmodifiableMap(map);
	}

	private static DiaryTier tier(
		String tierKey,
		String tierDisplay,
		int completionVarbitId,
		int countVarbitId,
		int totalTasks
	)
	{
		return new DiaryTier(tierKey, tierDisplay, completionVarbitId, countVarbitId, totalTasks);
	}

	private static void addRegion(Map<Integer, DiaryTier> map, String region, DiaryTier... tiers)
	{
		for (DiaryTier tier : tiers)
		{
			map.put(tier.varbitId(), tier.withRegion(region));
		}
	}

	private static Map<String, List<DiaryTier>> buildSyncTierGroups()
	{
		Map<String, List<DiaryTier>> regions = new LinkedHashMap<>();
		for (DiaryTier tier : VARBIT_TO_TIER.values())
		{
			regions.computeIfAbsent(tier.region(), r -> new ArrayList<>()).add(tier);
		}

		Map<String, List<DiaryTier>> immutable = new LinkedHashMap<>();
		for (Map.Entry<String, List<DiaryTier>> entry : regions.entrySet())
		{
			immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		return Collections.unmodifiableMap(immutable);
	}

	public static final class DiaryTier
	{
		private final String region;
		private final String tierKey;
		private final String tierDisplay;
		private final int varbitId;
		private final int countVarbitId;
		private final int totalTasks;

		private DiaryTier(
			String tierKey,
			String tierDisplay,
			int varbitId,
			int countVarbitId,
			int totalTasks
		)
		{
			this(null, tierKey, tierDisplay, varbitId, countVarbitId, totalTasks);
		}

		private DiaryTier(
			String region,
			String tierKey,
			String tierDisplay,
			int varbitId,
			int countVarbitId,
			int totalTasks
		)
		{
			this.region = region;
			this.tierKey = tierKey;
			this.tierDisplay = tierDisplay;
			this.varbitId = varbitId;
			this.countVarbitId = countVarbitId;
			this.totalTasks = totalTasks;
		}

		private DiaryTier withRegion(String regionName)
		{
			return new DiaryTier(regionName, tierKey, tierDisplay, varbitId, countVarbitId, totalTasks);
		}

		public String region()
		{
			return region;
		}

		public String tierKey()
		{
			return tierKey;
		}

		public String tierDisplay()
		{
			return tierDisplay;
		}

		public int varbitId()
		{
			return varbitId;
		}

		public int countVarbitId()
		{
			return countVarbitId;
		}

		public int totalTasks()
		{
			return totalTasks;
		}

		public static DiaryTier unknown(String region, String tierDisplay)
		{
			return new DiaryTier(region, tierDisplay.toLowerCase(), tierDisplay, -1, -1, 0);
		}
	}
}
