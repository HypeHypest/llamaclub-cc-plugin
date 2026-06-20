package com.llamaclub.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class CombatAchievementBossStatsReader
{
	static final String CONFIG_GROUP = "cabossstats";

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	public Map<String, CombatAchievementBossStat> readAll()
	{
		Map<String, CombatAchievementBossStat> stats = new HashMap<>();
		try
		{
			mergeStats(stats, readCachedWidgetStats());
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				mergeStats(stats, readFromHiscoresVarps());
			}
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to read combat achievement boss stats", ex);
		}
		return stats;
	}

	public boolean hasAuthoritativePersonalBest(String bossName)
	{
		if (bossName == null || bossName.isEmpty())
		{
			return false;
		}

		String key = bossKey(bossName);
		Double cachedPb = configManager.getRSProfileConfiguration(CONFIG_GROUP, key + ".pb", double.class);
		if (cachedPb != null && cachedPb > 0)
		{
			return true;
		}

		Double directPb = readDirectPersonalBestSeconds(bossName);
		return directPb != null && directPb > 0;
	}

	void cacheWidgetStats(String bossName, Integer killCount, Double personalBestSeconds)
	{
		if (bossName == null || bossName.isEmpty())
		{
			return;
		}

		String key = bossKey(bossName);
		String profile = configManager.getRSProfileKey();

		if (killCount != null && killCount > 0)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key + ".kc", killCount);
		}

		if (personalBestSeconds != null && personalBestSeconds > 0)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key + ".pb", personalBestSeconds);
		}
	}

	private Map<String, CombatAchievementBossStat> readCachedWidgetStats()
	{
		Map<String, CombatAchievementBossStat> stats = new HashMap<>();
		String profile = configManager.getRSProfileKey();
		List<String> keys = configManager.getRSProfileConfigurationKeys(CONFIG_GROUP, profile, "");
		if (keys == null || keys.isEmpty())
		{
			return stats;
		}

		Set<String> bossKeys = new HashSet<>();
		for (String key : keys)
		{
			if (key.endsWith(".kc"))
			{
				bossKeys.add(key.substring(0, key.length() - 3));
			}
			else if (key.endsWith(".pb"))
			{
				bossKeys.add(key.substring(0, key.length() - 3));
			}
		}

		for (String bossKey : bossKeys)
		{
			Integer killCount = configManager.getRSProfileConfiguration(CONFIG_GROUP, bossKey + ".kc", int.class);
			Double personalBestSeconds = configManager.getRSProfileConfiguration(CONFIG_GROUP, bossKey + ".pb", double.class);

			if ((killCount == null || killCount <= 0) && (personalBestSeconds == null || personalBestSeconds <= 0))
			{
				continue;
			}

			stats.put(bossKey, new CombatAchievementBossStat(displayName(bossKey), killCount, personalBestSeconds));
		}

		return stats;
	}

	private Map<String, CombatAchievementBossStat> readFromHiscoresVarps()
	{
		Map<String, CombatAchievementBossStat> stats = new HashMap<>();

		List<Integer> rowIds;
		try
		{
			rowIds = client.getDBTableRows(DBTableID.HiscoresBossesInfo.ID);
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read HiscoresBossesInfo rows", ex);
			return stats;
		}

		if (rowIds == null)
		{
			return stats;
		}

		for (int rowId : rowIds)
		{
			try
			{
				String bossName = readStringField(rowId, DBTableID.HiscoresBossesInfo.COL_BOSSNAME);
				Integer killVarp = readIntField(rowId, DBTableID.HiscoresBossesInfo.COL_BOSSVARP);

				if (bossName == null || bossName.isEmpty() || killVarp == null || killVarp <= 0)
				{
					continue;
				}

				Integer killCount = null;
				try
				{
					int kc = client.getVarpValue(killVarp);
					if (kc > 0)
					{
						killCount = kc;
					}
				}
				catch (RuntimeException ex)
				{
					log.debug("Unable to read kill varp {} for {}", killVarp, bossName, ex);
				}

				Double personalBestSeconds = readPersonalBestSeconds(rowId);

				if (killCount == null && personalBestSeconds == null)
				{
					continue;
				}

				String key = bossKey(bossName);
				stats.put(key, new CombatAchievementBossStat(bossName, killCount, personalBestSeconds));
			}
			catch (RuntimeException ex)
			{
				log.debug("Unable to read HiscoresBossesInfo row {}", rowId, ex);
			}
		}

		return stats;
	}

	private Double readDirectPersonalBestSeconds(String bossName)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		String key = bossKey(bossName);
		if ("brutus".equals(key))
		{
			return readTenthsVarp(VarPlayerID.COWBOSS_MISC);
		}

		if (bossKey("Grotesque Guardians").equals(key))
		{
			return readTenthsVarbit(VarbitID.GARGBOSS_FASTESTKILL);
		}

		return null;
	}

	private Double readPersonalBestSeconds(int rowId)
	{
		if (rowId == DBTableID.HiscoresBossesInfo.Row.HISCORES_BOSSES_COWBOSS)
		{
			return readTenthsVarp(VarPlayerID.COWBOSS_MISC);
		}

		if (rowId == DBTableID.HiscoresBossesInfo.Row.HISCORES_BOSSES_GROTESQUE_GUARDIANS)
		{
			return readTenthsVarbit(VarbitID.GARGBOSS_FASTESTKILL);
		}

		return null;
	}

	private Double readTenthsVarp(int varpId)
	{
		try
		{
			int raw = client.getVarpValue(varpId);
			if (raw <= 0)
			{
				return null;
			}

			return raw / 10D;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read varp {}", varpId, ex);
			return null;
		}
	}

	private Double readTenthsVarbit(int varbitId)
	{
		try
		{
			int raw = client.getVarbitValue(varbitId);
			if (raw <= 0)
			{
				return null;
			}

			return raw / 10D;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read varbit {}", varbitId, ex);
			return null;
		}
	}

	private String readStringField(int rowId, int column)
	{
		Object[] field = client.getDBTableField(DBTableID.HiscoresBossesInfo.ID, rowId, column);
		if (field == null || field.length == 0 || field[0] == null)
		{
			return null;
		}

		return String.valueOf(field[0]).trim();
	}

	private Integer readIntField(int rowId, int column)
	{
		Object[] field = client.getDBTableField(DBTableID.HiscoresBossesInfo.ID, rowId, column);
		if (field == null || field.length == 0 || field[0] == null)
		{
			return null;
		}

		if (field[0] instanceof Number)
		{
			return ((Number) field[0]).intValue();
		}

		try
		{
			return Integer.parseInt(String.valueOf(field[0]).trim());
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static void mergeStats(Map<String, CombatAchievementBossStat> target, Map<String, CombatAchievementBossStat> source)
	{
		for (Map.Entry<String, CombatAchievementBossStat> entry : source.entrySet())
		{
			CombatAchievementBossStat existing = target.get(entry.getKey());
			target.put(entry.getKey(), existing == null ? entry.getValue() : existing.merge(entry.getValue()));
		}
	}

	static String bossKey(String boss)
	{
		return boss.toLowerCase().replace(":", ".");
	}

	static String displayName(String key)
	{
		if (key == null || key.isEmpty())
		{
			return key;
		}

		if ("lunar chest 1".equals(key))
		{
			return "Lunar Chest (1)";
		}

		if ("lunar chest 2".equals(key))
		{
			return "Lunar Chest (2)";
		}

		if ("lunar chest 3".equals(key))
		{
			return "Lunar Chest (3)";
		}

		String[] words = key.replace('.', ':').split(" ");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			if (i > 0)
			{
				sb.append(' ');
			}

			String word = words[i];
			if (!word.isEmpty())
			{
				sb.append(Character.toUpperCase(word.charAt(0)));
				if (word.length() > 1)
				{
					sb.append(word.substring(1));
				}
			}
		}

		return sb.toString();
	}
}
