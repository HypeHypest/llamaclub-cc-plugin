/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 *
 * Bundled resource /thieving.json is also derived from the Dink plugin.
 */
package com.llamaclub.loot;

import com.google.gson.Gson;
import java.util.OptionalDouble;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

@Singleton
public class ThievingService extends AbstractRarityService
{
	@Inject
	public ThievingService(Gson gson, ItemManager itemManager)
	{
		super("/thieving.json", 32, gson, itemManager);
	}

	@Override
	public OptionalDouble getRarity(String sourceName, int itemId, int quantity)
	{
		if (itemId == ItemID.BLOOD_SHARD)
		{
			return OptionalDouble.of(1.0 / 5000);
		}

		if (itemId == ItemID.PRIF_TELEPORT_SEED)
		{
			return OptionalDouble.of(1.0 / 1024);
		}

		return super.getRarity(sourceName, itemId, quantity);
	}
}
