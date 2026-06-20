package com.llamaclub.clan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class ClanJoinDateScraper
{
	private static final int RSN_COLUMN = 10;
	private static final int FIRST_COLUMN = 11;
	private static final int SECOND_COLUMN = 13;
	private static final int FIRST_DROPDOWN = 7;
	private static final int SECOND_DROPDOWN = 8;

	private static final DateTimeFormatter UI_DATE_SHORT = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter UI_DATE_LONG = DateTimeFormatter.ofPattern("d-MMMM-yyyy", Locale.ENGLISH);

	@Inject
	private Client client;

	/**
	 * Reads join dates from the Clan Settings &gt; Members interface.
	 * Uses the same widget indexing approach as the Clanmate Export plugin.
	 *
	 * @return map of normalized username to ISO join date (yyyy-MM-dd)
	 */
	public Map<String, String> scrapeJoinDatesByUsername()
	{
		Map<String, String> joinDates = new HashMap<>();

		ClanSettings clanSettings = client.getClanSettings();
		if (clanSettings == null)
		{
			return joinDates;
		}

		int joinedColumnId = resolveJoinedColumnId();
		scrapeFromMemberListWidgets(joinDates, clanSettings, joinedColumnId);

		if (joinDates.isEmpty() && joinedColumnId != FIRST_COLUMN)
		{
			scrapeFromMemberListWidgets(joinDates, clanSettings, FIRST_COLUMN);
		}

		for (ClanMember clanMember : clanSettings.getMembers())
		{
			if (clanMember.getName().startsWith("[#"))
			{
				continue;
			}

			String key = normalizeUsername(Text.toJagexName(clanMember.getName()));
			if (joinDates.containsKey(key))
			{
				continue;
			}

			LocalDate joinDate = clanMember.getJoinDate();
			if (joinDate != null)
			{
				joinDates.put(key, joinDate.toString());
			}
		}

		log.debug("Scraped {} clan join dates from members interface", joinDates.size());
		return joinDates;
	}

	public boolean isMembersInterfaceOpen()
	{
		return getMembersWidget(RSN_COLUMN) != null;
	}

	private void scrapeFromMemberListWidgets(Map<String, String> joinDates, ClanSettings clanSettings, int joinedColumnId)
	{
		Widget namesWidget = getMembersWidget(RSN_COLUMN);
		Widget joinedWidget = getMembersWidget(joinedColumnId);
		if (namesWidget == null || joinedWidget == null)
		{
			return;
		}

		Widget[] nameChildren = namesWidget.getChildren();
		Widget[] joinedChildren = joinedWidget.getChildren();
		if (nameChildren == null || joinedChildren == null)
		{
			return;
		}

		int clanMemberCount = clanSettings.getMembers().size();
		int lastSuccessfulRsnIndex = 0;
		int otherColumnsPositions = 0;

		for (int i = 0; i < nameChildren.length; i++)
		{
			int rsnIndex = i == 0 ? 1 : lastSuccessfulRsnIndex + 3;
			if (rsnIndex < 0 || rsnIndex >= nameChildren.length)
			{
				continue;
			}

			int joinedIndex = otherColumnsPositions + clanMemberCount;
			if (joinedIndex < 0 || joinedIndex >= joinedChildren.length)
			{
				continue;
			}

			Widget nameWidget = nameChildren[rsnIndex];
			Widget joinedWidgetChild = joinedChildren[joinedIndex];
			if (nameWidget == null || joinedWidgetChild == null)
			{
				continue;
			}

			String rsn = Text.removeTags(nameWidget.getText());
			String joinedText = Text.removeTags(joinedWidgetChild.getText());
			String isoDate = toIsoDate(joinedText);

			if (rsn != null && !rsn.isBlank() && isoDate != null)
			{
				joinDates.put(normalizeUsername(rsn), isoDate);
			}

			lastSuccessfulRsnIndex = rsnIndex;
			otherColumnsPositions++;
		}
	}

	private Widget getMembersWidget(int childId)
	{
		Widget widget = client.getWidget(InterfaceID.CLANS_MEMBERS, childId);
		if (widget != null)
		{
			return widget;
		}

		return client.getWidget((InterfaceID.CLANS_MEMBERS << 16) | childId);
	}

	private int resolveJoinedColumnId()
	{
		String firstHeader = readDropdownHeader(FIRST_DROPDOWN);
		String secondHeader = readDropdownHeader(SECOND_DROPDOWN);

		if (isJoinedHeader(secondHeader))
		{
			return SECOND_COLUMN;
		}

		if (isJoinedHeader(firstHeader))
		{
			return FIRST_COLUMN;
		}

		return SECOND_COLUMN;
	}

	private String readDropdownHeader(int dropdownChildId)
	{
		Widget dropdown = getMembersWidget(dropdownChildId);
		if (dropdown == null)
		{
			return null;
		}

		Widget[] children = dropdown.getChildren();
		if (children == null || children.length <= 4)
		{
			return null;
		}

		return Text.removeTags(children[4].getText());
	}

	private static boolean isJoinedHeader(String header)
	{
		return header != null && header.trim().equalsIgnoreCase("Joined");
	}

	static String normalizeUsername(String name)
	{
		return Text.toJagexName(name).replace(' ', '_').toLowerCase(Locale.ENGLISH);
	}

	static String toIsoDate(String uiDate)
	{
		if (uiDate == null || uiDate.isBlank())
		{
			return null;
		}

		String trimmed = uiDate.trim();
		if (trimmed.equalsIgnoreCase("Not available"))
		{
			return null;
		}

		try
		{
			return LocalDate.parse(trimmed, UI_DATE_SHORT).toString();
		}
		catch (DateTimeParseException ex)
		{
			try
			{
				return LocalDate.parse(trimmed, UI_DATE_LONG).toString();
			}
			catch (DateTimeParseException ignored)
			{
				return null;
			}
		}
	}
}
