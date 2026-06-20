package com.llamaclub.sync;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlayerDataCollector
{
	@Inject
	private PlayerMetadataSync playerMetadataSync;

	@Inject
	private QuestSync questSync;

	@Inject
	private DiarySync diarySync;

	@Inject
	private CombatAchievementSync combatAchievementSync;

	@Inject
	private CollectionLogSync collectionLogSync;

	@Inject
	private BossKillCountSync bossKillCountSync;

	public Map<String, Object> collectAll()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("metadata", playerMetadataSync.sync());
		data.put("quests", questSync.sync());
		data.put("diaries", diarySync.sync());
		data.put("combatAchievements", combatAchievementSync.sync());
		data.put("collectionLog", collectionLogSync.sync());
		data.put("bossKillCounts", bossKillCountSync.sync());
		return data;
	}
}
