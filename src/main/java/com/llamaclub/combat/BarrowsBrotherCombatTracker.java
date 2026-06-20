package com.llamaclub.combat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import com.llamaclub.LlamaClubConfig;
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
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class BarrowsBrotherCombatTracker
{
	static final int BROTHER_COUNT = 6;

	private static final Set<String> BROTHER_NAMES = Set.of(
		"Dharok the Wretched",
		"Ahrim the Blighted",
		"Verac the Defiled",
		"Torag the Corrupted",
		"Karil the Tainted",
		"Guthan the Infested"
	);

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LlamaClubConfig config;

	private final RunState state = new RunState();

	static boolean isBarrowsBrotherName(String name)
	{
		return name != null && BROTHER_NAMES.contains(name);
	}

	static boolean isBarrowsBrother(NPC npc)
	{
		return npc != null && isBarrowsBrotherName(npc.getName());
	}

	public int getBrotherKillCount()
	{
		return state.killedBrothers.size();
	}

	public long getTotalTimeMillis()
	{
		return state.totalTimeMillis;
	}

	public Duration getTotalDuration()
	{
		return Duration.ofMillis(state.totalTimeMillis);
	}

	public boolean hasCompleteRun()
	{
		return state.killedBrothers.size() == BROTHER_COUNT;
	}

	public boolean canTrackBrother(String brotherName)
	{
		if (brotherName == null || state.killedBrothers.contains(brotherName))
		{
			return false;
		}

		return state.activeBrotherName == null || state.activeBrotherName.equals(brotherName);
	}

	public void ensureTracking(String brotherName, long nowMillis)
	{
		beginFightOnFirstHit(state, brotherName, nowMillis);
	}

	public void recordSpecAttempt()
	{
		state.stats.incrementSpecialAttacks();
	}

	public void recordSpecHit(String weaponName, int damage)
	{
		state.stats.addSpecHit(weaponName, damage);
	}

	/**
	 * Returns aggregated run data when all six brothers were tracked, otherwise null.
	 */
	public CompletedFight consumeCompletedRun()
	{
		if (!hasCompleteRun())
		{
			log.info(
				"Barrows chest opened with incomplete run tracking ({}/{} brothers, {}ms timed)",
				state.killedBrothers.size(),
				BROTHER_COUNT,
				state.totalTimeMillis
			);
			return null;
		}

		FightCombatStats statsSnapshot = new FightCombatStats();
		statsSnapshot.mergeFrom(state.stats);

		CompletedFight completed = new CompletedFight(
			BossNpcRegistry.BARROWS_CHESTS,
			getTotalDuration(),
			statsSnapshot,
			System.currentTimeMillis()
		);

		log.info(
			"Barrows run complete: {}ms, {} damage, {} hits, {} specs",
			state.totalTimeMillis,
			state.stats.getTotalDamage(),
			state.stats.getHitCount(),
			state.stats.getSpecialAttacks()
		);

		reset();
		return completed;
	}

	public void reset()
	{
		state.reset();
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!config.notifyKillCount())
		{
			return;
		}

		Actor source = event.getSource();
		Actor target = event.getTarget();
		NPC brother = null;

		if (source == client.getLocalPlayer() && target instanceof NPC)
		{
			brother = (NPC) target;
		}
		else if (target == client.getLocalPlayer() && source instanceof NPC)
		{
			brother = (NPC) source;
		}

		if (!isBarrowsBrother(brother))
		{
			return;
		}

		tryStartFight(brother.getName());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!config.notifyKillCount())
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine())
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		if (!isBarrowsBrother(npc))
		{
			return;
		}

		String brotherName = npc.getName();
		if (!canTrackBrother(brotherName))
		{
			return;
		}

		tryStartFight(brotherName);
		state.stats.addHit(getEquippedWeaponName(), hitsplat.getAmount());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!config.notifyKillCount())
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		if (!isBarrowsBrother(npc))
		{
			return;
		}

		recordBrotherKill(state, npc.getName(), System.currentTimeMillis());
	}

	private void tryStartFight(String brotherName)
	{
		if (!canTrackBrother(brotherName))
		{
			return;
		}

		beginFightOnFirstHit(state, brotherName, System.currentTimeMillis());
	}

	static void beginFightOnFirstHit(RunState state, String brotherName, long nowMillis)
	{
		if (brotherName == null || state.killedBrothers.contains(brotherName))
		{
			return;
		}

		if (state.killedBrothers.size() >= BROTHER_COUNT)
		{
			return;
		}

		if (brotherName.equals(state.activeBrotherName) && state.fightStartMillis > 0)
		{
			return;
		}

		if (state.activeBrotherName != null && !state.activeBrotherName.equals(brotherName))
		{
			return;
		}

		state.fightStartMillis = nowMillis;
		state.activeBrotherName = brotherName;
	}

	static boolean recordBrotherKill(RunState state, String brotherName, long nowMillis)
	{
		if (brotherName == null || state.killedBrothers.contains(brotherName))
		{
			return false;
		}

		if (state.killedBrothers.size() >= BROTHER_COUNT)
		{
			return false;
		}

		if (state.activeBrotherName == null)
		{
			log.debug("Barrows brother {} died without a tracked fight start", brotherName);
			return false;
		}

		if (!state.activeBrotherName.equals(brotherName))
		{
			log.debug(
				"Barrows brother death ignored due to name mismatch (active={}, dead={})",
				state.activeBrotherName,
				brotherName
			);
			return false;
		}

		long durationMillis = Math.max(0L, nowMillis - state.fightStartMillis);
		state.totalTimeMillis += durationMillis;
		state.killedBrothers.add(brotherName);
		state.fightStartMillis = 0;
		state.activeBrotherName = null;

		log.debug(
			"Barrows brother {} killed in {}ms (run total {}ms, {}/{} brothers)",
			brotherName,
			durationMillis,
			state.totalTimeMillis,
			state.killedBrothers.size(),
			BROTHER_COUNT
		);
		return true;
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

	static final class RunState
	{
		long fightStartMillis;
		String activeBrotherName;
		final Set<String> killedBrothers = new HashSet<>();
		long totalTimeMillis;
		final FightCombatStats stats = new FightCombatStats();

		void reset()
		{
			fightStartMillis = 0;
			activeBrotherName = null;
			killedBrothers.clear();
			totalTimeMillis = 0;
			stats.clear();
		}
	}
}
