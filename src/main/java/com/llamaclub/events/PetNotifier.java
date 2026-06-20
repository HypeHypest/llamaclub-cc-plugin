/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.pet.PetItems;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class PetNotifier extends BaseNotifier
{
	private static final int MAX_TICKS_WAIT = 10;

	private static final Pattern PET_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you(?:'|')?re being followed(?: by (.+))?|have a funny feeling like you would have been followed|feel something weird sneaking into your backpack)\\.?\\.?\\.?",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern UNTRADEABLE_PATTERN = Pattern.compile(
		"Untradeable drop: (.+)",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (.+)",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern CLAN_REGEX = Pattern.compile(
		"(?:[^\\w\\s]*)?(?<user>[\\w\\s]+?) (?:has a funny feeling like .+? (?:would have been followed|being followed)|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (?<pet>.+?)(?: at (?<milestone>.+))?\\.$",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern LEADING_NON_WORD = Pattern.compile("^[^\\w\\s]+");
	private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u001F\\u007F-\\u009F]");
	private static final Pattern GENERAL_PUNCTUATION = Pattern.compile("[\\u2000-\\u206F]");
	private static final Pattern CURRENCY_SYMBOLS = Pattern.compile("[\\u20A0-\\u20CF]");
	private static final Pattern ARROWS = Pattern.compile("[\\u2190-\\u21FF]");
	private static final Pattern MISC_SYMBOLS = Pattern.compile("[\\u2600-\\u26FF]");
	private static final Pattern DINGBATS = Pattern.compile("[\\u2700-\\u27BF]");

	private volatile boolean seenGameMessage = false;
	private volatile String gameMessage = null;
	private volatile String petSource = "unknown";
	private volatile String petName = null;
	private volatile String killCount = null;
	private final AtomicInteger ticksWaited = new AtomicInteger(0);

	@Override
	public boolean isEnabled()
	{
		return config.notifyPet();
	}

	@Override
	protected String getEventKind()
	{
		return "PET";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isEnabled())
		{
			return;
		}

		String message = event.getMessage();
		if (isGameMessage(event))
		{
			onGameChatMessage(message);
			return;
		}

		if (isClanNotification(event))
		{
			onClanNotification(message);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled())
		{
			return;
		}

		if (!seenGameMessage && petName == null)
		{
			return;
		}

		if (!seenGameMessage)
		{
			int ticks = ticksWaited.incrementAndGet();
			if (ticks > MAX_TICKS_WAIT)
			{
				reset();
			}
			return;
		}

		if (petName != null)
		{
			sendPetNotification();
			reset();
			return;
		}

		int ticks = ticksWaited.incrementAndGet();
		if (ticks > MAX_TICKS_WAIT)
		{
			sendPetNotification();
			reset();
		}
	}

	private void onGameChatMessage(String message)
	{
		Matcher petMatcher = PET_PATTERN.matcher(message);
		if (petMatcher.find())
		{
			seenGameMessage = true;
			gameMessage = message;
			ticksWaited.set(0);

			if (petMatcher.group(1) != null && !petMatcher.group(1).trim().isEmpty())
			{
				petName = petMatcher.group(1).trim();
			}

			if (message.toLowerCase().contains("backpack"))
			{
				petSource = "backpack";
			}
			else if (message.toLowerCase().contains("would have been followed"))
			{
				petSource = "dry";
			}
			else
			{
				petSource = "followed";
			}
			return;
		}

		if (!seenGameMessage || petName != null)
		{
			return;
		}

		Matcher untradeableMatcher = UNTRADEABLE_PATTERN.matcher(message);
		if (untradeableMatcher.find())
		{
			String itemName = untradeableMatcher.group(1).trim();
			if (PetItems.isPet(itemName))
			{
				petName = itemName;
			}
			return;
		}

		Matcher collectionMatcher = COLLECTION_LOG_PATTERN.matcher(message);
		if (collectionMatcher.find())
		{
			String itemName = collectionMatcher.group(1).trim();
			if (PetItems.isPet(itemName))
			{
				petName = itemName;
			}
		}
	}

	private void onClanNotification(String message)
	{
		String normalized = message.replace('\u00A0', ' ');
		Matcher clanMatcher = CLAN_REGEX.matcher(normalized);
		if (!clanMatcher.find())
		{
			return;
		}

		String user = cleanUsername(clanMatcher.group("user"));
		String playerName = getPlayerName();
		if (playerName == null || !user.equalsIgnoreCase(playerName))
		{
			return;
		}

		try
		{
			String pet = clanMatcher.group("pet");
			String milestone = clanMatcher.group("milestone");
			if (pet != null && !pet.trim().isEmpty())
			{
				petName = pet.trim();
			}
			if (milestone != null && !milestone.trim().isEmpty())
			{
				killCount = milestone.trim();
			}
		}
		catch (IllegalArgumentException ignored)
		{
			return;
		}

		if (!seenGameMessage)
		{
			ticksWaited.set(0);
		}
	}

	private void sendPetNotification()
	{
		boolean obtained = gameMessage != null && !gameMessage.toLowerCase().contains("would have been followed");

		Map<String, Object> data = new HashMap<>();
		data.put("message", gameMessage);
		data.put("obtained", obtained);
		data.put("petSource", petSource);

		if (petName != null && !petName.isEmpty())
		{
			data.put("petName", petName);
		}
		else
		{
			data.put("petName", "Unknown pet");
		}

		if (killCount != null && !killCount.isEmpty())
		{
			data.put("killCount", killCount);
		}

		sendEvent(data);
	}

	private void reset()
	{
		seenGameMessage = false;
		gameMessage = null;
		petSource = "unknown";
		petName = null;
		killCount = null;
		ticksWaited.set(0);
	}

	private String getPlayerName()
	{
		Player local = client.getLocalPlayer();
		return local != null ? local.getName() : null;
	}

	private static boolean isClanNotification(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		return type == ChatMessageType.FRIENDSCHATNOTIFICATION
			|| type == ChatMessageType.CLAN_MESSAGE
			|| type == ChatMessageType.CLAN_GUEST_MESSAGE
			|| type == ChatMessageType.CLAN_GIM_MESSAGE;
	}

	private static String cleanUsername(String username)
	{
		if (username == null)
		{
			return "";
		}

		String cleaned = LEADING_NON_WORD.matcher(username).replaceAll("");
		cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("");
		cleaned = GENERAL_PUNCTUATION.matcher(cleaned).replaceAll("");
		cleaned = CURRENCY_SYMBOLS.matcher(cleaned).replaceAll("");
		cleaned = ARROWS.matcher(cleaned).replaceAll("");
		cleaned = MISC_SYMBOLS.matcher(cleaned).replaceAll("");
		cleaned = DINGBATS.matcher(cleaned).replaceAll("");
		return cleaned.trim();
	}
}
