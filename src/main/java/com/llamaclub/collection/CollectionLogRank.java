/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.collection;

import net.runelite.api.Client;
import net.runelite.api.StructComposition;

public enum CollectionLogRank
{
	NONE(0),
	BRONZE(1714),
	IRON(1715),
	STEEL(1716),
	BLACK(1717),
	MITHRIL(1718),
	ADAMANT(1719),
	RUNE(1740),
	DRAGON(1741),
	GILDED(1742);

	private static final int THRESHOLD_PARAM = 2232;

	private final int structId;

	CollectionLogRank(int structId)
	{
		this.structId = structId;
	}

	public String getDisplayName()
	{
		if (this == NONE)
		{
			return "None";
		}
		return name().charAt(0) + name().substring(1).toLowerCase();
	}

	public int getClogRankThreshold(Client client, int collectionCountMax)
	{
		switch (this)
		{
			case NONE:
				return 0;

			case GILDED:
				return 25 * (int) (0.9 * collectionCountMax / 25);

			default:
				StructComposition struct = client.getStructComposition(structId);
				if (struct == null)
				{
					throw new IllegalStateException("Missing struct for rank " + name());
				}
				return struct.getIntValue(THRESHOLD_PARAM);
		}
	}
}
