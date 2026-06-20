/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class LevelNotifier extends BaseNotifier
{
	static final long EXPERIENCE_BATCH_MS = 60_000L;
	/** Ticks to wait after first login so skill XP is fully hydrated before tracking gains. */
	static final int LOGIN_SYNC_GRACE_TICKS = 4;

	private final Map<Skill, Integer> previousXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> pendingXpGain = new EnumMap<>(Skill.class);
	private long lastExperienceFlush = 0L;
	private boolean baselinesReady = false;
	private int loginGraceTicksRemaining = 0;
	private final Set<Skill> hydratedSkills = EnumSet.noneOf(Skill.class);

	@Inject
	public LevelNotifier()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			previousXp.put(skill, -1);
			pendingXpGain.put(skill, 0);
		}
	}

	@Override
	public boolean isEnabled()
	{
		return config.notifyLevel() || config.notifyExperience();
	}

	@Override
	protected String getEventKind()
	{
		return "LEVEL_UP";
	}

	/**
	 * Clears tracking state when the plugin is started or stopped.
	 */
	public void resetSession()
	{
		baselinesReady = false;
		loginGraceTicksRemaining = 0;
		lastExperienceFlush = 0L;
		hydratedSkills.clear();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			pendingXpGain.put(skill, 0);
		}
	}

	/**
	 * Waits for skill XP to hydrate after login (or plugin start while already logged in)
	 * before treating stat changes as gameplay gains.
	 */
	public void beginLoginHydration()
	{
		baselinesReady = false;
		loginGraceTicksRemaining = LOGIN_SYNC_GRACE_TICKS;
		hydratedSkills.clear();
	}

	public void syncBaselines()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long now = System.currentTimeMillis();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			int xp = client.getSkillExperience(skill);
			previousXp.put(skill, xp);
			pendingXpGain.put(skill, 0);
			if (xp > 0)
			{
				hydratedSkills.add(skill);
			}
		}
		lastExperienceFlush = now;
		baselinesReady = true;
	}

	/**
	 * Sends any accumulated experience gains immediately (e.g. on logout).
	 */
	public void flushPendingExperience()
	{
		if (!config.notifyExperience() || !baselinesReady || !hasPendingExperience(pendingXpGain))
		{
			return;
		}

		flushExperienceBatch(System.currentTimeMillis());
	}

	static boolean isInLoginGracePeriod(int loginGraceTicksRemaining)
	{
		return loginGraceTicksRemaining > 0;
	}

	static boolean shouldNotifyLevelUp(boolean skillHydrated, int oldXp, int newXp)
	{
		if (!skillHydrated || newXp <= oldXp)
		{
			return false;
		}

		return Experience.getLevelForXp(newXp) > Experience.getLevelForXp(oldXp);
	}

	static boolean shouldFlushExperience(long now, long lastFlush, long batchMs)
	{
		return lastFlush <= 0L || now - lastFlush >= batchMs;
	}

	static boolean hasPendingExperience(Map<Skill, Integer> pendingXpGain)
	{
		for (Integer accumulated : pendingXpGain.values())
		{
			if (accumulated != null && accumulated > 0)
			{
				return true;
			}
		}

		return false;
	}

	static List<Map<String, Object>> buildExperienceGainSkills(
		Map<Skill, Integer> pendingXpGain,
		Map<Skill, Integer> previousXp
	)
	{
		List<Map<String, Object>> skills = new ArrayList<>();

		for (Map.Entry<Skill, Integer> entry : pendingXpGain.entrySet())
		{
			int accumulated = entry.getValue();
			if (accumulated <= 0)
			{
				continue;
			}

			Skill skill = entry.getKey();
			int totalXp = previousXp.getOrDefault(skill, 0);
			Map<String, Object> skillData = new HashMap<>();
			skillData.put("skill", skill.getName());
			skillData.put("experienceGained", accumulated);
			skillData.put("totalExperience", totalXp);
			skillData.put("level", Experience.getLevelForXp(totalXp));
			skills.add(skillData);
		}

		return skills;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			beginLoginHydration();
			return;
		}

		if (loginGraceTicksRemaining > 0)
		{
			loginGraceTicksRemaining = 0;
			baselinesReady = false;
			hydratedSkills.clear();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!isEnabled() || isInLoginGracePeriod(loginGraceTicksRemaining))
		{
			return;
		}

		Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			return;
		}

		if (!baselinesReady)
		{
			syncBaselines();
			if (!baselinesReady)
			{
				return;
			}
		}

		int newXp = event.getXp();
		int oldXp = previousXp.getOrDefault(skill, -1);
		boolean skillHydrated = hydratedSkills.contains(skill);

		if (!skillHydrated)
		{
			previousXp.put(skill, newXp);
			hydratedSkills.add(skill);
			return;
		}

		if (oldXp < 0 || newXp <= oldXp)
		{
			previousXp.put(skill, newXp);
			return;
		}

		int xpGained = newXp - oldXp;
		int oldLevel = Experience.getLevelForXp(oldXp);
		int newLevel = Experience.getLevelForXp(newXp);

		if (config.notifyLevel() && shouldNotifyLevelUp(true, oldXp, newXp))
		{
			Map<String, Object> data = new HashMap<>();
			data.put("skill", skill.getName());
			data.put("level", newLevel);
			data.put("previousLevel", oldLevel);
			data.put("totalExperience", newXp);
			data.put("totalLevel", client.getTotalLevel());
			if (client.getLocalPlayer() != null)
			{
				data.put("combatLevel", client.getLocalPlayer().getCombatLevel());
			}
			sendEventOfKind("LEVEL_UP", data);
		}

		if (config.notifyExperience())
		{
			pendingXpGain.merge(skill, xpGained, Integer::sum);
		}

		previousXp.put(skill, newXp);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (loginGraceTicksRemaining > 0)
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				loginGraceTicksRemaining = 0;
				return;
			}

			loginGraceTicksRemaining--;
			if (loginGraceTicksRemaining == 0)
			{
				syncBaselines();
			}
			return;
		}

		if (!config.notifyExperience() || !baselinesReady || !hasPendingExperience(pendingXpGain))
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (!shouldFlushExperience(now, lastExperienceFlush, EXPERIENCE_BATCH_MS))
		{
			return;
		}

		flushExperienceBatch(now);
	}

	private void flushExperienceBatch(long now)
	{
		List<Map<String, Object>> skills = buildExperienceGainSkills(pendingXpGain, previousXp);
		if (skills.isEmpty())
		{
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("skills", skills);
		sendEventOfKind("EXPERIENCE_GAIN", data, false);

		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			pendingXpGain.put(skill, 0);
		}
		lastExperienceFlush = now;
	}
}
