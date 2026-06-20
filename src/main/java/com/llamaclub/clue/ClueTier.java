/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.clue;

import com.llamaclub.LlamaClubConfig;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ClueTier
{
	BEGINNER,
	EASY,
	MEDIUM,
	HARD,
	ELITE,
	MASTER;

	private static final Map<String, ClueTier> BY_NAME = Arrays.stream(values())
		.collect(Collectors.toMap(t -> t.name(), Function.identity()));

	public static ClueTier parse(String tier)
	{
		if (tier == null || tier.isEmpty())
		{
			return null;
		}
		return BY_NAME.get(tier.toUpperCase());
	}

	public String getConfigKey()
	{
		return "clueTier" + name().charAt(0) + name().substring(1).toLowerCase();
	}

	public String getDisplayName()
	{
		return name().charAt(0) + name().substring(1).toLowerCase();
	}

	public boolean isEnabled(LlamaClubConfig config)
	{
		switch (this)
		{
			case BEGINNER:
				return config.clueTierBeginner();
			case EASY:
				return config.clueTierEasy();
			case MEDIUM:
				return config.clueTierMedium();
			case HARD:
				return config.clueTierHard();
			case ELITE:
				return config.clueTierElite();
			case MASTER:
				return config.clueTierMaster();
			default:
				return true;
		}
	}
}
