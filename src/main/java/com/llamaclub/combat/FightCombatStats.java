package com.llamaclub.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accumulated combat statistics for a boss fight or multi-phase run.
 */
public class FightCombatStats
{
	private int totalDamage;
	private int hitCount;
	private int specialAttacks;
	private final Map<String, Integer> damageByWeapon = new HashMap<>();
	private final Map<String, Integer> damageBySpecWeapon = new HashMap<>();

	public void addHit(String weaponName, int damage)
	{
		if (damage <= 0)
		{
			return;
		}

		totalDamage += damage;
		hitCount++;
		damageByWeapon.merge(weaponName, damage, Integer::sum);
	}

	public void addSpecHit(String weaponName, int damage)
	{
		if (damage <= 0)
		{
			return;
		}

		damageBySpecWeapon.merge(weaponName, damage, Integer::sum);
	}

	public void incrementSpecialAttacks()
	{
		specialAttacks++;
	}

	public void mergeFrom(FightCombatStats other)
	{
		if (other == null)
		{
			return;
		}

		totalDamage += other.totalDamage;
		hitCount += other.hitCount;
		specialAttacks += other.specialAttacks;
		other.damageByWeapon.forEach((weapon, damage) -> damageByWeapon.merge(weapon, damage, Integer::sum));
		other.damageBySpecWeapon.forEach((weapon, damage) -> damageBySpecWeapon.merge(weapon, damage, Integer::sum));
	}

	public int getTotalDamage()
	{
		return totalDamage;
	}

	public int getHitCount()
	{
		return hitCount;
	}

	public int getSpecialAttacks()
	{
		return specialAttacks;
	}

	public Map<String, Integer> getDamageByWeapon()
	{
		return new HashMap<>(damageByWeapon);
	}

	public Map<String, Integer> getDamageBySpecWeapon()
	{
		return new HashMap<>(damageBySpecWeapon);
	}

	public List<String> getWeaponsUsed()
	{
		Set<String> weapons = new LinkedHashSet<>(damageByWeapon.keySet());
		weapons.addAll(damageBySpecWeapon.keySet());
		return new ArrayList<>(weapons);
	}

	public boolean hasCombatData()
	{
		return totalDamage > 0 || hitCount > 0 || specialAttacks > 0;
	}

	public void clear()
	{
		totalDamage = 0;
		hitCount = 0;
		specialAttacks = 0;
		damageByWeapon.clear();
		damageBySpecWeapon.clear();
	}
}
