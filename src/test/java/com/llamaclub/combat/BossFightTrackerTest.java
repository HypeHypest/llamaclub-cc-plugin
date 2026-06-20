package com.llamaclub.combat;

import java.time.Duration;
import org.junit.Assert;
import org.junit.Test;

public class BossFightTrackerTest
{
	@Test
	public void accumulatesHitsIntoRunState()
	{
		BossFightTracker.BossRunState run = new BossFightTracker.BossRunState("Vorkath");

		BossFightTracker.applyHit(run, "Dragon hunter crossbow", 120, 1_000L);
		BossFightTracker.applyHit(run, "Dragon hunter crossbow", 80, 2_000L);

		Assert.assertEquals(200, run.stats.getTotalDamage());
		Assert.assertEquals(2, run.stats.getHitCount());
		Assert.assertEquals(200, (int) run.stats.getDamageByWeapon().get("Dragon hunter crossbow"));
	}

	@Test
	public void finalizesRunDurationFromSegmentStart()
	{
		BossFightTracker.BossRunState run = new BossFightTracker.BossRunState("Vorkath");
		run.segmentStartMillis = 1_000L;

		BossFightTracker.applyHit(run, "Dragon hunter crossbow", 50, 1_500L);
		CompletedFight completed = BossFightTracker.finalizeRunState(run, 6_000L);

		Assert.assertEquals("Vorkath", completed.getBossKey());
		Assert.assertEquals(Duration.ofMillis(5_000L), completed.getDuration());
		Assert.assertEquals(50, completed.getStats().getTotalDamage());
	}

	@Test
	public void mergesSpecStatsIntoCompletedFightPayload()
	{
		FightCombatStats stats = new FightCombatStats();
		stats.addHit("Dragon warhammer", 150);
		stats.addSpecHit("Dragon warhammer", 150);
		stats.incrementSpecialAttacks();

		CompletedFight completed = new CompletedFight(
			"Vorkath",
			Duration.ofSeconds(45),
			stats,
			System.currentTimeMillis()
		);

		Assert.assertEquals(150, completed.toKillCountPayloadFields().get("totalDamage"));
		Assert.assertEquals(1, completed.toKillCountPayloadFields().get("specialAttacks"));
		Assert.assertEquals(
			150,
			((java.util.Map<?, ?>) completed.toKillCountPayloadFields().get("damageBySpecWeapon")).get("Dragon warhammer")
		);
	}
}
