package com.llamaclub.combat;

import org.junit.Assert;
import org.junit.Test;

public class SpecAttributionServiceTest
{
	@Test
	public void fightStatsTrackSpecAttemptsAndSpecDamageSeparately()
	{
		FightCombatStats stats = new FightCombatStats();
		stats.incrementSpecialAttacks();
		stats.incrementSpecialAttacks();
		stats.addHit("Dragon warhammer", 0);
		stats.addSpecHit("Dragon warhammer", 0);

		Assert.assertEquals(2, stats.getSpecialAttacks());
		Assert.assertTrue(stats.getDamageBySpecWeapon().isEmpty());

		stats.addHit("Dragon warhammer", 150);
		stats.addSpecHit("Dragon warhammer", 150);

		Assert.assertEquals(150, (int) stats.getDamageBySpecWeapon().get("Dragon warhammer"));
		Assert.assertEquals(150, (int) stats.getDamageByWeapon().get("Dragon warhammer"));
	}

	@Test
	public void specHitDelayUsesMeleeDefaultForAdjacentTarget()
	{
		Assert.assertEquals(1, SpecHitDelay.estimateHitDelayTicks(null, null));
	}
}
