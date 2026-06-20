package com.llamaclub.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class BossKillCountSync
{
	@Inject
	private ConfigManager configManager;

	@Inject
	private CombatAchievementBossStatsReader combatAchievementBossStatsReader;

	public Map<String, Object> sync()
	{
		Map<String, Map<String, Object>> merged = new HashMap<>();
		String profile = configManager.getRSProfileKey();

		List<String> killCountKeys = configManager.getRSProfileConfigurationKeys("killcount", profile, "");
		if (killCountKeys != null)
		{
			for (String key : killCountKeys)
			{
				Integer killCount = configManager.getRSProfileConfiguration("killcount", key, int.class);
				if (killCount == null || killCount <= 0)
				{
					continue;
				}

				Double personalBestSeconds = configManager.getRSProfileConfiguration("personalbest", key, double.class);
				mergeBoss(merged, key, killCount, personalBestSeconds, "runelite_profile_config");
			}
		}

		mergePersonalBestOnlyBosses(merged, profile);

		try
		{
			for (CombatAchievementBossStat caStat : combatAchievementBossStatsReader.readAll().values())
			{
				String key = CombatAchievementBossStatsReader.bossKey(caStat.getName());
				mergeBoss(merged, key, caStat.getKillCount(), caStat.getPersonalBestSeconds(), "combat_achievements");
			}
		}
		catch (RuntimeException ex)
		{
			// Keep profile-config boss data even if CA enrichment fails.
		}

		List<Map<String, Object>> bosses = new ArrayList<>(merged.values());
		bosses.sort(Comparator.comparing(b -> String.valueOf(b.get("name"))));

		Map<String, Object> result = new HashMap<>();
		result.put("bosses", bosses);
		result.put("bossCount", bosses.size());
		result.put("source", "merged");
		return result;
	}

	/**
	 * Tiered Lunar Chest PBs (e.g. {@code lunar chest 1}) live only under {@code personalbest},
	 * not {@code killcount}. Include them so the website speed leaderboard can merge tiers.
	 */
	private void mergePersonalBestOnlyBosses(Map<String, Map<String, Object>> merged, String profile)
	{
		List<String> personalBestKeys = configManager.getRSProfileConfigurationKeys("personalbest", profile, "");
		if (personalBestKeys == null)
		{
			return;
		}

		for (String key : personalBestKeys)
		{
			if (merged.containsKey(key))
			{
				continue;
			}

			Double personalBestSeconds = configManager.getRSProfileConfiguration("personalbest", key, double.class);
			if (personalBestSeconds == null || personalBestSeconds <= 0)
			{
				continue;
			}

			mergeBoss(merged, key, null, personalBestSeconds, "runelite_profile_config");
		}
	}

	private static void mergeBoss(
		Map<String, Map<String, Object>> merged,
		String key,
		Integer killCount,
		Double personalBestSeconds,
		String source
	)
	{
		Map<String, Object> boss = merged.computeIfAbsent(key, ignored -> new HashMap<>());
		boss.put("name", CombatAchievementBossStatsReader.displayName(key));

		if (killCount != null && killCount > 0)
		{
			int existingKc = 0;
			if (boss.containsKey("killCount"))
			{
				Object existing = boss.get("killCount");
				if (existing instanceof Number)
				{
					existingKc = ((Number) existing).intValue();
				}
			}

			if (killCount >= existingKc)
			{
				boss.put("killCount", killCount);
			}
		}

		if (personalBestSeconds != null && personalBestSeconds > 0)
		{
			Double existingPb = null;
			if (boss.containsKey("personalBestSeconds"))
			{
				Object existing = boss.get("personalBestSeconds");
				if (existing instanceof Number)
				{
					existingPb = ((Number) existing).doubleValue();
				}
			}

			if ("combat_achievements".equals(source) || existingPb == null || personalBestSeconds < existingPb)
			{
				boss.put("personalBestSeconds", personalBestSeconds);
				boss.put("personalBest", BossTimeFormat.formatSeconds(personalBestSeconds));
				boss.put("personalBestSource", source);
			}
		}
	}
}
