package com.llamaclub.combat;

import org.junit.Assert;
import org.junit.Test;

public class BarrowsBrotherCombatTrackerTest
{
	private static final String DHAROK = "Dharok the Wretched";
	private static final String AHRIM = "Ahrim the Blighted";

	@Test
	public void recognizesBarrowsBrotherNames()
	{
		Assert.assertTrue(BarrowsBrotherCombatTracker.isBarrowsBrotherName("Guthan the Infested"));
		Assert.assertFalse(BarrowsBrotherCombatTracker.isBarrowsBrotherName("Bloodworm"));
	}

	@Test
	public void accumulatesSixBrotherKillDurations()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();

		recordBrotherKill(state, DHAROK, 0L, 14_000L);
		recordBrotherKill(state, AHRIM, 15_000L, 25_000L);
		recordBrotherKill(state, "Verac the Defiled", 26_000L, 36_000L);
		recordBrotherKill(state, "Torag the Corrupted", 37_000L, 46_000L);
		recordBrotherKill(state, "Karil the Tainted", 47_000L, 61_000L);
		recordBrotherKill(state, "Guthan the Infested", 62_000L, 70_000L);

		Assert.assertEquals(6, state.killedBrothers.size());
		Assert.assertEquals(65_000L, state.totalTimeMillis);
	}

	@Test
	public void ignoresBrotherKillWithoutFightStart()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();

		Assert.assertFalse(BarrowsBrotherCombatTracker.recordBrotherKill(state, DHAROK, 10_000L));
		Assert.assertEquals(0, state.killedBrothers.size());
	}

	@Test
	public void ignoresBrotherKillWhenActiveNameMismatch()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();
		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 0L);

		Assert.assertFalse(BarrowsBrotherCombatTracker.recordBrotherKill(state, AHRIM, 10_000L));
		Assert.assertEquals(0, state.killedBrothers.size());
		Assert.assertEquals(DHAROK, state.activeBrotherName);
	}

	@Test
	public void doesNotResetTimerForSameBrotherHit()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();

		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 1_000L);
		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 8_000L);

		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, DHAROK, 12_000L));
		Assert.assertEquals(11_000L, state.totalTimeMillis);
	}

	@Test
	public void recordsKillByBrotherNameDespiteIndexChange()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();
		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 0L);

		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, DHAROK, 12_000L));
		Assert.assertEquals(12_000L, state.totalTimeMillis);
	}

	@Test
	public void accumulatesCombatStatsAcrossBrothers()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();

		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 0L);
		state.stats.addHit("Abyssal whip", 100);
		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, DHAROK, 10_000L));

		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, AHRIM, 11_000L);
		state.stats.addHit("Abyssal whip", 50);
		state.stats.addSpecHit("Dragon warhammer", 30);
		state.stats.incrementSpecialAttacks();
		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, AHRIM, 20_000L));

		Assert.assertEquals(150, state.stats.getTotalDamage());
		Assert.assertEquals(2, state.stats.getHitCount());
		Assert.assertEquals(1, state.stats.getSpecialAttacks());
		Assert.assertEquals(30, (int) state.stats.getDamageBySpecWeapon().get("Dragon warhammer"));
	}

	@Test
	public void resetsIncompleteRunState()
	{
		BarrowsBrotherCombatTracker.RunState state = new BarrowsBrotherCombatTracker.RunState();
		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, DHAROK, 0L);
		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, DHAROK, 8_000L));

		state.reset();

		Assert.assertEquals(0, state.killedBrothers.size());
		Assert.assertEquals(0L, state.totalTimeMillis);
		Assert.assertEquals(0L, state.fightStartMillis);
		Assert.assertEquals(0, state.stats.getTotalDamage());
	}

	private static void recordBrotherKill(
		BarrowsBrotherCombatTracker.RunState state,
		String brotherName,
		long fightStartMillis,
		long deathMillis
	)
	{
		BarrowsBrotherCombatTracker.beginFightOnFirstHit(state, brotherName, fightStartMillis);
		Assert.assertTrue(BarrowsBrotherCombatTracker.recordBrotherKill(state, brotherName, deathMillis));
	}
}
