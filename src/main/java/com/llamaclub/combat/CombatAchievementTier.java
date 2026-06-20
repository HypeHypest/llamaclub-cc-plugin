/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CombatAchievementTier
{
	EASY,
	MEDIUM,
	HARD,
	ELITE,
	MASTER,
	GRANDMASTER;

	public static final Map<String, CombatAchievementTier> BY_LOWER_NAME = Collections.unmodifiableMap(
		Arrays.stream(values()).collect(Collectors.toMap(t -> t.name().toLowerCase(), Function.identity()))
	);

	private final int points = ordinal() + 1;

	public int getPoints()
	{
		return points;
	}

	public String getDisplayName()
	{
		return name().charAt(0) + name().substring(1).toLowerCase();
	}
}
