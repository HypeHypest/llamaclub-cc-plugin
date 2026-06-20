package com.llamaclub.combat;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Estimates server ticks until a player hitsplat lands on a target after a special attack.
 * Inspired by RuneLite's Special Attack Counter hit-delay logic.
 */
final class SpecHitDelay
{
	private static final int DEFAULT_MELEE_DELAY = 1;
	private static final int DEFAULT_RANGED_DELAY = 2;

	private SpecHitDelay()
	{
	}

	static int estimateHitDelayTicks(Client client, Actor target)
	{
		if (client == null || target == null)
		{
			return DEFAULT_MELEE_DELAY;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return DEFAULT_MELEE_DELAY;
		}

		WorldPoint playerPoint = player.getWorldLocation();
		WorldArea targetArea = target.getWorldArea();
		if (playerPoint == null || targetArea == null)
		{
			return DEFAULT_RANGED_DELAY;
		}

		int distance = targetArea.distanceTo(playerPoint);
		if (distance <= 1)
		{
			return DEFAULT_MELEE_DELAY;
		}

		return Math.max(DEFAULT_RANGED_DELAY, (distance + 5) / 6);
	}
}
