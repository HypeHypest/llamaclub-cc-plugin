package com.llamaclub.service;

import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class PluginFilterSettingsService
{
	public static final int DEFAULT_LOOT_MIN_VALUE = 1_000_000;
	public static final int DEFAULT_CLUE_MIN_VALUE = 0;
	public static final int DEFAULT_CLUE_SCREENSHOT_MIN_VALUE = 0;

	private volatile int lootMinValue = DEFAULT_LOOT_MIN_VALUE;
	private volatile int clueMinValue = DEFAULT_CLUE_MIN_VALUE;
	private volatile int clueScreenshotMinValue = DEFAULT_CLUE_SCREENSHOT_MIN_VALUE;

	public int lootMinValue()
	{
		return lootMinValue;
	}

	public int clueMinValue()
	{
		return clueMinValue;
	}

	public int clueScreenshotMinValue()
	{
		return clueScreenshotMinValue;
	}

	public void updateFromResponseBody(String responseBody)
	{
		Map<String, Object> settings = parseSettingsFromResponse(responseBody);
		if (settings == null)
		{
			return;
		}

		update(settings);
	}

	public void update(Map<String, Object> settings)
	{
		if (settings == null)
		{
			return;
		}

		Integer loot = parseInt(settings.get("lootMinValue"));
		if (loot != null && loot >= 0)
		{
			lootMinValue = loot;
		}

		Integer clue = parseInt(settings.get("clueMinValue"));
		if (clue != null && clue >= 0)
		{
			clueMinValue = clue;
		}

		Integer screenshot = parseInt(settings.get("clueScreenshotMinValue"));
		if (screenshot != null && screenshot >= 0)
		{
			clueScreenshotMinValue = screenshot;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseSettingsFromResponse(String responseBody)
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

			Object settings = parsed.get("pluginFilterSettings");
			if (!(settings instanceof Map))
			{
				return null;
			}

			return (Map<String, Object>) settings;
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
	}

	private static Integer parseInt(Object value)
	{
		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}

		if (value instanceof String)
		{
			try
			{
				return Integer.parseInt((String) value);
			}
			catch (NumberFormatException ignored)
			{
				return null;
			}
		}

		return null;
	}
}
