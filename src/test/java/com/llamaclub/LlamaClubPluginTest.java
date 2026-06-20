package com.llamaclub;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LlamaClubPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LlamaClubPlugin.class);
		RuneLite.main(args);
	}
}
