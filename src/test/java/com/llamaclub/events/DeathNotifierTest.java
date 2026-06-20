package com.llamaclub.events;

import com.llamaclub.combat.BossNpcRegistry;
import org.junit.Assert;
import org.junit.Test;

public class DeathNotifierTest
{
	@Test
	public void treatsRegisteredBossNamesAsKillerCandidates()
	{
		Assert.assertNotNull(BossNpcRegistry.resolveBossKeyByName("Brutus"));
		Assert.assertNotNull(BossNpcRegistry.resolveBossKeyByName("Demonic Brutus"));
	}

	@Test
	public void resolvesKillerFromTrackedNpcSnapshotWhenLiveActorMissing()
	{
		Assert.assertEquals("Brutus", BossNpcRegistry.resolveBossKey(15626));
		Assert.assertFalse(DeathNotifier.isKillerCandidate(null, null, false));
	}
}
