/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.combat.BossNpcRegistry;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SkullIcon;
import net.runelite.api.WorldType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
@Singleton
public class DeathNotifier extends BaseNotifier
{
	private static final String ATTACK_OPTION = "Attack";
	private static final long ATTACK_TIMEOUT_MS = 10_000L;
	private static final int NEARBY_KILLER_SEARCH_DISTANCE = 20;

	@Inject
	private ItemManager itemManager;

	private Actor lastAttacker = null;
	private long lastAttackTime = 0;
	private WeakReference<Actor> lastTarget = new WeakReference<>(null);
	private TrackedNpc lastCombatNpc = null;

	@Override
	public boolean isEnabled()
	{
		return config.notifyDeath();
	}

	@Override
	protected String getEventKind()
	{
		return "DEATH";
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!isEnabled())
		{
			return;
		}

		Actor source = event.getSource();
		Actor target = event.getTarget();
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		if (target == localPlayer && source instanceof NPC)
		{
			trackCombatNpc((NPC) source);
		}

		if (source == localPlayer && target instanceof NPC)
		{
			NPC npc = (NPC) target;
			lastTarget = new WeakReference<>(npc);
			trackCombatNpc(npc);
		}
		else if (source == localPlayer && isTrackableCombatTarget(target))
		{
			lastTarget = new WeakReference<>(target);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isEnabled() || event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat.isMine() || hitsplat.getAmount() <= 0)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		Actor playerTarget = localPlayer.getInteracting();
		if (playerTarget instanceof NPC)
		{
			trackCombatNpc((NPC) playerTarget);
		}

		NPC bestNpc = findBestNpcKiller(localPlayer);
		if (bestNpc != null)
		{
			trackCombatNpc(bestNpc);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!isEnabled())
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		handleDeath();

		if (actor == lastTarget.get())
		{
			lastTarget = new WeakReference<>(null);
		}
	}

	private void handleDeath()
	{
		sendEvent(buildDeathData());
		reset();
	}

	private void trackCombatNpc(NPC npc)
	{
		if (!isNpcKillerCandidate(npc))
		{
			return;
		}

		lastAttacker = npc;
		lastAttackTime = System.currentTimeMillis();
		lastCombatNpc = new TrackedNpc(
			npc.getId(),
			resolveNpcKillerName(npc),
			lastAttackTime
		);
	}

	private Map<String, Object> buildDeathData()
	{
		Map<String, Object> deathData = new HashMap<>();
		Actor killer = identifyKiller();

		String killerName = "Unknown";
		String killerType = "UNKNOWN";
		if (killer instanceof NPC)
		{
			NPC npc = (NPC) killer;
			killerName = resolveNpcKillerName(npc);
			killerType = "NPC";
			deathData.put("killerId", npc.getId());
			deathData.put("killerCombatLevel", npc.getCombatLevel());
		}
		else if (killer instanceof Player)
		{
			Player player = (Player) killer;
			killerName = player.getName();
			killerType = "PLAYER";
			deathData.put("killerCombatLevel", player.getCombatLevel());
		}
		else if (isRecentCombat(lastCombatNpc))
		{
			killerName = lastCombatNpc.name;
			killerType = "NPC";
			deathData.put("killerId", lastCombatNpc.npcId);
			deathData.put("killerName", killerName);
			deathData.put("killedBy", killerName);
			deathData.put("killerType", killerType);
			deathData.put("source", killerName);
			deathData.put("sourceType", killerType);
			log.debug(
				"Resolved death killer from tracked combat snapshot: {} ({})",
				killerName,
				lastCombatNpc.npcId
			);
		}

		if (!deathData.containsKey("killerName"))
		{
			deathData.put("killerName", killerName);
			deathData.put("killedBy", killerName);
			deathData.put("killerType", killerType);
			deathData.put("source", killerName);
			deathData.put("sourceType", killerType);
		}

		if ("Unknown".equals(killerName))
		{
			log.debug(
				"Could not resolve death killer (lastCombatNpc={}, lastAttacker={}, lastTarget={})",
				lastCombatNpc,
				lastAttacker,
				lastTarget.get()
			);
		}

		if (client.getLocalPlayer() != null)
		{
			deathData.put(
				"location",
				client.getLocalPlayer().getWorldLocation().getX() + ","
					+ client.getLocalPlayer().getWorldLocation().getY()
			);
		}

		Set<WorldType> worldTypes = client.getWorldType();
		deathData.put("isPvpWorld", worldTypes.contains(WorldType.PVP));
		deathData.put("isHighRiskWorld", worldTypes.contains(WorldType.HIGH_RISK));

		List<Map<String, Object>> allItems = getAllPricedItems();
		int keepCount = getKeepCount();

		List<Map<String, Object>> keptItems = new ArrayList<>();
		List<Map<String, Object>> lostItems = new ArrayList<>();
		long totalLostValue = 0;

		for (int i = 0; i < allItems.size(); i++)
		{
			Map<String, Object> item = allItems.get(i);
			if (i < keepCount)
			{
				keptItems.add(item);
			}
			else
			{
				lostItems.add(item);
				totalLostValue += ((Number) item.get("gePrice")).longValue() * (int) item.get("quantity");
			}
		}

		deathData.put("keptItems", keptItems);
		deathData.put("lostItems", lostItems);
		deathData.put("totalLostValue", totalLostValue);

		return deathData;
	}

	private int getKeepCount()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return 3;
		}

		int keepCount = localPlayer.getSkullIcon() == SkullIcon.NONE ? 3 : 0;
		if (client.isPrayerActive(Prayer.PROTECT_ITEM))
		{
			keepCount++;
		}

		return keepCount;
	}

	private List<Map<String, Object>> getAllPricedItems()
	{
		List<Map<String, Object>> items = new ArrayList<>();
		collectItemsFromContainer(InventoryID.INVENTORY, items);
		collectItemsFromContainer(InventoryID.EQUIPMENT, items);
		items.sort(Comparator.<Map<String, Object>>comparingLong(m -> ((Number) m.get("gePrice")).longValue()).reversed());
		return items;
	}

	private void collectItemsFromContainer(InventoryID containerId, List<Map<String, Object>> items)
	{
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return;
		}

		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			int itemId = item.getId();
			long gePrice = itemManager.getItemPrice(itemId);
			ItemComposition composition = itemManager.getItemComposition(itemId);

			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			itemData.put("name", composition != null ? composition.getName() : "Unknown");
			itemData.put("quantity", item.getQuantity());
			itemData.put("gePrice", gePrice);
			items.add(itemData);
		}
	}

	private Actor identifyKiller()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		Actor target = lastTarget.get();
		if (isKillerCandidate(target, localPlayer, false))
		{
			return target;
		}

		if (lastAttacker != null && isRecentCombat(lastAttackTime))
		{
			if (isKillerCandidate(lastAttacker, localPlayer, false))
			{
				return lastAttacker;
			}
		}

		NPC combatNpc = findBestNpcKiller(localPlayer);
		if (combatNpc != null)
		{
			return combatNpc;
		}

		if (isRecentCombat(lastAttackTime))
		{
			return findNearbyCombatNpc(localPlayer);
		}

		return null;
	}

	private NPC findBestNpcKiller(Player localPlayer)
	{
		return client.getTopLevelWorldView().npcs().stream()
			.filter(npc -> npc != null && !npc.isDead())
			.filter(npc -> isInCombatWith(localPlayer, npc))
			.filter(DeathNotifier::isNpcKillerCandidate)
			.max(npcKillerComparator(localPlayer))
			.orElse(null);
	}

	private NPC findNearbyCombatNpc(Player localPlayer)
	{
		return client.getTopLevelWorldView().npcs().stream()
			.filter(npc -> npc != null && !npc.isDead())
			.filter(npc -> localPlayer.getLocalLocation().distanceTo(npc.getLocalLocation()) <= NEARBY_KILLER_SEARCH_DISTANCE)
			.filter(DeathNotifier::isNpcKillerCandidate)
			.min(Comparator.comparingInt(npc -> localPlayer.getLocalLocation().distanceTo(npc.getLocalLocation())))
			.orElse(null);
	}

	private static boolean isInCombatWith(Player localPlayer, NPC npc)
	{
		return npc.getInteracting() == localPlayer || localPlayer.getInteracting() == npc;
	}

	private static boolean isRecentCombat(long trackedAt)
	{
		return trackedAt > 0 && (System.currentTimeMillis() - trackedAt) < ATTACK_TIMEOUT_MS;
	}

	private static boolean isRecentCombat(TrackedNpc trackedNpc)
	{
		return trackedNpc != null && isRecentCombat(trackedNpc.trackedAt);
	}

	private Comparator<NPC> npcKillerComparator(Player localPlayer)
	{
		return Comparator
			.comparing((NPC npc) -> BossNpcRegistry.resolveBossKey(npc) != null)
			.thenComparing(npc -> hasBossHitpointsName(npc.getTransformedComposition()))
			.thenComparing(npc -> hasAttackAction(npc.getTransformedComposition()))
			.thenComparingInt(NPC::getCombatLevel)
			.thenComparingInt(npc -> -localPlayer.getLocalLocation().distanceTo(npc.getLocalLocation()));
	}

	private static String resolveNpcKillerName(NPC npc)
	{
		String name = BossNpcRegistry.npcDisplayName(npc);
		if (name != null && !name.isEmpty())
		{
			return name;
		}

		NPCComposition composition = npc.getTransformedComposition();
		if (composition != null)
		{
			String hpName = composition.getStringValue(ParamID.NPC_HP_NAME);
			if (hpName != null && !hpName.isEmpty())
			{
				return hpName;
			}
		}

		String bossKey = BossNpcRegistry.resolveBossKey(npc);
		if (bossKey != null)
		{
			return bossKey;
		}

		String bossKeyById = BossNpcRegistry.resolveBossKey(npc.getId());
		return bossKeyById != null ? bossKeyById : "Unknown";
	}

	static boolean isTrackableCombatTarget(Actor target)
	{
		if (target == null || target.isDead())
		{
			return false;
		}

		if (target instanceof NPC)
		{
			return isNpcKillerCandidate((NPC) target);
		}

		return target.getCombatLevel() > 0;
	}

	static boolean isKillerCandidate(Actor actor, Player localPlayer, boolean requireInteracting)
	{
		if (actor == null || actor.isDead())
		{
			return false;
		}

		if (requireInteracting && actor instanceof NPC && !isInCombatWith(localPlayer, (NPC) actor))
		{
			return false;
		}

		if (actor instanceof Player)
		{
			return true;
		}

		if (actor instanceof NPC)
		{
			return isNpcKillerCandidate((NPC) actor);
		}

		return false;
	}

	static boolean isNpcKillerCandidate(NPC npc)
	{
		if (npc == null || npc.isDead())
		{
			return false;
		}

		if (BossNpcRegistry.resolveBossKey(npc) != null)
		{
			return true;
		}

		if (BossNpcRegistry.resolveBossKey(npc.getId()) != null)
		{
			return true;
		}

		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			composition = npc.getComposition();
		}

		if (composition == null || composition.isFollower())
		{
			return false;
		}

		if (hasBossHitpointsName(composition))
		{
			return true;
		}

		return composition.isInteractible()
			&& composition.getCombatLevel() > 0
			&& hasAttackAction(composition);
	}

	static boolean hasBossHitpointsName(NPCComposition composition)
	{
		if (composition == null)
		{
			return false;
		}

		String hpName = composition.getStringValue(ParamID.NPC_HP_NAME);
		return hpName != null && !hpName.isEmpty();
	}

	static boolean hasAttackAction(NPCComposition composition)
	{
		if (composition == null)
		{
			return false;
		}

		return ArrayUtils.contains(composition.getActions(), ATTACK_OPTION);
	}

	private void reset()
	{
		lastAttacker = null;
		lastAttackTime = 0;
		lastTarget = new WeakReference<>(null);
		lastCombatNpc = null;
	}

	private static final class TrackedNpc
	{
		private final int npcId;
		private final String name;
		private final long trackedAt;

		private TrackedNpc(int npcId, String name, long trackedAt)
		{
			this.npcId = npcId;
			this.name = name;
			this.trackedAt = trackedAt;
		}

		@Override
		public String toString()
		{
			return name + " (" + npcId + ")";
		}
	}
}
