package com.llamaclub.clan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.util.Text;

@Singleton
public class ClanMemberCollector
{
	@Inject
	private Client client;

	@Inject
	private ClanJoinDateScraper joinDateScraper;

	@Getter
	private int lastCollectedMemberCount;

	@Getter
	private int lastCollectedJoinDateCount;

	public Map<String, Object> collect()
	{
		ClanSettings clanSettings = client.getClanSettings();
		if (clanSettings == null)
		{
			return null;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		List<Map<String, Object>> members = new ArrayList<>();
		Map<String, String> scrapedJoinDates = joinDateScraper.scrapeJoinDatesByUsername();
		int joinDateCount = 0;

		for (ClanMember clanMember : clanSettings.getMembers())
		{
			if (clanMember.getName().startsWith("[#"))
			{
				continue;
			}

			String username = Text.toJagexName(clanMember.getName());
			ClanTitle title = clanSettings.titleForRank(clanMember.getRank());
			String rank = title == null ? "Member" : title.getName();
			String rankKey = title == null ? "member" : title.getName().toLowerCase().replaceAll("[-\\s]", "_");
			int rankIndex = clanMember.getRank().getRank();

			Map<String, Object> member = new HashMap<>();
			member.put("username", username);
			member.put("rank", rank);
			member.put("rankKey", rankKey);
			member.put("rankIndex", rankIndex);

			LocalDate joinDate = clanMember.getJoinDate();
			String normalizedUsername = ClanJoinDateScraper.normalizeUsername(username);
			String joinDateIso = scrapedJoinDates.get(normalizedUsername);

			if (joinDateIso == null && joinDate != null)
			{
				joinDateIso = joinDate.toString();
			}

			if (joinDateIso != null)
			{
				member.put("joinDate", joinDateIso);
				joinDateCount++;
			}

			members.add(member);
		}

		lastCollectedMemberCount = members.size();
		lastCollectedJoinDateCount = joinDateCount;

		if (members.isEmpty())
		{
			return null;
		}

		ClanMember self = clanSettings.findMember(local.getName());
		if (self == null)
		{
			self = clanSettings.findMember(Text.toJagexName(local.getName()));
		}

		ClanTitle selfTitle = self != null ? clanSettings.titleForRank(self.getRank()) : null;
		String syncedByRank = selfTitle == null ? "Member" : selfTitle.getName();
		String syncedByRankKey = selfTitle == null ? "member" : selfTitle.getName().toLowerCase().replaceAll("[-\\s]", "_");

		Map<String, Object> syncedBy = new HashMap<>();
		syncedBy.put("username", Text.toJagexName(local.getName()));
		syncedBy.put("rank", syncedByRank);
		syncedBy.put("rankKey", syncedByRankKey);

		Map<String, Object> clanSync = new HashMap<>();
		clanSync.put("clanName", clanSettings.getName());
		clanSync.put("clanSize", members.size());
		clanSync.put("syncedBy", syncedBy);
		clanSync.put("members", members);
		return clanSync;
	}

	public String describeJoinDateReadiness()
	{
		if (!joinDateScraper.isMembersInterfaceOpen())
		{
			return "Open Clan Settings and go to the Members tab before syncing.";
		}

		return "Could not read join dates. On the Members tab, set the right column to Joined, then sync again.";
	}
}
