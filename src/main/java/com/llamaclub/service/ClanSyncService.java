package com.llamaclub.service;

import com.llamaclub.LlamaClubConfig;
import com.llamaclub.api.PayloadBuilder;
import com.llamaclub.api.WebhookClient;
import com.llamaclub.clan.ClanMemberCollector;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

@Singleton
@Slf4j
public class ClanSyncService
{
	@Inject
	private LlamaClubConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClanMemberCollector clanMemberCollector;

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

	public void syncNow(BiConsumer<Boolean, String> callback)
	{
		if (!config.webhookEnabled() || config.pluginToken() == null || config.pluginToken().isBlank())
		{
			if (callback != null)
			{
				callback.accept(false, null);
			}
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			if (callback != null)
			{
				callback.accept(false, "Log into OSRS before syncing clan members");
			}
			return;
		}

		clientThread.invokeLater(() ->
		{
			Map<String, Object> clanSync = clanMemberCollector.collect();
			if (clanSync == null)
			{
				if (callback != null)
				{
					callback.accept(false, "Open your clan chat and ensure you are a member before syncing");
				}
				return;
			}

			if (countMembersWithJoinDate(clanSync) == 0)
			{
				log.warn(
					"Clan sync aborted: no join dates collected for {} members",
					clanMemberCollector.getLastCollectedMemberCount()
				);

				if (callback != null)
				{
					callback.accept(false, clanMemberCollector.describeJoinDateReadiness());
				}
				return;
			}

			log.info(
				"Clan sync sending {} members with {} join dates",
				clanMemberCollector.getLastCollectedMemberCount(),
				clanMemberCollector.getLastCollectedJoinDateCount()
			);

			Map<String, Object> payload = payloadBuilder.buildClanSync(clanSync);
			webhookClient.sendAsync(config.webhookUrl(), payload, callback);
		});
	}

	public int getLastCollectedJoinDateCount()
	{
		return clanMemberCollector.getLastCollectedJoinDateCount();
	}

	public int getLastCollectedMemberCount()
	{
		return clanMemberCollector.getLastCollectedMemberCount();
	}

	private static int countMembersWithJoinDate(Map<String, Object> clanSync)
	{
		Object membersObj = clanSync.get("members");
		if (!(membersObj instanceof List))
		{
			return 0;
		}

		int count = 0;
		for (Object memberObj : (List<?>) membersObj)
		{
			if (memberObj instanceof Map && ((Map<?, ?>) memberObj).containsKey("joinDate"))
			{
				count++;
			}
		}

		return count;
	}
}
