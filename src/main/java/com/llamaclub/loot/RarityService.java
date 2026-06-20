/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 *
 * Bundled resource /npc_drops.json is also derived from the Dink plugin.
 */
package com.llamaclub.loot;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

@Singleton
public class RarityService extends AbstractRarityService
{
	@Inject
	public RarityService(Gson gson, ItemManager itemManager)
	{
		super("/npc_drops.json", 1024, gson, itemManager);
	}
}
