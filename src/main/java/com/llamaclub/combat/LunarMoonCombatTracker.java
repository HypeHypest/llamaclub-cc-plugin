package com.llamaclub.combat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.llamaclub.LlamaClubConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class LunarMoonCombatTracker
{
	static final int MOON_COUNT = 3;

	private static final Map<Integer, String> MOON_NPC_IDS = Map.of(
		NpcID.PMOON_BOSS_BLUE_MOON, "Blue Moon",
		NpcID.PMOON_BOSS_BLOOD_MOON, "Blood Moon",
		NpcID.PMOON_BOSS_ECLIPSE_MOON, "Eclipse Moon"
	);

	private static final Set<String> MOON_NAMES = Set.copyOf(MOON_NPC_IDS.values());

	private static final Pattern MOON_DISTRACTED_PATTERN = Pattern.compile(
		"The (Blue Moon|Blood Moon|Eclipse Moon)(?: of Peril)? is sufficiently distracted",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LlamaClubConfig config;

	private final RunState state = new RunState();

	static boolean isLunarMoonName(String name)
	{
		return name != null && MOON_NAMES.contains(name);
	}

	static boolean isLunarMoon(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}

		if (MOON_NPC_IDS.containsKey(npc.getId()))
		{
			return true;
		}

		return isLunarMoonName(npc.getName());
	}

	static String resolveMoonName(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}

		String byId = MOON_NPC_IDS.get(npc.getId());
		if (byId != null)
		{
			return byId;
		}

		String name = npc.getName();
		return isLunarMoonName(name) ? name : null;
	}

	public int getMoonKillCount()
	{
		return state.killedMoons.size();
	}

	public Duration getTotalDuration()
	{
		return Duration.ofMillis(state.totalTimeMillis);
	}

	public boolean canTrackMoon(String moonName)
	{
		if (moonName == null || state.killedMoons.contains(moonName))
		{
			return false;
		}

		return state.activeMoonName == null || state.activeMoonName.equals(moonName);
	}

	public void ensureTracking(String moonName, long nowMillis)
	{
		beginFightOnFirstHit(state, moonName, nowMillis);
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
	 * Returns aggregated run data when at least one moon was tracked, otherwise null.
	 */
	public LunarRunResult consumeRun()
	{
		int moonsKilled = state.killedMoons.size();
		if (moonsKilled <= 0)
		{
			log.info("Lunar chest opened with no tracked moon fights");
			reset();
			return null;
		}

		FightCombatStats statsSnapshot = new FightCombatStats();
		statsSnapshot.mergeFrom(state.stats);

		CompletedFight completed = new CompletedFight(
			BossNpcRegistry.LUNAR_CHEST,
			getTotalDuration(),
			statsSnapshot,
			System.currentTimeMillis()
		);

		log.info(
			"Lunar run complete: {}/{} moons, {}ms, {} damage, {} hits, {} specs",
			moonsKilled,
			MOON_COUNT,
			state.totalTimeMillis,
			state.stats.getTotalDamage(),
			state.stats.getHitCount(),
			state.stats.getSpecialAttacks()
		);

		LunarRunResult result = new LunarRunResult(moonsKilled, completed);
		reset();
		return result;
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
		NPC moon = null;

		if (source == client.getLocalPlayer() && target instanceof NPC)
		{
			moon = (NPC) target;
		}
		else if (target == client.getLocalPlayer() && source instanceof NPC)
		{
			moon = (NPC) source;
		}

		if (!isLunarMoon(moon))
		{
			return;
		}

		String moonName = resolveMoonName(moon);
		if (moonName != null)
		{
			tryStartFight(moonName);
		}
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
		if (!isLunarMoon(npc))
		{
			return;
		}

		String moonName = resolveMoonName(npc);
		if (moonName == null || !canTrackMoon(moonName))
		{
			return;
		}

		tryStartFight(moonName);
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
		if (!isLunarMoon(npc))
		{
			return;
		}

		String moonName = resolveMoonName(npc);
		if (moonName != null)
		{
			completeMoonFromDialogue(state, moonName, System.currentTimeMillis());
		}
	}

	/**
	 * Moons of Peril bosses are subdued rather than killed; they despawn when sufficiently distracted.
	 */
	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!config.notifyKillCount())
		{
			return;
		}

		NPC npc = event.getNpc();
		if (!isLunarMoon(npc))
		{
			return;
		}

		String moonName = resolveMoonName(npc);
		if (moonName != null)
		{
			completeMoonFromDialogue(state, moonName, System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyKillCount())
		{
			return;
		}

		if (event.getType() != ChatMessageType.NPC_SAY)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		Matcher matcher = MOON_DISTRACTED_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return;
		}

		String moonName = normalizeMoonName(matcher.group(1));
		if (moonName != null)
		{
			completeMoonFromDialogue(state, moonName, System.currentTimeMillis());
		}
	}

	private void tryStartFight(String moonName)
	{
		if (!canTrackMoon(moonName))
		{
			return;
		}

		beginFightOnFirstHit(state, moonName, System.currentTimeMillis());
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

	static void beginFightOnFirstHit(RunState state, String moonName, long nowMillis)
	{
		if (moonName == null || state.killedMoons.contains(moonName))
		{
			return;
		}

		if (state.killedMoons.size() >= MOON_COUNT)
		{
			return;
		}

		if (moonName.equals(state.activeMoonName) && state.fightStartMillis > 0)
		{
			return;
		}

		if (state.activeMoonName != null && !state.activeMoonName.equals(moonName))
		{
			return;
		}

		state.fightStartMillis = nowMillis;
		state.activeMoonName = moonName;
	}

	static boolean recordMoonKill(RunState state, String moonName, long nowMillis)
	{
		if (moonName == null || state.killedMoons.contains(moonName))
		{
			return false;
		}

		if (state.killedMoons.size() >= MOON_COUNT)
		{
			return false;
		}

		if (state.activeMoonName == null)
		{
			log.debug("Lunar moon {} died without a tracked fight start", moonName);
			return false;
		}

		if (!state.activeMoonName.equals(moonName))
		{
			log.debug(
				"Lunar moon death ignored due to name mismatch (active={}, dead={})",
				state.activeMoonName,
				moonName
			);
			return false;
		}

		long durationMillis = Math.max(0L, nowMillis - state.fightStartMillis);
		state.totalTimeMillis += durationMillis;
		state.killedMoons.add(moonName);
		state.fightStartMillis = 0;
		state.activeMoonName = null;

		log.info(
			"Lunar moon {} subdued in {}ms (run total {}ms, {}/{} moons)",
			moonName,
			durationMillis,
			state.totalTimeMillis,
			state.killedMoons.size(),
			MOON_COUNT
		);
		return true;
	}

	static String normalizeMoonName(String raw)
	{
		if (raw == null)
		{
			return null;
		}

		for (String moonName : MOON_NAMES)
		{
			if (moonName.equalsIgnoreCase(raw))
			{
				return moonName;
			}
		}

		return null;
	}

	/**
	 * Eyatlalli confirms a moon was subdued; used when {@link ActorDeath} / {@link NpcDespawned} do not fire.
	 */
	static boolean completeMoonFromDialogue(RunState state, String moonName, long nowMillis)
	{
		if (moonName == null || state.killedMoons.contains(moonName))
		{
			return false;
		}

		if (state.activeMoonName != null && !state.activeMoonName.equals(moonName))
		{
			state.activeMoonName = null;
			state.fightStartMillis = 0;
		}

		if (state.activeMoonName == null)
		{
			beginFightOnFirstHit(state, moonName, nowMillis);
		}

		return recordMoonKill(state, moonName, nowMillis);
	}

	@Value
	public static class LunarRunResult
	{
		int moonsKilled;
		CompletedFight completedFight;
	}

	static final class RunState
	{
		long fightStartMillis;
		String activeMoonName;
		final Set<String> killedMoons = new HashSet<>();
		long totalTimeMillis;
		final FightCombatStats stats = new FightCombatStats();

		void reset()
		{
			fightStartMillis = 0;
			activeMoonName = null;
			killedMoons.clear();
			totalTimeMillis = 0;
			stats.clear();
		}
	}
}
