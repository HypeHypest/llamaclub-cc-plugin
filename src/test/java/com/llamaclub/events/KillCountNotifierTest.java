package com.llamaclub.events;

import com.llamaclub.combat.CompletedFight;
import com.llamaclub.combat.FightCombatStats;
import org.junit.Assert;
import org.junit.Test;

public class KillCountNotifierTest
{
	@Test
	public void parsesStandardKillCountMessage()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your Brutus kill count is: 260."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Brutus", parsed.getBoss());
		Assert.assertEquals(260, parsed.getCount());
	}

	@Test
	public void parsesSubduedPrefixKillCountMessage()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your subdued Brutus count is: 260."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Brutus", parsed.getBoss());
		Assert.assertEquals(260, parsed.getCount());
	}

	@Test
	public void parsesSubduedTypeKillCountMessage()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your Brutus subdued count is: 260."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Brutus", parsed.getBoss());
		Assert.assertEquals(260, parsed.getCount());
	}

	@Test
	public void parsesDemonicBrutusSubduedMessage()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your subdued Demonic Brutus count is: 260."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Demonic Brutus", parsed.getBoss());
		Assert.assertEquals(260, parsed.getCount());
	}

	@Test
	public void milestoneIntervalFor260IsTen()
	{
		Assert.assertEquals(10, KillCountNotifier.getMilestoneInterval(260));
		Assert.assertEquals(50, KillCountNotifier.getMilestoneInterval(250));
		Assert.assertEquals(100, KillCountNotifier.getMilestoneInterval(300));
		Assert.assertEquals(0, KillCountNotifier.getMilestoneInterval(259));
	}

	@Test
	public void parsesKillCountWithColorTags()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your Barrows chest count is: <col=ff0000>562</col>."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Barrows Chests", parsed.getBoss());
		Assert.assertEquals(562, parsed.getCount());
		Assert.assertEquals(0, KillCountNotifier.getMilestoneInterval(parsed.getCount()));
	}

	@Test
	public void parsesKillCountMilestoneWithColorTags()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your Barrows chest count is: <col=ff0000>560</col>."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Barrows Chests", parsed.getBoss());
		Assert.assertEquals(560, parsed.getCount());
		Assert.assertEquals(10, KillCountNotifier.getMilestoneInterval(parsed.getCount()));
	}

	@Test
	public void parsesPersonalBestFromTimeMessage()
	{
		KillCountNotifier.ParsedTime parsed = KillCountNotifier.parseTimeMessage(
			"Time: 1:45.67 (Personal best: 1:30.00)"
		);

		Assert.assertNotNull(parsed);
		Assert.assertFalse(parsed.isNewPersonalBest());
		Assert.assertEquals(90.0, KillCountNotifier.resolvePersonalBestSeconds(
			parsed.getKillTime(),
			parsed.getPersonalBestTime(),
			parsed.isNewPersonalBest()
		), 0.01);
	}

	@Test
	public void parsesNewPersonalBestFromTimeMessage()
	{
		KillCountNotifier.ParsedTime parsed = KillCountNotifier.parseTimeMessage(
			"Time: 1:20.40 (new personal best)"
		);

		Assert.assertNotNull(parsed);
		Assert.assertTrue(parsed.isNewPersonalBest());
		Assert.assertEquals(80.4, KillCountNotifier.resolvePersonalBestSeconds(
			parsed.getKillTime(),
			parsed.getPersonalBestTime(),
			parsed.isNewPersonalBest()
		), 0.01);
	}

	@Test
	public void resolvePersonalBestPrefersNewBestOverExistingPbLine()
	{
		java.time.Duration killTime = java.time.Duration.ofMillis(85000);
		java.time.Duration pbTime = java.time.Duration.ofMillis(90000);

		Assert.assertEquals(85.0, KillCountNotifier.resolvePersonalBestSeconds(killTime, pbTime, true), 0.01);
		Assert.assertEquals(90.0, KillCountNotifier.resolvePersonalBestSeconds(killTime, pbTime, false), 0.01);
	}

	@Test
	public void resolvePersonalBestToPersistPrefersFasterKillTimeOverStaleChatPb()
	{
		java.time.Duration killTime = java.time.Duration.ofMillis(6000);
		java.time.Duration pbTime = java.time.Duration.ofMillis(29_400);

		Assert.assertEquals(6.0, KillCountNotifier.resolvePersonalBestToPersist(killTime, pbTime, false), 0.01);
	}

	@Test
	public void resolvePersonalBestToPersistKeepsExistingPbWhenKillIsSlower()
	{
		java.time.Duration killTime = java.time.Duration.ofMillis(95_000);
		java.time.Duration pbTime = java.time.Duration.ofMillis(90_000);

		Assert.assertEquals(90.0, KillCountNotifier.resolvePersonalBestToPersist(killTime, pbTime, false), 0.01);
	}

	@Test
	public void resolvePersonalBestToPersistUsesKillTimeWhenChatPbMissing()
	{
		java.time.Duration killTime = java.time.Duration.ofMillis(45_200);

		Assert.assertEquals(45.2, KillCountNotifier.resolvePersonalBestToPersist(killTime, null, false), 0.01);
	}

	@Test
	public void buildKillCountPayloadIncludesCombatFieldsAndPrefersChatTime()
	{
		FightCombatStats stats = new FightCombatStats();
		stats.addHit("Dragon hunter crossbow", 670);
		stats.addHit("Dragon warhammer", 150);
		stats.addSpecHit("Dragon warhammer", 150);
		stats.incrementSpecialAttacks();
		stats.incrementSpecialAttacks();

		CompletedFight completedFight = new CompletedFight(
			"Vorkath",
			java.time.Duration.ofMillis(45_200),
			stats,
			System.currentTimeMillis()
		);

		java.time.Duration chatTime = java.time.Duration.ofMillis(40_000);
		java.util.Map<String, Object> payload = KillCountNotifier.buildKillCountPayload(
			"Vorkath",
			123,
			chatTime,
			false,
			completedFight
		);

		Assert.assertEquals("Vorkath", payload.get("boss"));
		Assert.assertEquals(123, payload.get("killCount"));
		Assert.assertEquals(40.0, payload.get("timeSeconds"));
		Assert.assertEquals(820, payload.get("totalDamage"));
		Assert.assertEquals(2, payload.get("specialAttacks"));
		Assert.assertTrue(payload.containsKey("damageBySpecWeapon"));
		Assert.assertTrue(payload.containsKey("weaponsUsed"));
	}

	@Test
	public void parsesLunarChestKillCountMessage()
	{
		KillCountNotifier.ParsedKillCount parsed = KillCountNotifier.parseKillCountMessage(
			"Your Lunar chest count is: <col=ff0000>42</col>."
		);

		Assert.assertNotNull(parsed);
		Assert.assertEquals("Lunar Chest", parsed.getBoss());
		Assert.assertEquals(42, parsed.getCount());
	}

	@Test
	public void buildKillCountPayloadIncludesMoonCount()
	{
		FightCombatStats stats = new FightCombatStats();
		stats.addHit("Dragon scimitar", 500);

		CompletedFight completedFight = new CompletedFight(
			"Lunar Chest",
			java.time.Duration.ofMillis(180_000),
			stats,
			System.currentTimeMillis()
		);

		java.util.Map<String, Object> payload = KillCountNotifier.buildKillCountPayload(
			"Lunar Chest",
			10,
			java.time.Duration.ofMillis(180_000),
			false,
			completedFight,
			3
		);

		Assert.assertEquals("Lunar Chest", payload.get("boss"));
		Assert.assertEquals(10, payload.get("killCount"));
		Assert.assertEquals(3, payload.get("moonCount"));
		Assert.assertEquals(180.0, payload.get("timeSeconds"));
		Assert.assertEquals(500, payload.get("totalDamage"));
	}

	@Test
	public void buildKillCountPayloadOmitsMoonCountWhenNull()
	{
		java.util.Map<String, Object> payload = KillCountNotifier.buildKillCountPayload(
			"Vorkath",
			1,
			null,
			false,
			null
		);

		Assert.assertFalse(payload.containsKey("moonCount"));
	}

	@Test
	public void buildKillCountPayloadUsesCombatDurationWhenChatTimeMissing()
	{
		FightCombatStats stats = new FightCombatStats();
		stats.addHit("Dragon hunter crossbow", 820);

		CompletedFight completedFight = new CompletedFight(
			"Vorkath",
			java.time.Duration.ofMillis(45_200),
			stats,
			System.currentTimeMillis()
		);

		java.util.Map<String, Object> payload = KillCountNotifier.buildKillCountPayload(
			"Vorkath",
			123,
			null,
			false,
			completedFight
		);

		Assert.assertEquals(45.2, (Double) payload.get("timeSeconds"), 0.01);
		Assert.assertEquals(820, payload.get("totalDamage"));
	}
}
