package com.llamaclub.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

@Singleton
public class CollectionLogSync
{
	@Inject
	private Client client;

	public Map<String, Object> sync()
	{
		int obtainedCount = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int totalCount = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);

		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> obtainedItems = new ArrayList<>();

		result.put("obtainedItems", obtainedItems);
		result.put("obtainedCount", obtainedCount);
		result.put("totalCount", totalCount);
		result.put("source", "varp");
		return result;
	}
}
