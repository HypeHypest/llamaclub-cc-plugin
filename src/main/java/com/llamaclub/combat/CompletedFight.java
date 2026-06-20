package com.llamaclub.combat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompletedFight
{
	private final String bossKey;
	private final Duration duration;
	private final FightCombatStats stats;
	private final long completedAtMillis;

	public CompletedFight(String bossKey, Duration duration, FightCombatStats stats, long completedAtMillis)
	{
		this.bossKey = bossKey;
		this.duration = duration;
		this.stats = stats;
		this.completedAtMillis = completedAtMillis;
	}

	public String getBossKey()
	{
		return bossKey;
	}

	public Duration getDuration()
	{
		return duration;
	}

	public FightCombatStats getStats()
	{
		return stats;
	}

	public long getCompletedAtMillis()
	{
		return completedAtMillis;
	}

	public Map<String, Object> toKillCountPayloadFields()
	{
		Map<String, Object> fields = new HashMap<>();
		if (stats == null)
		{
			return fields;
		}

		fields.put("totalDamage", stats.getTotalDamage());
		fields.put("hitCount", stats.getHitCount());
		fields.put("specialAttacks", stats.getSpecialAttacks());
		fields.put("damageByWeapon", stats.getDamageByWeapon());
		fields.put("damageBySpecWeapon", stats.getDamageBySpecWeapon());
		fields.put("weaponsUsed", stats.getWeaponsUsed());
		return fields;
	}

	public List<String> getWeaponsUsed()
	{
		return stats != null ? stats.getWeaponsUsed() : List.of();
	}
}
