/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.pet;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PetItems
{
	private static final Set<String> PET_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"abyssal orphan", "baby mole", "baron", "bran", "butch", "callisto cub", "dom", "gull",
		"hellpuppy", "huberte", "ikkle hydra", "jal-nib-rek", "kalphite princess", "lil' zik",
		"lil'viathan", "little nightmare", "moxi", "muphin", "nexling", "nid", "noon", "olmlet",
		"pet chaos elemental", "pet dagannoth prime", "pet dagannoth rex", "pet dagannoth supreme",
		"pet dark core", "pet general graardor", "pet k'ril tsutsaroth", "pet kraken", "pet kree'arra",
		"pet smoke devil", "pet snakeling", "pet zilyana", "phoenix", "prince black dragon",
		"scorpia's offspring", "scurry", "skotos", "smolcano", "smol heredit", "sraracha",
		"tiny tempor", "tumeken's guardian", "tzrek-jad", "venenatis spiderling", "vet'ion jr.",
		"vorki", "wisp", "yami", "youngllef",
		"baby chinchompa", "beaver", "giant squirrel", "heron", "rift guardian", "rock golem",
		"rocky", "soup", "tangleroot",
		"abyssal protector", "bloodhound", "chompy chick", "herbi", "lil' creator",
		"pet penance queen", "quetzin"
	)));

	private PetItems()
	{
	}

	public static boolean isPet(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return false;
		}

		String normalized = itemName.toLowerCase().trim();
		if (PET_NAMES.contains(normalized))
		{
			return true;
		}

		if (normalized.startsWith("pet "))
		{
			String withoutPrefix = normalized.substring(4).trim();
			return PET_NAMES.contains(withoutPrefix) || PET_NAMES.contains(normalized);
		}

		return normalized.contains("pet ");
	}
}
