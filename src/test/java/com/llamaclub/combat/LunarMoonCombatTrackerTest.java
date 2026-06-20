package com.llamaclub.combat;

import org.junit.Assert;
import org.junit.Test;

public class LunarMoonCombatTrackerTest
{
	private static final String BLUE_MOON = "Blue Moon";
	private static final String BLOOD_MOON = "Blood Moon";
	private static final String ECLIPSE_MOON = "Eclipse Moon";

	@Test
	public void recognizesLunarMoonNames()
	{
		Assert.assertTrue(LunarMoonCombatTracker.isLunarMoonName(BLUE_MOON));
		Assert.assertTrue(LunarMoonCombatTracker.isLunarMoonName(BLOOD_MOON));
		Assert.assertFalse(LunarMoonCombatTracker.isLunarMoonName("Blood jaguar"));
	}

	@Test
	public void accumulatesThreeMoonKillDurations()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		recordMoonKill(state, ECLIPSE_MOON, 0L, 14_000L);
		recordMoonKill(state, BLUE_MOON, 15_000L, 25_000L);
		recordMoonKill(state, BLOOD_MOON, 26_000L, 36_000L);

		Assert.assertEquals(3, state.killedMoons.size());
		Assert.assertEquals(34_000L, state.totalTimeMillis);
	}

	@Test
	public void accumulatesSingleMoonKillDuration()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		recordMoonKill(state, BLUE_MOON, 0L, 12_000L);

		Assert.assertEquals(1, state.killedMoons.size());
		Assert.assertEquals(12_000L, state.totalTimeMillis);
	}

	@Test
	public void ignoresMoonKillWithoutFightStart()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		Assert.assertFalse(LunarMoonCombatTracker.recordMoonKill(state, BLUE_MOON, 10_000L));
		Assert.assertEquals(0, state.killedMoons.size());
	}

	@Test
	public void ignoresMoonKillWhenActiveNameMismatch()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();
		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLUE_MOON, 0L);

		Assert.assertFalse(LunarMoonCombatTracker.recordMoonKill(state, BLOOD_MOON, 10_000L));
		Assert.assertEquals(0, state.killedMoons.size());
		Assert.assertEquals(BLUE_MOON, state.activeMoonName);
	}

	@Test
	public void doesNotResetTimerForSameMoonHit()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLUE_MOON, 1_000L);
		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLUE_MOON, 8_000L);

		Assert.assertTrue(LunarMoonCombatTracker.recordMoonKill(state, BLUE_MOON, 12_000L));
		Assert.assertEquals(11_000L, state.totalTimeMillis);
	}

	@Test
	public void accumulatesCombatStatsAcrossMoons()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		LunarMoonCombatTracker.beginFightOnFirstHit(state, ECLIPSE_MOON, 0L);
		state.stats.addHit("Dragon scimitar", 100);
		Assert.assertTrue(LunarMoonCombatTracker.recordMoonKill(state, ECLIPSE_MOON, 10_000L));

		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLUE_MOON, 11_000L);
		state.stats.addHit("Dragon mace", 50);
		state.stats.addSpecHit("Dragon warhammer", 30);
		state.stats.incrementSpecialAttacks();
		Assert.assertTrue(LunarMoonCombatTracker.recordMoonKill(state, BLUE_MOON, 20_000L));

		Assert.assertEquals(150, state.stats.getTotalDamage());
		Assert.assertEquals(2, state.stats.getHitCount());
		Assert.assertEquals(1, state.stats.getSpecialAttacks());
		Assert.assertEquals(30, (int) state.stats.getDamageBySpecWeapon().get("Dragon warhammer"));
	}

	@Test
	public void resetsIncompleteRunState()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();
		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLUE_MOON, 0L);
		Assert.assertTrue(LunarMoonCombatTracker.recordMoonKill(state, BLUE_MOON, 8_000L));

		state.reset();

		Assert.assertEquals(0, state.killedMoons.size());
		Assert.assertEquals(0L, state.totalTimeMillis);
		Assert.assertEquals(0, state.stats.getTotalDamage());
	}

	@Test
	public void completesMoonFromDialogueWithoutPriorFightStart()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();

		Assert.assertTrue(
			LunarMoonCombatTracker.completeMoonFromDialogue(state, BLUE_MOON, 15_000L)
		);
		Assert.assertEquals(1, state.killedMoons.size());
		Assert.assertTrue(state.killedMoons.contains(BLUE_MOON));
	}

	@Test
	public void completesMoonFromDialogueAfterTrackedFight()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();
		LunarMoonCombatTracker.beginFightOnFirstHit(state, BLOOD_MOON, 0L);

		Assert.assertTrue(
			LunarMoonCombatTracker.completeMoonFromDialogue(state, BLOOD_MOON, 20_000L)
		);
		Assert.assertEquals(20_000L, state.totalTimeMillis);
		Assert.assertEquals(1, state.killedMoons.size());
	}

	@Test
	public void normalizesMoonNameFromDialogueText()
	{
		Assert.assertEquals(BLOOD_MOON, LunarMoonCombatTracker.normalizeMoonName("blood moon"));
		Assert.assertNull(LunarMoonCombatTracker.normalizeMoonName("Blood jaguar"));
	}

	@Test
	public void ignoresDuplicateDialogueCompletion()
	{
		LunarMoonCombatTracker.RunState state = new LunarMoonCombatTracker.RunState();
		Assert.assertTrue(
			LunarMoonCombatTracker.completeMoonFromDialogue(state, ECLIPSE_MOON, 10_000L)
		);
		Assert.assertFalse(
			LunarMoonCombatTracker.completeMoonFromDialogue(state, ECLIPSE_MOON, 11_000L)
		);
		Assert.assertEquals(1, state.killedMoons.size());
	}

	private static void recordMoonKill(
		LunarMoonCombatTracker.RunState state,
		String moonName,
		long fightStartMillis,
		long deathMillis
	)
	{
		LunarMoonCombatTracker.beginFightOnFirstHit(state, moonName, fightStartMillis);
		Assert.assertTrue(LunarMoonCombatTracker.recordMoonKill(state, moonName, deathMillis));
	}
}
