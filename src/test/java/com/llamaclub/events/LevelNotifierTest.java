package com.llamaclub.events;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class LevelNotifierTest
{
	@Test
	public void loginGracePeriodBlocksStatTrackingWhileActive()
	{
		Assert.assertTrue(LevelNotifier.isInLoginGracePeriod(LevelNotifier.LOGIN_SYNC_GRACE_TICKS));
		Assert.assertTrue(LevelNotifier.isInLoginGracePeriod(1));
		Assert.assertFalse(LevelNotifier.isInLoginGracePeriod(0));
	}

	@Test
	public void shouldFlushExperienceAfterBatchWindow()
	{
		long now = 100_000L;
		Assert.assertFalse(LevelNotifier.shouldFlushExperience(now, now - 30_000L, LevelNotifier.EXPERIENCE_BATCH_MS));
		Assert.assertTrue(LevelNotifier.shouldFlushExperience(now, now - LevelNotifier.EXPERIENCE_BATCH_MS, LevelNotifier.EXPERIENCE_BATCH_MS));
		Assert.assertTrue(LevelNotifier.shouldFlushExperience(now, 0L, LevelNotifier.EXPERIENCE_BATCH_MS));
	}

	@Test
	public void hasPendingExperienceWhenAnySkillHasGain()
	{
		Map<Skill, Integer> pending = new EnumMap<>(Skill.class);
		pending.put(Skill.ATTACK, 0);
		pending.put(Skill.STRENGTH, 0);
		Assert.assertFalse(LevelNotifier.hasPendingExperience(pending));

		pending.put(Skill.HITPOINTS, 500);
		Assert.assertTrue(LevelNotifier.hasPendingExperience(pending));
	}

	@Test
	public void shouldNotifyLevelUpOnlyWhenXpIncreasesAcrossLevelBoundary()
	{
		int oldXp = Experience.getXpForLevel(50);
		int newXp = Experience.getXpForLevel(51);
		Assert.assertTrue(LevelNotifier.shouldNotifyLevelUp(true, oldXp, newXp));
		Assert.assertFalse(LevelNotifier.shouldNotifyLevelUp(true, newXp, newXp));
		Assert.assertFalse(LevelNotifier.shouldNotifyLevelUp(false, oldXp, newXp));
		Assert.assertFalse(LevelNotifier.shouldNotifyLevelUp(true, newXp, oldXp));
	}

	@Test
	public void hydrationJumpDoesNotNotifyLevelUp()
	{
		int hydratedXp = Experience.getXpForLevel(50);
		int lateHydratedXp = Experience.getXpForLevel(99);
		Assert.assertFalse(LevelNotifier.shouldNotifyLevelUp(false, 0, lateHydratedXp));
		Assert.assertTrue(LevelNotifier.shouldNotifyLevelUp(true, hydratedXp, lateHydratedXp));
	}
	{
		Map<Skill, Integer> pending = new EnumMap<>(Skill.class);
		pending.put(Skill.ATTACK, 12_400);
		pending.put(Skill.STRENGTH, 0);
		pending.put(Skill.HITPOINTS, 4_100);

		Map<Skill, Integer> totals = new EnumMap<>(Skill.class);
		totals.put(Skill.ATTACK, 2_535_426);
		totals.put(Skill.HITPOINTS, 1_200_000);

		List<Map<String, Object>> skills = LevelNotifier.buildExperienceGainSkills(pending, totals);

		Assert.assertEquals(2, skills.size());
		Assert.assertEquals("Attack", skills.get(0).get("skill"));
		Assert.assertEquals(12_400, skills.get(0).get("experienceGained"));
		Assert.assertEquals(2_535_426, skills.get(0).get("totalExperience"));
		Assert.assertEquals(Experience.getLevelForXp(2_535_426), skills.get(0).get("level"));
		Assert.assertEquals("Hitpoints", skills.get(1).get("skill"));
		Assert.assertEquals(4_100, skills.get(1).get("experienceGained"));
	}
}
