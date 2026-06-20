/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.combat.BarrowsBrotherCombatTracker;
import com.llamaclub.combat.BossFightTracker;
import com.llamaclub.combat.CompletedFight;
import com.llamaclub.combat.LunarMoonCombatTracker;
import com.llamaclub.combat.LunarMoonCombatTracker.LunarRunResult;
import com.llamaclub.sync.CombatAchievementBossStatsReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class KillCountNotifier extends BaseNotifier
{
	private static final Pattern PRIMARY_REGEX = Pattern.compile(
		"Your (?<key>.+?)\\s+(?<type>kill|chest|completion|harvest|success|opened|subdued)\\s?count is:?\\s*(?<value>[\\d,]+)\\b",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern SECONDARY_REGEX = Pattern.compile(
		"Your (?:completed|subdued) (?<key>.+) count is: (?<value>[\\d,]+)\\b",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern TIME_REGEX = Pattern.compile(
		"(?:Duration|time|Subdued in):?\\s*(?<time>[\\d:]+(?:\\.\\d+)?)\\.?(?:\\s*\\(new personal best\\))?(?:\\s*\\(Personal best:\\s*(?<pbtime>[\\d:]+(?:\\.\\d+)?)\\))?",
		Pattern.CASE_INSENSITIVE
	);

	private static final String BARROWS_BOSS_NAME = "Barrows Chests";
	private static final String LUNAR_CHEST_BOSS_NAME = "Lunar Chest";

	private static final int MAX_BAD_TICKS = 10;
	private static final int[] MILESTONE_INTERVALS = {100, 50, 10};

	@Inject
	private ConfigManager configManager;

	@Inject
	private BarrowsBrotherCombatTracker barrowsBrotherCombatTracker;

	@Inject
	private LunarMoonCombatTracker lunarMoonCombatTracker;

	@Inject
	private BossFightTracker bossFightTracker;

	@Inject
	private CombatAchievementBossStatsReader combatAchievementBossStatsReader;

	private String pendingBoss = null;
	private Integer pendingCount = null;
	private Duration pendingTime = null;
	private Duration pendingPbTime = null;
	private boolean pendingIsPb = false;
	private CompletedFight pendingCompletedFight = null;
	private Integer pendingMoonCount = null;
	private int badTicks = 0;

	@Override
	public boolean isEnabled()
	{
		return config.notifyKillCount();
	}

	@Override
	protected String getEventKind()
	{
		return "KILL_COUNT";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled())
		{
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		onChatMessage(event.getMessage());
	}

	public void onChatMessage(String message)
	{
		if (!isEnabled())
		{
			return;
		}

		if (message.startsWith("Preparation"))
		{
			return;
		}

		message = normalizeChatMessage(message);
		parseBossKillCount(message);
		parseTime(message);
	}

	static String normalizeChatMessage(String message)
	{
		if (message == null)
		{
			return null;
		}

		return Text.removeTags(message);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled())
		{
			return;
		}

		if (pendingBoss != null && pendingCount != null)
		{
			persistPendingPersonalBest();

			if (hasParsedTimeData() || hasCombatStatsPending() || badTicks >= MAX_BAD_TICKS)
			{
				sendKillCountNotification();
				reset();
				return;
			}

			badTicks++;
			return;
		}
		else if (pendingTime != null || pendingBoss != null || pendingCount != null)
		{
			badTicks++;
			if (badTicks > MAX_BAD_TICKS)
			{
				reset();
			}
		}
	}

	private void parseBossKillCount(String message)
	{
		ParsedKillCount parsed = parseKillCountMessage(message);
		if (parsed == null)
		{
			return;
		}

		pendingBoss = parsed.getBoss();
		pendingCount = parsed.getCount();
		badTicks = 0;
		setKc(parsed.getBoss(), parsed.getCount());

		if (BARROWS_BOSS_NAME.equals(parsed.getBoss()))
		{
			applyBarrowsSpeedTime();
		}
		else if (LUNAR_CHEST_BOSS_NAME.equals(parsed.getBoss()))
		{
			applyLunarSpeedTime();
		}
		else
		{
			applyCombatSpeedTime(parsed.getBoss());
		}
	}

	private void applyCombatSpeedTime(String boss)
	{
		if (pendingTime != null)
		{
			return;
		}

		Duration combatDuration = bossFightTracker.peekPendingDuration(boss);
		if (combatDuration != null && !combatDuration.isZero())
		{
			pendingTime = combatDuration;
		}
	}

	private void applyBarrowsSpeedTime()
	{
		CompletedFight completed = barrowsBrotherCombatTracker.consumeCompletedRun();
		if (completed != null)
		{
			pendingCompletedFight = completed;
			pendingTime = completed.getDuration();
			pendingPbTime = completed.getDuration();
			pendingIsPb = false;
		}
		else
		{
			pendingCompletedFight = null;
			pendingTime = null;
			pendingPbTime = null;
			pendingIsPb = false;
			barrowsBrotherCombatTracker.reset();
		}
	}

	private void applyLunarSpeedTime()
	{
		LunarRunResult result = lunarMoonCombatTracker.consumeRun();
		if (result != null)
		{
			pendingCompletedFight = result.getCompletedFight();
			pendingTime = result.getCompletedFight().getDuration();
			pendingPbTime = result.getCompletedFight().getDuration();
			pendingIsPb = false;
			pendingMoonCount = result.getMoonsKilled();
		}
		else
		{
			pendingCompletedFight = null;
			pendingTime = null;
			pendingPbTime = null;
			pendingIsPb = false;
			pendingMoonCount = null;
			lunarMoonCombatTracker.reset();
		}
	}

	private void parseTime(String message)
	{
		if (BARROWS_BOSS_NAME.equals(pendingBoss) || LUNAR_CHEST_BOSS_NAME.equals(pendingBoss))
		{
			return;
		}

		ParsedTime parsed = parseTimeMessage(message);
		if (parsed == null)
		{
			return;
		}

		if (parsed.getKillTime() != null)
		{
			pendingTime = parsed.getKillTime();
		}

		if (parsed.getPersonalBestTime() != null)
		{
			pendingPbTime = parsed.getPersonalBestTime();
		}

		if (parsed.isNewPersonalBest())
		{
			pendingIsPb = true;
		}

		if (message.startsWith("Wave") && pendingBoss == null)
		{
			int tobIndex = message.indexOf("Theatre of Blood");
			if (tobIndex > 0)
			{
				pendingBoss = "Theatre of Blood";
			}
		}

		badTicks = 0;
	}

	/**
	 * Package-private for unit tests.
	 */
	static ParsedTime parseTimeMessage(String message)
	{
		message = normalizeChatMessage(message);
		if (message == null || message.startsWith("Preparation"))
		{
			return null;
		}

		String msg = message;
		if (message.startsWith("Wave"))
		{
			int tobIndex = message.indexOf("Theatre of Blood");
			if (tobIndex > 0)
			{
				msg = message.substring(tobIndex);
			}
		}

		Matcher matcher = TIME_REGEX.matcher(msg);
		if (!matcher.find())
		{
			return null;
		}

		Duration killTime = parseTimeString(matcher.group("time"));
		if (killTime == null)
		{
			return null;
		}

		String lowerMsg = msg.toLowerCase();
		boolean isNewPersonalBest = lowerMsg.contains("(new personal best)")
			|| lowerMsg.contains("new personal best");
		Duration personalBestTime = parseTimeString(matcher.group("pbtime"));

		return new ParsedTime(killTime, personalBestTime, isNewPersonalBest);
	}

	static Double resolvePersonalBestSeconds(Duration killTime, Duration personalBestTime, boolean isNewPersonalBest)
	{
		if (isNewPersonalBest && killTime != null)
		{
			return durationToSeconds(killTime);
		}

		if (personalBestTime != null)
		{
			return durationToSeconds(personalBestTime);
		}

		return null;
	}

	static Double resolvePersonalBestToPersist(Duration killTime, Duration personalBestTime, boolean isNewPersonalBest)
	{
		Double killSeconds = killTime != null && !killTime.isZero() ? durationToSeconds(killTime) : null;
		Double chatPbSeconds = resolvePersonalBestSeconds(killTime, personalBestTime, isNewPersonalBest);
		Double bestCandidate = null;

		if (killSeconds != null && killSeconds > 0)
		{
			bestCandidate = killSeconds;
		}

		if (chatPbSeconds != null && chatPbSeconds > 0)
		{
			bestCandidate = bestCandidate == null ? chatPbSeconds : Math.min(bestCandidate, chatPbSeconds);
		}

		return bestCandidate;
	}

	private void persistPendingPersonalBest()
	{
		if (pendingBoss == null)
		{
			return;
		}

		String pbBoss = pendingBoss;
		if (pendingMoonCount != null && pendingMoonCount > 0)
		{
			pbBoss = pendingBoss + " " + pendingMoonCount;
		}

		try
		{
			if (combatAchievementBossStatsReader.hasAuthoritativePersonalBest(pbBoss))
			{
				return;
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to check CA personal best for {}", pbBoss, ex);
		}

		Double seconds = resolvePersonalBestToPersist(pendingTime, pendingPbTime, pendingIsPb);
		if (seconds != null && seconds > 0)
		{
			setPbIfBetter(pbBoss, seconds);
		}
	}

	private boolean hasParsedTimeData()
	{
		return pendingTime != null || pendingPbTime != null || pendingIsPb;
	}

	private boolean hasCombatStatsPending()
	{
		if (pendingCompletedFight != null)
		{
			return true;
		}

		return pendingBoss != null && bossFightTracker.hasPendingFight(pendingBoss);
	}

	private static String normalizePrimaryBoss(String boss, String type)
	{
		if (boss == null || type == null)
		{
			return null;
		}

		switch (type.toLowerCase())
		{
			case "chest":
				if ("Barrows".equalsIgnoreCase(boss))
				{
					return BARROWS_BOSS_NAME;
				}
				if ("Lunar".equals(boss))
				{
					return LUNAR_CHEST_BOSS_NAME;
				}
				return null;

			case "completion":
				if ("Gauntlet".equalsIgnoreCase(boss))
				{
					return "Crystalline Hunllef";
				}
				if ("Corrupted Gauntlet".equalsIgnoreCase(boss))
				{
					return "Corrupted Hunllef";
				}
				return null;

			case "harvest":
				if ("Herbiboar".equalsIgnoreCase(boss))
				{
					return "Herbiboar";
				}
				return null;

			case "kill":
			case "subdued":
			case "success":
			case "opened":
				return boss;

			default:
				return null;
		}
	}

	private static String normalizeSecondaryBoss(String boss)
	{
		if (boss == null)
		{
			return null;
		}

		if ("Wintertodt".equalsIgnoreCase(boss))
		{
			return "Wintertodt";
		}

		int modeSeparator = boss.lastIndexOf(':');
		String raidName = modeSeparator > 0 ? boss.substring(0, modeSeparator).trim() : boss;

		if (raidName.equalsIgnoreCase("Theatre of Blood")
			|| raidName.equalsIgnoreCase("Tombs of Amascut")
			|| raidName.equalsIgnoreCase("Chambers of Xeric")
			|| boss.equalsIgnoreCase("Chambers of Xeric Challenge Mode"))
		{
			return boss;
		}

		// Non-raid bosses (e.g. Brutus, Demonic Brutus) use "Your subdued X count is:".
		return boss;
	}

	/**
	 * Package-private for unit tests.
	 */
	static ParsedKillCount parseKillCountMessage(String message)
	{
		message = normalizeChatMessage(message);
		if (message == null || message.startsWith("Preparation"))
		{
			return null;
		}

		Matcher primary = PRIMARY_REGEX.matcher(message);
		if (primary.find())
		{
			String boss = normalizePrimaryBoss(primary.group("key"), primary.group("type"));
			if (boss == null)
			{
				return null;
			}

			try
			{
				return new ParsedKillCount(
					boss,
					Integer.parseInt(primary.group("value").replace(",", ""))
				);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		Matcher secondary = SECONDARY_REGEX.matcher(message);
		if (secondary.find())
		{
			String boss = normalizeSecondaryBoss(secondary.group("key"));
			if (boss == null)
			{
				return null;
			}

			try
			{
				return new ParsedKillCount(
					boss,
					Integer.parseInt(secondary.group("value").replace(",", ""))
				);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		return null;
	}

	static final class ParsedKillCount
	{
		private final String boss;
		private final int count;

		ParsedKillCount(String boss, int count)
		{
			this.boss = boss;
			this.count = count;
		}

		String getBoss()
		{
			return boss;
		}

		int getCount()
		{
			return count;
		}
	}

	static final class ParsedTime
	{
		private final Duration killTime;
		private final Duration personalBestTime;
		private final boolean newPersonalBest;

		ParsedTime(Duration killTime, Duration personalBestTime, boolean newPersonalBest)
		{
			this.killTime = killTime;
			this.personalBestTime = personalBestTime;
			this.newPersonalBest = newPersonalBest;
		}

		Duration getKillTime()
		{
			return killTime;
		}

		Duration getPersonalBestTime()
		{
			return personalBestTime;
		}

		boolean isNewPersonalBest()
		{
			return newPersonalBest;
		}
	}

	private static Duration parseTimeString(String timeStr)
	{
		if (timeStr == null)
		{
			return null;
		}

		try
		{
			String[] parts = timeStr.split(":");
			if (parts.length == 2)
			{
				int minutes = Integer.parseInt(parts[0]);
				double seconds = Double.parseDouble(parts[1]);
				long totalMillis = (minutes * 60 * 1000L) + (long) (seconds * 1000);
				return Duration.ofMillis(totalMillis);
			}
			else if (parts.length == 3)
			{
				int hours = Integer.parseInt(parts[0]);
				int minutes = Integer.parseInt(parts[1]);
				double seconds = Double.parseDouble(parts[2]);
				long totalMillis = (hours * 3600 * 1000L) + (minutes * 60 * 1000L) + (long) (seconds * 1000);
				return Duration.ofMillis(totalMillis);
			}
		}
		catch (NumberFormatException e)
		{
			log.debug("Failed to parse time: {}", timeStr);
		}

		return null;
	}

	private void sendKillCountNotification()
	{
		CompletedFight completedFight = pendingCompletedFight;
		if (completedFight == null && pendingBoss != null)
		{
			completedFight = bossFightTracker.consume(pendingBoss);
		}

		Map<String, Object> kcData = buildKillCountPayload(
			pendingBoss,
			pendingCount,
			pendingTime,
			pendingIsPb,
			completedFight,
			pendingMoonCount
		);
		sendEvent(kcData);
	}

	static Map<String, Object> buildKillCountPayload(
		String boss,
		Integer killCount,
		Duration time,
		boolean personalBest,
		CompletedFight completedFight
	)
	{
		return buildKillCountPayload(boss, killCount, time, personalBest, completedFight, null);
	}

	static Map<String, Object> buildKillCountPayload(
		String boss,
		Integer killCount,
		Duration time,
		boolean personalBest,
		CompletedFight completedFight,
		Integer moonCount
	)
	{
		Map<String, Object> kcData = new HashMap<>();
		kcData.put("boss", boss);
		kcData.put("killCount", killCount);

		int milestone = getMilestoneInterval(killCount);
		if (milestone > 0)
		{
			kcData.put("milestone", milestone);
		}

		if (time != null)
		{
			kcData.put("time", formatDuration(time));
			kcData.put("timeSeconds", durationToSeconds(time));
		}
		else if (completedFight != null && completedFight.getDuration() != null && !completedFight.getDuration().isZero())
		{
			kcData.put("time", formatDuration(completedFight.getDuration()));
			kcData.put("timeSeconds", durationToSeconds(completedFight.getDuration()));
		}

		if (moonCount != null && moonCount > 0)
		{
			kcData.put("moonCount", moonCount);
		}

		if (personalBest)
		{
			kcData.put("personalBest", true);
		}

		if (completedFight != null)
		{
			kcData.putAll(completedFight.toKillCountPayloadFields());
		}

		return kcData;
	}

	/**
	 * Returns the highest milestone interval reached (100, 50, or 10), or 0 if none.
	 */
	static int getMilestoneInterval(int killCount)
	{
		if (killCount <= 0)
		{
			return 0;
		}

		for (int interval : MILESTONE_INTERVALS)
		{
			if (killCount % interval == 0)
			{
				return interval;
			}
		}

		return 0;
	}

	private static double durationToSeconds(Duration duration)
	{
		return duration.getSeconds() + (duration.getNano() / 1_000_000_000.0);
	}

	private static String formatDuration(Duration duration)
	{
		long totalSeconds = duration.getSeconds();
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long millis = duration.getNano() / 1_000_000;

		if (hours > 0)
		{
			return String.format("%d:%02d:%02d.%d", hours, minutes, seconds, millis / 100);
		}

		return String.format("%d:%02d.%d", minutes, seconds, millis / 100);
	}

	private void setKc(String boss, int killcount)
	{
		configManager.setRSProfileConfiguration("killcount", bossKey(boss), killcount);
	}

	private void setPb(String boss, double seconds)
	{
		configManager.setRSProfileConfiguration("personalbest", bossKey(boss), seconds);
	}

	private void setPbIfBetter(String boss, double seconds)
	{
		Double existing = configManager.getRSProfileConfiguration("personalbest", bossKey(boss), double.class);
		if (existing == null || existing <= 0 || seconds < existing)
		{
			setPb(boss, seconds);
		}
	}

	private static String bossKey(String boss)
	{
		return boss.toLowerCase().replace(":", ".");
	}

	private void reset()
	{
		pendingBoss = null;
		pendingCount = null;
		pendingTime = null;
		pendingPbTime = null;
		pendingIsPb = false;
		pendingCompletedFight = null;
		pendingMoonCount = null;
		badTicks = 0;
	}
}
