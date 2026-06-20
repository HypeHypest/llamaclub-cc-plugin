/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.loot;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Singleton
public class RecentLootTracker
{
	private static final Set<Integer> SPECIAL_LOOT_NPC_IDS = Set.of(
		NpcID.WHISPERER, NpcID.WHISPERER_MELEE, NpcID.WHISPERER_QUEST, NpcID.WHISPERER_MELEE_QUEST,
		NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD, NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING_INACTIVE,
		NpcID.YAMA,
		NpcID.HESPORI,
		NpcID.GRYPHON_BOSS,
		NpcID.GB_HILLGIANT_CHEST,
		NpcID.GB_MOSSGIANT_CHEST
	);

	private static final Set<String> SPECIAL_LOOT_NPC_NAMES = Set.of(
		"The Whisperer", "Araxxor",
		"Branda the Fire Queen", "Eldric the Ice King",
		"Crystalline Hunllef", "Corrupted Hunllef",
		"The Gauntlet", "Corrupted Gauntlet",
		"Shellbane gryphon",
		"Obor (Chest)",
		"Bryophyta (Chest)"
	);

	private volatile RecentLootDrop lastDrop;

	public RecentLootDrop findDropContaining(int itemId)
	{
		RecentLootDrop drop = lastDrop;
		if (drop == null || drop.isExpired() || !drop.containsItem(itemId))
		{
			return null;
		}
		return drop;
	}

	public void record(Collection<ItemStack> items, String source, String sourceType, Integer sourceId)
	{
		Set<Integer> itemIds = new HashSet<>();
		for (ItemStack stack : items)
		{
			if (stack.getId() > 0 && stack.getQuantity() > 0)
			{
				itemIds.add(stack.getId());
			}
		}

		if (itemIds.isEmpty())
		{
			return;
		}

		lastDrop = new RecentLootDrop(source, sourceType, sourceId, itemIds, Instant.now());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		int npcId = event.getComposition().getId();
		String name = event.getComposition().getName();

		if (npcId != NpcID.YAMA && npcId != NpcID.HESPORI && !name.startsWith("Hallowed Sepulchre"))
		{
			return;
		}

		record(event.getItems(), name, "NPC", npcId);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}

		int npcId = npc.getId();
		if (SPECIAL_LOOT_NPC_IDS.contains(npcId))
		{
			return;
		}

		String sourceName = "Unknown";
		NPCComposition composition = npc.getComposition();
		if (composition != null)
		{
			sourceName = composition.getName();
		}

		record(event.getItems(), sourceName, "NPC", npcId);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		record(event.getItems(), event.getPlayer().getName(), "PLAYER", null);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.EVENT || event.getType() == LootRecordType.PICKPOCKET)
		{
			record(event.getItems(), event.getName(), "EVENT", null);
		}
		else if (event.getType() == LootRecordType.NPC && SPECIAL_LOOT_NPC_NAMES.contains(event.getName()))
		{
			String source = event.getName();
			if ("The Gauntlet".equals(source) || "Corrupted Gauntlet".equals(source))
			{
				record(event.getItems(), source, "EVENT", null);
			}
			else
			{
				record(event.getItems(), source, "NPC", null);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();
		if ("You have found the Pharaoh's sceptre!".equals(message)
			|| "You have found a Pharaoh's sceptre!".equals(message))
		{
			record(
				java.util.List.of(new ItemStack(ItemID.PHARAOHS_SCEPTRE, 1)),
				"Pyramid Plunder",
				"EVENT",
				null
			);
		}
	}
}
