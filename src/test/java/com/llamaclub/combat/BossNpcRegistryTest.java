package com.llamaclub.combat;

import net.runelite.api.gameval.NpcID;
import org.junit.Assert;
import org.junit.Test;

public class BossNpcRegistryTest
{
	@Test
	public void resolvesVorkathNpcId()
	{
		Assert.assertEquals("Vorkath", BossNpcRegistry.resolveBossKey(NpcID.VORKATH));
	}

	@Test
	public void resolvesZulrahFormsToSameBossKey()
	{
		Assert.assertEquals("Zulrah", BossNpcRegistry.resolveBossKey(NpcID.SNAKEBOSS_BOSS_RANGED));
		Assert.assertEquals("Zulrah", BossNpcRegistry.resolveBossKey(NpcID.SNAKEBOSS_BOSS_MELEE));
		Assert.assertEquals("Zulrah", BossNpcRegistry.resolveBossKey(NpcID.SNAKEBOSS_BOSS_MAGIC));
	}

	@Test
	public void resolvesBrutusNpcId()
	{
		Assert.assertEquals("Brutus", BossNpcRegistry.resolveBossKey(15626));
	}

	@Test
	public void resolvesBossNameFallback()
	{
		Assert.assertEquals("Brutus", BossNpcRegistry.resolveBossKeyByName("Brutus"));
		Assert.assertEquals("Brutus", BossNpcRegistry.resolveBossKeyByName("Demonic Brutus"));
	}

	@Test
	public void excludesRaidsFromTracking()
	{
		Assert.assertTrue(BossNpcRegistry.isExcludedRaid("Theatre of Blood"));
		Assert.assertTrue(BossNpcRegistry.isExcludedRaid("Tombs of Amascut: Expert Mode"));
		Assert.assertFalse(BossNpcRegistry.isTrackableBossKey("Theatre of Blood"));
		Assert.assertTrue(BossNpcRegistry.isTrackableBossKey("Vorkath"));
	}

	@Test
	public void excludesBarrowsFromGenericTracking()
	{
		Assert.assertFalse(BossNpcRegistry.isTrackableBossKey(BossNpcRegistry.BARROWS_CHESTS));
	}

	@Test
	public void excludesLunarChestFromGenericTracking()
	{
		Assert.assertFalse(BossNpcRegistry.isTrackableBossKey(BossNpcRegistry.LUNAR_CHEST));
	}

	@Test
	public void marksHydraAsMultiPhaseWithTerminalForm()
	{
		Assert.assertTrue(BossNpcRegistry.isMultiPhaseBoss("Alchemical Hydra"));
		Assert.assertTrue(BossNpcRegistry.isTerminalNpc("Alchemical Hydra", NpcID.HYDRABOSS_FINALDEATH));
		Assert.assertFalse(BossNpcRegistry.isTerminalNpc("Alchemical Hydra", NpcID.HYDRABOSS_2));
	}
}
