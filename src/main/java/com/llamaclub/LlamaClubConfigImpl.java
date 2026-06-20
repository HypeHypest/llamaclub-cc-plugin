package com.llamaclub;

import com.llamaclub.service.PluginFilterSettingsService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
class LlamaClubConfigImpl implements LlamaClubConfig
{
	private static final String FIXED_WEBHOOK_URL = "https://llamaclub.co.uk/api/runelite/ingest";
	private static final String FIXED_WEBSITE_URL = "https://llamaclub.co.uk";

	private final ConfigManager configManager;
	private final PluginFilterSettingsService pluginFilterSettingsService;

	@Inject
	LlamaClubConfigImpl(ConfigManager configManager, PluginFilterSettingsService pluginFilterSettingsService)
	{
		this.configManager = configManager;
		this.pluginFilterSettingsService = pluginFilterSettingsService;
	}

	@Override
	public boolean webhookEnabled()
	{
		return true;
	}

	@Override
	public String webhookUrl()
	{
		return FIXED_WEBHOOK_URL;
	}

	@Override
	public String pluginToken()
	{
		return getString("pluginToken", "");
	}

	@Override
	public String websiteUrl()
	{
		return FIXED_WEBSITE_URL;
	}

	@Override
	public int lootMinValue()
	{
		return pluginFilterSettingsService.lootMinValue();
	}

	@Override
	public String lootWhitelist()
	{
		return getString("lootWhitelist", "");
	}

	@Override
	public boolean lootIncludeUntradeables()
	{
		return getBoolean("lootIncludeUntradeables", true);
	}

	@Override
	public boolean clueTierBeginner()
	{
		return getBoolean("clueTierBeginner", true);
	}

	@Override
	public boolean clueTierEasy()
	{
		return getBoolean("clueTierEasy", true);
	}

	@Override
	public boolean clueTierMedium()
	{
		return getBoolean("clueTierMedium", true);
	}

	@Override
	public boolean clueTierHard()
	{
		return getBoolean("clueTierHard", true);
	}

	@Override
	public boolean clueTierElite()
	{
		return getBoolean("clueTierElite", true);
	}

	@Override
	public boolean clueTierMaster()
	{
		return getBoolean("clueTierMaster", true);
	}

	@Override
	public int clueMinValue()
	{
		return pluginFilterSettingsService.clueMinValue();
	}

	@Override
	public int clueScreenshotMinValue()
	{
		return pluginFilterSettingsService.clueScreenshotMinValue();
	}

	@Override
	public boolean notifyLoot()
	{
		return getBoolean("notifyLoot", true);
	}

	@Override
	public boolean notifyPet()
	{
		return getBoolean("notifyPet", true);
	}

	@Override
	public boolean notifyLevel()
	{
		return getBoolean("notifyLevel", true);
	}

	@Override
	public boolean notifyExperience()
	{
		return getBoolean("notifyExperience", true);
	}

	@Override
	public boolean notifyQuest()
	{
		return getBoolean("notifyQuest", true);
	}

	@Override
	public boolean notifyKillCount()
	{
		return getBoolean("notifyKillCount", true);
	}

	@Override
	public boolean notifyClue()
	{
		return getBoolean("notifyClue", true);
	}

	@Override
	public boolean notifyDiary()
	{
		return getBoolean("notifyDiary", true);
	}

	@Override
	public boolean notifyCombatAchievement()
	{
		return getBoolean("notifyCombatAchievement", true);
	}

	@Override
	public boolean notifyCollection()
	{
		return getBoolean("notifyCollection", true);
	}

	@Override
	public boolean notifyDeath()
	{
		return getBoolean("notifyDeath", true);
	}

	@Override
	public boolean sendEventScreenshots()
	{
		return true;
	}

	@Override
	public boolean screenshotIncludeSidebar()
	{
		return getBoolean("screenshotIncludeSidebar", true);
	}

	@Override
	public boolean screenshotHidePrivateMessages()
	{
		return getBoolean("screenshotHidePrivateMessages", false);
	}

	@Override
	public String collectionDenylist()
	{
		return getString("collectionDenylist", "");
	}

	@Override
	public boolean syncOnLogout()
	{
		return true;
	}

	@Override
	public boolean displayCodeword()
	{
		return getBoolean("displayCodeword", false);
	}

	@Override
	public String configuredCodeword()
	{
		return getString("configuredCodeword", "");
	}

	@Override
	public boolean showCodewordTimestamp()
	{
		return getBoolean("showCodewordTimestamp", true);
	}

	@Override
	public String codewordColor()
	{
		return getString("codewordColor", "#FD8F00");
	}

	@Override
	public String timestampColor()
	{
		return getString("timestampColor", "#FF9A00");
	}

	private boolean getBoolean(String key, boolean defaultValue)
	{
		String value = configManager.getConfiguration(GROUP, key);
		return value != null ? Boolean.parseBoolean(value) : defaultValue;
	}

	private int getInt(String key, int defaultValue)
	{
		String value = configManager.getConfiguration(GROUP, key);
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	private String getString(String key, String defaultValue)
	{
		String value = configManager.getConfiguration(GROUP, key);
		return value != null ? value : defaultValue;
	}
}
