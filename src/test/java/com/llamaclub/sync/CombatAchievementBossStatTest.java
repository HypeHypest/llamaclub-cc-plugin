package com.llamaclub.sync;

import org.junit.Assert;
import org.junit.Test;

public class CombatAchievementBossStatTest
{
	@Test
	public void mergePrefersHigherKillCountAndFasterPersonalBest()
	{
		CombatAchievementBossStat widgetStats = new CombatAchievementBossStat("Brutus", 5, 4.2);
		CombatAchievementBossStat chatStats = new CombatAchievementBossStat("Brutus", 5, 29.4);

		CombatAchievementBossStat merged = widgetStats.merge(chatStats);
		Assert.assertEquals(Integer.valueOf(5), merged.getKillCount());
		Assert.assertEquals(4.2, merged.getPersonalBestSeconds(), 0.01);
	}
}
