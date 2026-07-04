package com.llamaclub.auth;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.util.Text;

@Singleton
public class AccountIdentity
{
	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private com.llamaclub.LlamaClubConfig config;

	private String cachedUsername;

	/**
	 * Keeps the last known in-game name available for logout sync.
	 */
	public void refreshCachedUsername()
	{
		resolveUsername();
	}

	public Map<String, Object> build()
	{
		Map<String, Object> identity = new HashMap<>();
		identity.put("pluginToken", config.pluginToken());
		identity.put("accountHash", Long.toString(client.getAccountHash()));
		identity.put("rsProfileKey", configManager.getRSProfileKey());
		identity.put("world", client.getWorld());

		String username = resolveUsername();
		if (username != null)
		{
			identity.put("username", username);
		}

		if (configManager.getProfile() != null)
		{
			identity.put("runeliteProfileId", Long.toString(configManager.getProfile().getId()));
		}

		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		if (profileType != null)
		{
			identity.put("profileType", profileType.name());
		}

		return identity;
	}

	private String resolveUsername()
	{
		Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null && !local.getName().isBlank())
		{
			cachedUsername = normalizeUsername(local.getName());
			return cachedUsername;
		}

		if (cachedUsername != null && !cachedUsername.isBlank())
		{
			return cachedUsername;
		}

		String profileDisplayName = configManager.getRSProfileConfiguration(
			ConfigManager.RSPROFILE_GROUP,
			ConfigManager.RSPROFILE_DISPLAY_NAME
		);
		if (profileDisplayName != null && !profileDisplayName.isBlank())
		{
			cachedUsername = normalizeUsername(profileDisplayName);
			return cachedUsername;
		}

		return null;
	}

	static String normalizeUsername(String username)
	{
		if (username == null || username.isBlank())
		{
			return null;
		}

		return Text.toJagexName(username);
	}
}
