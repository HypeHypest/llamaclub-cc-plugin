package com.llamaclub.sync;

final class CombatAchievementBossStat
{
	private final String name;
	private final Integer killCount;
	private final Double personalBestSeconds;

	CombatAchievementBossStat(String name, Integer killCount, Double personalBestSeconds)
	{
		this.name = name;
		this.killCount = killCount;
		this.personalBestSeconds = personalBestSeconds;
	}

	String getName()
	{
		return name;
	}

	Integer getKillCount()
	{
		return killCount;
	}

	Double getPersonalBestSeconds()
	{
		return personalBestSeconds;
	}

	CombatAchievementBossStat merge(CombatAchievementBossStat other)
	{
		if (other == null)
		{
			return this;
		}

		Integer mergedKc = killCount;
		if (other.killCount != null && other.killCount > 0)
		{
			mergedKc = mergedKc == null ? other.killCount : Math.max(mergedKc, other.killCount);
		}

		Double mergedPb = personalBestSeconds;
		if (other.personalBestSeconds != null && other.personalBestSeconds > 0)
		{
			mergedPb = mergedPb == null
				? other.personalBestSeconds
				: Math.min(mergedPb, other.personalBestSeconds);
		}

		return new CombatAchievementBossStat(name, mergedKc, mergedPb);
	}
}
