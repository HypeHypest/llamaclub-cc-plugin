/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.loot;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RecentLootDrop
{
	static final Duration RECENT_WINDOW = Duration.ofSeconds(30);

	private final String source;
	private final String sourceType;
	private final Integer sourceId;
	private final Set<Integer> itemIds;
	private final Instant time;

	public RecentLootDrop(
		String source,
		String sourceType,
		Integer sourceId,
		Set<Integer> itemIds,
		Instant time
	)
	{
		this.source = source;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.itemIds = Collections.unmodifiableSet(new HashSet<>(itemIds));
		this.time = time;
	}

	public String getSource()
	{
		return source;
	}

	public String getSourceType()
	{
		return sourceType;
	}

	public Integer getSourceId()
	{
		return sourceId;
	}

	public boolean containsItem(int itemId)
	{
		return itemIds.contains(itemId);
	}

	public boolean isExpired()
	{
		return Duration.between(time, Instant.now()).compareTo(RECENT_WINDOW) > 0;
	}
}
