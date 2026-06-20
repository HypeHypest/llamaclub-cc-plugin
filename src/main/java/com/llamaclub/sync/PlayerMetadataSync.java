package com.llamaclub.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.WorldType;

@Singleton
public class PlayerMetadataSync
{
	@Inject
	private Client client;

	public Map<String, Object> sync()
	{
		Map<String, Object> metadata = new HashMap<>();
		Set<WorldType> worldTypes = client.getWorldType();
		if (worldTypes != null && !worldTypes.isEmpty())
		{
			metadata.put("worldTypes", worldTypes.stream().map(Enum::name).toArray(String[]::new));
		}

		String accountType = resolveAccountType(client.getVarbitValue(Varbits.ACCOUNT_TYPE));
		if (accountType != null)
		{
			metadata.put("accountType", accountType);
		}

		metadata.put("combatLevel", resolveCombatLevel());
		metadata.put("totalLevel", client.getTotalLevel());
		metadata.put("totalExperience", client.getOverallExperience());

		Map<String, Object> skills = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			Map<String, Object> skillData = new HashMap<>();
			skillData.put("level", client.getRealSkillLevel(skill));
			skillData.put("experience", client.getSkillExperience(skill));
			skills.put(skill.getName().toLowerCase(), skillData);
		}
		metadata.put("skills", skills);

		return metadata;
	}

	private int resolveCombatLevel()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			return localPlayer.getCombatLevel();
		}

		return Experience.getCombatLevel(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.PRAYER)
		);
	}

	private static String resolveAccountType(int accountType)
	{
		switch (accountType)
		{
			case 1:
				return "IRONMAN";
			case 2:
				return "ULTIMATE_IRONMAN";
			case 3:
				return "HARDCORE_IRONMAN";
			case 4:
				return "GROUP_IRONMAN";
			case 5:
				return "HARDCORE_GROUP_IRONMAN";
			case 6:
				return "UNRANKED_GROUP_IRONMAN";
			default:
				return "NORMAL";
		}
	}
}
