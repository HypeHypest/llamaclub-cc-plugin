package com.llamaclub.service;

import com.llamaclub.LlamaClubConfig;
import com.llamaclub.api.PayloadBuilder;
import com.llamaclub.api.WebhookClient;
import com.llamaclub.sync.PlayerDataCollector;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

@Singleton
public class SyncService
{
	@Inject
	private LlamaClubConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PlayerDataCollector playerDataCollector;

	@Inject
	private PayloadBuilder payloadBuilder;

	@Inject
	private WebhookClient webhookClient;

	public void syncNow(Consumer<Boolean> callback)
	{
		syncNow((success, error) ->
		{
			if (callback != null)
			{
				callback.accept(success);
			}
		});
	}

	public void syncOnSessionEnd(Consumer<Boolean> callback)
	{
		runSync(callback == null ? null : (success, error) -> callback.accept(success), true, true);
	}

	public void syncNow(BiConsumer<Boolean, String> callback)
	{
		runSync(callback, false, false);
	}

	public void syncBlocking(Consumer<Boolean> callback)
	{
		runSync(callback == null ? null : (success, error) ->
		{
			if (callback != null)
			{
				callback.accept(success);
			}
		}, true, false);
	}

	private void runSync(BiConsumer<Boolean, String> callback, boolean blocking, boolean sessionEnd)
	{
		if (!config.webhookEnabled() || config.pluginToken() == null || config.pluginToken().isBlank())
		{
			if (callback != null)
			{
				callback.accept(false, null);
			}
			return;
		}

		if (!sessionEnd && client.getGameState() != GameState.LOGGED_IN)
		{
			if (callback != null)
			{
				callback.accept(false, "Log into OSRS before syncing");
			}
			return;
		}

		Runnable syncTask = () ->
		{
			Map<String, Object> playerSync = playerDataCollector.collectAll();
			Map<String, Object> payload = payloadBuilder.buildPlayerSync(playerSync);
			webhookClient.sendAsync(config.webhookUrl(), payload, callback);
		};

		if (blocking)
		{
			clientThread.invoke(syncTask);
		}
		else
		{
			clientThread.invokeLater(syncTask);
		}
	}
}
