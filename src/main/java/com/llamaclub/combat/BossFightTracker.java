package com.llamaclub.combat;

import com.llamaclub.LlamaClubConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class BossFightTracker
{
	private static final long PENDING_TTL_MILLIS = 60_000L;
	private static final long RUN_IDLE_TTL_MILLIS = 60_000L;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LlamaClubConfig config;

	private final Map<String, BossRunState> activeRuns = new HashMap<>();
	private final Map<String, CompletedFight> pendingByBossKey = new HashMap<>();

	public boolean isTrackingBossNpc(int npcIndex, String bossKey)
	{
		BossRunState run = activeRuns.get(bossKey);
		if (run == null)
		{
			return false;
		}

		return run.activeNpcIndex < 0 || run.activeNpcIndex == npcIndex;
	}

	public void ensureTracking(String bossKey, int npcIndex, long nowMillis)
	{
		BossRunState run = activeRuns.computeIfAbsent(bossKey, BossRunState::new);
		run.activeNpcIndex = npcIndex;
		run.lastActivityMillis = nowMillis;
		if (run.segmentStartMillis <= 0)
		{
			run.segmentStartMillis = nowMillis;
		}
	}

	public void recordSpecAttempt(String bossKey)
	{
		BossRunState run = activeRuns.get(bossKey);
		if (run != null)
		{
			run.stats.incrementSpecialAttacks();
			run.lastActivityMillis = System.currentTimeMillis();
		}
	}

	public void recordSpecHit(String bossKey, String weaponName, int damage)
	{
		BossRunState run = activeRuns.get(bossKey);
		if (run != null)
		{
			run.stats.addSpecHit(weaponName, damage);
			run.lastActivityMillis = System.currentTimeMillis();
		}
	}

	public boolean hasPendingFight(String bossKey)
	{
		CompletedFight pending = pendingByBossKey.get(bossKey);
		return pending != null && !isExpired(pending.getCompletedAtMillis());
	}

	public Duration peekPendingDuration(String bossKey)
	{
		CompletedFight pending = pendingByBossKey.get(bossKey);
		if (pending == null || isExpired(pending.getCompletedAtMillis()))
		{
			return null;
		}

		return pending.getDuration();
	}

	public CompletedFight consume(String bossKey)
	{
		CompletedFight pending = pendingByBossKey.remove(bossKey);
		if (pending == null || isExpired(pending.getCompletedAtMillis()))
		{
			return null;
		}

		return pending;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isEnabled())
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		String bossKey = BossNpcRegistry.resolveBossKey(npc);
		if (!BossNpcRegistry.isTrackableBossKey(bossKey))
		{
			return;
		}

		long now = System.currentTimeMillis();
		BossRunState run = activeRuns.computeIfAbsent(bossKey, key -> new BossRunState(key));
		if (run.segmentStartMillis <= 0)
		{
			run.segmentStartMillis = now;
		}

		run.activeNpcIndex = npc.getIndex();
		run.lastActivityMillis = now;

		String weaponName = getEquippedWeaponName();
		run.stats.addHit(weaponName, hitsplat.getAmount());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!isEnabled() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		String bossKey = BossNpcRegistry.resolveBossKey(npc);
		if (!BossNpcRegistry.isTrackableBossKey(bossKey))
		{
			return;
		}

		BossRunState run = activeRuns.get(bossKey);
		if (run == null || run.segmentStartMillis <= 0)
		{
			return;
		}

		long now = System.currentTimeMillis();
		run.totalDurationMillis += Math.max(0L, now - run.segmentStartMillis);
		run.segmentStartMillis = 0;
		run.activeNpcIndex = -1;
		run.lastActivityMillis = now;

		if (BossNpcRegistry.isMultiPhaseBoss(bossKey) && !BossNpcRegistry.isTerminalNpc(bossKey, npc.getId()))
		{
			log.debug("Boss {} phase death recorded, waiting for terminal phase", bossKey);
			return;
		}

		finalizeRun(bossKey, run, now);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled())
		{
			return;
		}

		long now = System.currentTimeMillis();
		expirePending(now);
		expireIdleRuns(now);
	}

	private void finalizeRun(String bossKey, BossRunState run, long nowMillis)
	{
		if (!run.stats.hasCombatData() && run.totalDurationMillis <= 0)
		{
			activeRuns.remove(bossKey);
			return;
		}

		Duration duration = Duration.ofMillis(Math.max(0L, run.totalDurationMillis));
		CompletedFight completed = new CompletedFight(bossKey, duration, run.stats, nowMillis);
		pendingByBossKey.put(bossKey, completed);
		activeRuns.remove(bossKey);

		log.debug(
			"Completed fight for {} in {}ms with {} damage",
			bossKey,
			run.totalDurationMillis,
			run.stats.getTotalDamage()
		);
	}

	private void expirePending(long nowMillis)
	{
		Iterator<Map.Entry<String, CompletedFight>> iterator = pendingByBossKey.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<String, CompletedFight> entry = iterator.next();
			if (isExpired(entry.getValue().getCompletedAtMillis(), nowMillis))
			{
				iterator.remove();
			}
		}
	}

	private void expireIdleRuns(long nowMillis)
	{
		Iterator<Map.Entry<String, BossRunState>> iterator = activeRuns.entrySet().iterator();
		while (iterator.hasNext())
		{
			BossRunState run = iterator.next().getValue();
			if (nowMillis - run.lastActivityMillis > RUN_IDLE_TTL_MILLIS)
			{
				finalizeRun(run.bossKey, run, nowMillis);
			}
		}
	}

	private String getEquippedWeaponName()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return "Unarmed";
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return "Unarmed";
		}

		return itemManager.getItemComposition(weapon.getId()).getName();
	}

	private boolean isEnabled()
	{
		return config.notifyKillCount();
	}

	private static boolean isExpired(long completedAtMillis)
	{
		return isExpired(completedAtMillis, System.currentTimeMillis());
	}

	private static boolean isExpired(long completedAtMillis, long nowMillis)
	{
		return nowMillis - completedAtMillis > PENDING_TTL_MILLIS;
	}

	public void reset()
	{
		activeRuns.clear();
		pendingByBossKey.clear();
	}

	static final class BossRunState
	{
		final String bossKey;
		final FightCombatStats stats = new FightCombatStats();
		long segmentStartMillis;
		long totalDurationMillis;
		long lastActivityMillis;
		int activeNpcIndex = -1;

		BossRunState(String bossKey)
		{
			this.bossKey = bossKey;
			this.lastActivityMillis = System.currentTimeMillis();
		}
	}

	static void applyHit(BossRunState run, String weaponName, int damage, long nowMillis)
	{
		if (run.segmentStartMillis <= 0)
		{
			run.segmentStartMillis = nowMillis;
		}

		run.lastActivityMillis = nowMillis;
		run.stats.addHit(weaponName, damage);
	}

	static CompletedFight finalizeRunState(BossRunState run, long nowMillis)
	{
		if (run.segmentStartMillis > 0)
		{
			run.totalDurationMillis += Math.max(0L, nowMillis - run.segmentStartMillis);
			run.segmentStartMillis = 0;
		}

		Duration duration = Duration.ofMillis(Math.max(0L, run.totalDurationMillis));
		return new CompletedFight(run.bossKey, duration, run.stats, nowMillis);
	}
}
