/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub;

/**
 * Plugin settings persisted via {@link net.runelite.client.config.ConfigManager}.
 * Not registered as a RuneLite {@link net.runelite.client.config.Config} so the
 * main Configuration panel shows only the plugin on/off toggle (no gear icon).
 */
public interface LlamaClubConfig
{
	String GROUP = "llamaclub";

	boolean webhookEnabled();

	String webhookUrl();

	String pluginToken();

	String websiteUrl();

	int lootMinValue();

	String lootWhitelist();

	boolean lootIncludeUntradeables();

	boolean clueTierBeginner();

	boolean clueTierEasy();

	boolean clueTierMedium();

	boolean clueTierHard();

	boolean clueTierElite();

	boolean clueTierMaster();

	int clueMinValue();

	int clueScreenshotMinValue();

	boolean notifyLoot();

	boolean notifyPet();

	boolean notifyLevel();

	boolean notifyExperience();

	boolean notifyQuest();

	boolean notifyKillCount();

	boolean notifyClue();

	boolean notifyDiary();

	boolean notifyCombatAchievement();

	boolean notifyCollection();

	boolean notifyDeath();

	boolean sendEventScreenshots();

	boolean screenshotIncludeSidebar();

	boolean screenshotHidePrivateMessages();

	String collectionDenylist();

	boolean syncOnLogout();

	boolean displayCodeword();

	String configuredCodeword();

	boolean showCodewordTimestamp();

	String codewordColor();

	String timestampColor();
}
