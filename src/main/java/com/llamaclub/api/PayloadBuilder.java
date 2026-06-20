package com.llamaclub.api;

import com.llamaclub.auth.AccountIdentity;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PayloadBuilder
{
	@Inject
	private AccountIdentity accountIdentity;

	public Map<String, Object> buildEvent(String kind, Map<String, Object> eventData)
	{
		Map<String, Object> payload = basePayload("event");
		payload.put("event", event(eventData, kind));
		return payload;
	}

	public Map<String, Object> buildPlayerSync(Map<String, Object> playerSync)
	{
		Map<String, Object> payload = basePayload("player_sync");
		payload.put("playerSync", playerSync);
		return payload;
	}

	public Map<String, Object> buildClanSync(Map<String, Object> clanSync)
	{
		Map<String, Object> payload = basePayload("clan_sync");
		payload.put("clanSync", clanSync);
		return payload;
	}

	private Map<String, Object> basePayload(String type)
	{
		Map<String, Object> payload = new HashMap<>();
		payload.put("schemaVersion", 1);
		payload.put("type", type);
		payload.put("sentAt", System.currentTimeMillis());
		payload.put("identity", accountIdentity.build());
		return payload;
	}

	private static Map<String, Object> event(Map<String, Object> eventData, String kind)
	{
		Map<String, Object> event = new HashMap<>(eventData);
		event.put("kind", kind);
		return event;
	}
}
