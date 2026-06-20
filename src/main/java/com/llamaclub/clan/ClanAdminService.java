package com.llamaclub.clan;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.util.Text;

@Singleton
public class ClanAdminService
{
	private static final ClanRank DEPUTY_OWNER = ClanRank.DEPUTY_OWNER;

	@Inject
	private Client client;

	public boolean canAccessAdmin()
	{
		ClanSettings clanSettings = client.getClanSettings();
		Player local = client.getLocalPlayer();
		if (clanSettings == null || local == null)
		{
			return false;
		}

		ClanMember member = clanSettings.findMember(local.getName());
		if (member == null)
		{
			member = clanSettings.findMember(Text.toJagexName(local.getName()));
		}
		if (member == null)
		{
			return false;
		}

		ClanRank rank = member.getRank();
		return rank.equals(ClanRank.OWNER) || rank.equals(DEPUTY_OWNER);
	}
}
