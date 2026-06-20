/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.combat.CombatAchievementTier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class CombatAchievementNotifier extends BaseNotifier
{
	private static final Pattern ACHIEVEMENT_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+)\\.",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TASK_POINTS_SUFFIX = Pattern.compile("\\s+\\(\\d+ points?\\)$", Pattern.CASE_INSENSITIVE);

	private static final Map<CombatAchievementTier, Integer> CUM_POINTS_VARBIT_BY_TIER = new LinkedHashMap<>();

	static
	{
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.EASY, VarbitID.CA_THRESHOLD_EASY);
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.MEDIUM, VarbitID.CA_THRESHOLD_MEDIUM);
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.HARD, VarbitID.CA_THRESHOLD_HARD);
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.ELITE, VarbitID.CA_THRESHOLD_ELITE);
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.MASTER, VarbitID.CA_THRESHOLD_MASTER);
		CUM_POINTS_VARBIT_BY_TIER.put(CombatAchievementTier.GRANDMASTER, VarbitID.CA_THRESHOLD_GRANDMASTER);
	}

	@Inject
	private ClientThread clientThread;

	private final NavigableMap<Integer, CombatAchievementTier> cumulativeUnlockPoints = new TreeMap<>();

	@Override
	public boolean isEnabled()
	{
		return config.notifyCombatAchievement();
	}

	@Override
	protected String getEventKind()
	{
		return "COMBAT_ACHIEVEMENT";
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (cumulativeUnlockPoints.size() < CUM_POINTS_VARBIT_BY_TIER.size())
		{
			initThresholds();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled() || !isGameMessage(event))
		{
			return;
		}

		Matcher matcher = ACHIEVEMENT_PATTERN.matcher(event.getMessage());
		if (!matcher.find())
		{
			return;
		}

		CombatAchievementTier tier = CombatAchievementTier.BY_LOWER_NAME.get(matcher.group("tier").toLowerCase());
		if (tier == null)
		{
			return;
		}

		String task = TASK_POINTS_SUFFIX.matcher(matcher.group("task")).replaceFirst("");
		String message = event.getMessage();
		handleCombatTask(tier, task, message);
	}

	private void handleCombatTask(CombatAchievementTier tier, String task, String message)
	{
		clientThread.invokeAtTickEnd(() ->
		{
			if (cumulativeUnlockPoints.size() < CUM_POINTS_VARBIT_BY_TIER.size())
			{
				initThresholds();
			}

			int taskPoints = tier.getPoints();
			int totalPoints = client.getVarbitValue(VarbitID.CA_POINTS);
			int totalPossiblePoints = client.getVarbitValue(VarbitID.CA_THRESHOLD_GRANDMASTER);

			Map.Entry<Integer, CombatAchievementTier> nextThreshold = cumulativeUnlockPoints.ceilingEntry(totalPoints + 1);
			Map.Entry<Integer, CombatAchievementTier> prev = cumulativeUnlockPoints.floorEntry(totalPoints);
			int prevThreshold = prev != null ? prev.getKey() : 0;

			Integer tierProgress = null;
			Integer tierTotalPoints = null;
			if (nextThreshold != null)
			{
				tierProgress = totalPoints - prevThreshold;
				tierTotalPoints = nextThreshold.getKey() - prevThreshold;
			}

			boolean crossedThreshold = prevThreshold > 0 && totalPoints - taskPoints < prevThreshold;
			CombatAchievementTier completedTier = crossedThreshold && prev != null ? prev.getValue() : null;
			CombatAchievementTier currentTier = crossedThreshold || prev == null ? null : prev.getValue();
			CombatAchievementTier nextTier = nextThreshold != null ? nextThreshold.getValue() : null;

			Map<String, Object> data = new HashMap<>();
			data.put("tier", tier.getDisplayName());
			data.put("task", task);
			data.put("taskName", task);
			data.put("taskPoints", taskPoints);
			data.put("points", taskPoints);
			data.put("totalPoints", totalPoints);
			data.put("totalPossiblePoints", totalPossiblePoints);
			data.put("crossedThreshold", crossedThreshold);
			data.put("message", message);

			if (tierProgress != null)
			{
				data.put("tierProgress", tierProgress);
			}
			if (tierTotalPoints != null)
			{
				data.put("tierTotalPoints", tierTotalPoints);
			}
			if (currentTier != null)
			{
				data.put("currentTier", currentTier.getDisplayName());
			}
			if (nextTier != null)
			{
				data.put("nextTier", nextTier.getDisplayName());
			}
			if (completedTier != null)
			{
				data.put("completedTier", completedTier.getDisplayName());
			}

			sendEvent(data);
		});
	}

	private void initThresholds()
	{
		for (Map.Entry<CombatAchievementTier, Integer> entry : CUM_POINTS_VARBIT_BY_TIER.entrySet())
		{
			int cumulativePoints = client.getVarbitValue(entry.getValue());
			if (cumulativePoints > 0)
			{
				cumulativeUnlockPoints.put(cumulativePoints, entry.getKey());
			}
		}
	}
}
