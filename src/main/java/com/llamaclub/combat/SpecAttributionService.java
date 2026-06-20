package com.llamaclub.combat;

import com.llamaclub.LlamaClubConfig;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Attributes special attack attempts and confirmed spec hitsplats to the active boss fight.
 * Inspired by RuneLite's Special Attack Counter (SA energy drop + projected hitsplat tick).
 */
@Slf4j
@Singleton
public class SpecAttributionService
{
	private static final int SPEC_GRACE_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LlamaClubConfig config;

	@Inject
	private BossFightTracker bossFightTracker;

	@Inject
	private BarrowsBrotherCombatTracker barrowsBrotherCombatTracker;

	@Inject
	private LunarMoonCombatTracker lunarMoonCombatTracker;

	private int previousSpecEnergy = -1;
	private final List<PendingSpec> pendingSpecs = new ArrayList<>();
	private final List<TickHitsplat> tickHitsplats = new ArrayList<>();

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!isEnabled() || event.getVarpId() != VarPlayerID.SA_ENERGY)
		{
			return;
		}

		int currentEnergy = event.getValue();
		if (previousSpecEnergy < 0 || currentEnergy >= previousSpecEnergy)
		{
			previousSpecEnergy = currentEnergy;
			return;
		}

		previousSpecEnergy = currentEnergy;
		final int serverTick = client.getTickCount();

		clientThread.invokeLater(() -> handleSpecUsed(serverTick));
	}

	private void handleSpecUsed(int serverTick)
	{
		WeaponSnapshot weapon = getEquippedWeapon();
		if (weapon == null)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		Actor target = localPlayer.getInteracting();
		if (!(target instanceof NPC))
		{
			return;
		}

		NPC targetNpc = (NPC) target;
		long nowMillis = System.currentTimeMillis();
		String bossKey;

		if (BarrowsBrotherCombatTracker.isBarrowsBrother(targetNpc))
		{
			String brotherName = targetNpc.getName();
			if (!barrowsBrotherCombatTracker.canTrackBrother(brotherName))
			{
				return;
			}

			barrowsBrotherCombatTracker.ensureTracking(brotherName, nowMillis);
			barrowsBrotherCombatTracker.recordSpecAttempt();
			bossKey = BossNpcRegistry.BARROWS_CHESTS;
		}
		else if (LunarMoonCombatTracker.isLunarMoon(targetNpc))
		{
			String moonName = LunarMoonCombatTracker.resolveMoonName(targetNpc);
			if (moonName == null || !lunarMoonCombatTracker.canTrackMoon(moonName))
			{
				return;
			}

			lunarMoonCombatTracker.ensureTracking(moonName, nowMillis);
			lunarMoonCombatTracker.recordSpecAttempt();
			bossKey = BossNpcRegistry.LUNAR_CHEST;
		}
		else
		{
			bossKey = BossNpcRegistry.resolveBossKey(targetNpc);
			if (!BossNpcRegistry.isTrackableBossKey(bossKey))
			{
				return;
			}

			if (!bossFightTracker.isTrackingBossNpc(targetNpc.getIndex(), bossKey))
			{
				bossFightTracker.ensureTracking(bossKey, targetNpc.getIndex(), nowMillis);
			}

			bossFightTracker.recordSpecAttempt(bossKey);
		}

		int hitsplatTick = serverTick + SpecHitDelay.estimateHitDelayTicks(client, targetNpc);
		pendingSpecs.add(new PendingSpec(
			weapon.weaponId,
			weapon.weaponName,
			targetNpc.getIndex(),
			bossKey,
			hitsplatTick,
			hitsplatTick + SPEC_GRACE_TICKS
		));

		log.debug(
			"Spec attempt tracked for {} with {} (hitsplat tick {})",
			bossKey,
			weapon.weaponName,
			hitsplatTick
		);
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
		int tick = client.getTickCount();
		tickHitsplats.add(new TickHitsplat(tick, npc.getIndex(), hitsplat.getAmount()));
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled())
		{
			return;
		}

		int tick = client.getTickCount();
		resolvePendingSpecs(tick);
		expirePendingSpecs(tick);
		tickHitsplats.clear();
	}

	private void resolvePendingSpecs(int tick)
	{
		for (PendingSpec pending : pendingSpecs)
		{
			if (tick < pending.hitsplatTick || tick > pending.expireTick)
			{
				continue;
			}

			Integer damage = lastHitsplatDamage(tick, pending.targetNpcIndex);
			if (damage == null)
			{
				continue;
			}

			if (BossNpcRegistry.BARROWS_CHESTS.equals(pending.bossKey))
			{
				barrowsBrotherCombatTracker.recordSpecHit(pending.weaponName, damage);
			}
			else if (BossNpcRegistry.LUNAR_CHEST.equals(pending.bossKey))
			{
				lunarMoonCombatTracker.recordSpecHit(pending.weaponName, damage);
			}
			else
			{
				bossFightTracker.recordSpecHit(pending.bossKey, pending.weaponName, damage);
			}

			pending.resolved = true;
		}

		pendingSpecs.removeIf(spec -> spec.resolved);
	}

	private void expirePendingSpecs(int tick)
	{
		pendingSpecs.removeIf(spec -> tick > spec.expireTick);
	}

	private Integer lastHitsplatDamage(int tick, int targetNpcIndex)
	{
		Integer damage = null;
		for (TickHitsplat capture : tickHitsplats)
		{
			if (capture.tick == tick && capture.targetNpcIndex == targetNpcIndex)
			{
				damage = capture.damage;
			}
		}
		return damage;
	}

	private WeaponSnapshot getEquippedWeapon()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return null;
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return new WeaponSnapshot(-1, "Unarmed");
		}

		int weaponId = weapon.getId();
		String weaponName = itemManager.getItemComposition(weaponId).getName();
		return new WeaponSnapshot(weaponId, weaponName);
	}

	private boolean isEnabled()
	{
		return config.notifyKillCount();
	}

	public void reset()
	{
		previousSpecEnergy = -1;
		pendingSpecs.clear();
		tickHitsplats.clear();
	}

	static final class PendingSpec
	{
		final int weaponId;
		final String weaponName;
		final int targetNpcIndex;
		final String bossKey;
		final int hitsplatTick;
		final int expireTick;
		boolean resolved;

		PendingSpec(
			int weaponId,
			String weaponName,
			int targetNpcIndex,
			String bossKey,
			int hitsplatTick,
			int expireTick
		)
		{
			this.weaponId = weaponId;
			this.weaponName = weaponName;
			this.targetNpcIndex = targetNpcIndex;
			this.bossKey = bossKey;
			this.hitsplatTick = hitsplatTick;
			this.expireTick = expireTick;
		}
	}

	private static final class TickHitsplat
	{
		final int tick;
		final int targetNpcIndex;
		final int damage;

		TickHitsplat(int tick, int targetNpcIndex, int damage)
		{
			this.tick = tick;
			this.targetNpcIndex = targetNpcIndex;
			this.damage = damage;
		}
	}

	private static final class WeaponSnapshot
	{
		final int weaponId;
		final String weaponName;

		WeaponSnapshot(int weaponId, String weaponName)
		{
			this.weaponId = weaponId;
			this.weaponName = weaponName;
		}
	}
}
