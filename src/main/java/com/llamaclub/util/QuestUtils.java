/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
/*
 * Portions of this file are derived from or inspired by the RuneLite Screenshot plugin
 * Copyright (c) 2016-2017, Adam
 * Licensed under the BSD 2-Clause License
 * See LICENSES/runelite-LICENSE.txt for full license text
 */
package com.llamaclub.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class QuestUtils
{
	private static final Pattern QUEST_CHAT_PATTERN = Pattern.compile(
		"Congratulations,? you('ve| have) completed a quest: (?<quest>.+)\\s*$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern QUEST_PATTERN_1 = Pattern.compile(
		".+?ve[\\s.]?(?<verb>been|rebuilt|.+?ed)? ?(?:the )?'?(?<quest>.+?)'?(?: [Qq]uest)?[!.]?$"
	);
	private static final Pattern QUEST_PATTERN_2 = Pattern.compile(
		"'?(?<quest>.+?)'?(?: [Qq]uest)? (?<verb>[a-z]\\w+?ed)?(?: f.*?)?[!.]?$"
	);
	private static final Pattern MARKUP_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern TREASURE_TRAIL_COMPLETION = Pattern.compile(
		"(?i)(treasure\\s+trail|well done,? you('ve| have) completed the treasure trail)"
	);
	private static final Pattern NON_QUEST_COMPLETION = Pattern.compile(
		"(?i)(received a drop|received a loot|received a clue|captured an impling| loot: )"
	);
	private static final Pattern ITEM_DROP_IN_NAME = Pattern.compile("(?i)\\d+ x .+");

	private static final List<String> RFD_TAGS = Arrays.asList(
		"Another Cook", "freed", "defeated", "saved"
	);
	private static final List<String> WORD_QUEST_IN_NAME_TAGS = Arrays.asList(
		"Another Cook", "Doric", "Heroes", "Legends", "Observatory", "Olaf", "Waterfall"
	);
	private static final Map<String, String> QUEST_REPLACEMENTS = new HashMap<>();

	static
	{
		QUEST_REPLACEMENTS.put("Lumbridge Cook... again", "Another Cook's");
		QUEST_REPLACEMENTS.put("Skrach 'Bone Crusher' Uglogwee", "Skrach Uglogwee");
	}

	private QuestUtils()
	{
	}

	@Nullable
	public static String parseQuestWidget(@Nullable String text)
	{
		String normalized = stripMarkup(text);
		if (normalized == null || normalized.isEmpty() || isExcludedCompletionText(normalized))
		{
			return null;
		}

		Matcher chatMatcher = QUEST_CHAT_PATTERN.matcher(normalized);
		if (chatMatcher.matches())
		{
			return finalizeQuestName(chatMatcher.group("quest"), "");
		}

		Matcher matcher = getMatcher(normalized);
		if (matcher == null)
		{
			log.debug("Unable to match quest completion text: {}", text);
			return null;
		}

		String quest = matcher.group("quest");
		String verb = matcher.group("verb");
		return finalizeQuestName(quest, verb != null ? verb : "");
	}

	/**
	 * Normalizes a quest name for deduplication comparisons.
	 */
	public static String normalizeQuestKey(@Nullable String questName)
	{
		String normalized = stripMarkup(questName);
		if (normalized == null)
		{
			return "";
		}

		return normalized.trim().toLowerCase();
	}

	@Nullable
	private static String finalizeQuestName(@Nullable String quest, String verb)
	{
		if (quest == null || quest.isEmpty())
		{
			return null;
		}

		if (looksLikeInvalidQuestName(quest))
		{
			log.debug("Skipping non-quest completion text parsed as quest: {}", quest);
			return null;
		}

		quest = QUEST_REPLACEMENTS.getOrDefault(quest, quest);

		if (verb.contains("kind of"))
		{
			log.debug("Skipping partial completion of quest: {}", quest);
			return null;
		}
		else if (verb.contains("completely"))
		{
			quest += " II";
		}

		final String questName = quest;
		final String verbText = verb;

		if (RFD_TAGS.stream().anyMatch(tag -> (questName + verbText).contains(tag)))
		{
			quest = "Recipe for Disaster - " + quest;
		}

		if (WORD_QUEST_IN_NAME_TAGS.stream().anyMatch(quest::contains))
		{
			quest += " Quest";
		}

		return quest.trim();
	}

	@Nullable
	private static Matcher getMatcher(String text)
	{
		Matcher questMatch1 = QUEST_PATTERN_1.matcher(text);
		if (questMatch1.matches())
		{
			return questMatch1;
		}

		Matcher questMatch2 = QUEST_PATTERN_2.matcher(text);
		if (questMatch2.matches())
		{
			return questMatch2;
		}

		return null;
	}

	@Nullable
	static String stripMarkup(@Nullable String text)
	{
		if (text == null)
		{
			return null;
		}

		return MARKUP_TAG.matcher(text).replaceAll("").trim();
	}

	static boolean isExcludedCompletionText(String normalized)
	{
		return TREASURE_TRAIL_COMPLETION.matcher(normalized).find()
			|| NON_QUEST_COMPLETION.matcher(normalized).find();
	}

	private static boolean looksLikeInvalidQuestName(String quest)
	{
		return quest.contains("drop:")
			|| ITEM_DROP_IN_NAME.matcher(quest).matches()
			|| quest.regionMatches(true, 0, "received a", 0, "received a".length());
	}
}
