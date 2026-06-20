package com.llamaclub.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class BingoLootWhitelistService
{
	private volatile Set<String> cachedNames = Set.of();

	public Set<String> asSet()
	{
		return cachedNames;
	}

	public void updateFromResponseBody(String responseBody)
	{
		List<String> names = parseNamesFromResponse(responseBody);
		if (names == null)
		{
			return;
		}

		update(names);
	}

	public void update(List<String> names)
	{
		if (names == null || names.isEmpty())
		{
			cachedNames = Set.of();
			return;
		}

		Set<String> merged = names.stream()
			.filter(name -> name != null && !name.isBlank())
			.map(String::trim)
			.map(String::toLowerCase)
			.collect(Collectors.toCollection(HashSet::new));
		cachedNames = Collections.unmodifiableSet(merged);
	}

	@SuppressWarnings("unchecked")
	private List<String> parseNamesFromResponse(String responseBody)
	{
		if (responseBody == null || responseBody.isBlank())
		{
			return null;
		}

		try
		{
			com.google.gson.Gson gson = new com.google.gson.Gson();
			Map<String, Object> parsed = gson.fromJson(
				responseBody,
				new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType()
			);

			Object whitelist = parsed.get("bingoLootWhitelist");
			if (!(whitelist instanceof List))
			{
				return null;
			}

			return ((List<?>) whitelist).stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.collect(Collectors.toList());
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
	}
}
