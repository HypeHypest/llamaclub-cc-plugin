package com.llamaclub.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class CombatAchievementBossWidgetListener
{
	private static final Pattern KILL_COUNT = Pattern.compile("(?i)kill count:?\\s*([\\d,]+)");
	private static final Pattern PERSONAL_BEST = Pattern.compile("(?i)personal best:?\\s*([\\d:]+(?:\\.\\d+)?)");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CombatAchievementBossStatsReader statsReader;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.CA_BOSS)
		{
			return;
		}

		clientThread.invokeLater(this::captureBossStatsFromWidget);
	}

	private void captureBossStatsFromWidget()
	{
		String bossName = readBossName();
		if (bossName == null || bossName.isEmpty())
		{
			return;
		}

		List<String> lines = new ArrayList<>();
		collectText(client.getWidget(InterfaceID.CaBoss.CA_BOSS_STATS), lines);

		Integer killCount = null;
		Double personalBestSeconds = null;

		for (String line : lines)
		{
			Matcher kcMatcher = KILL_COUNT.matcher(line);
			if (kcMatcher.find())
			{
				try
				{
					killCount = Integer.parseInt(kcMatcher.group(1).replace(",", ""));
				}
				catch (NumberFormatException ignored)
				{
					// keep scanning
				}
			}

			Matcher pbMatcher = PERSONAL_BEST.matcher(line);
			if (pbMatcher.find())
			{
				personalBestSeconds = BossTimeFormat.parseTimeString(pbMatcher.group(1));
			}
		}

		if (killCount == null && personalBestSeconds == null)
		{
			return;
		}

		statsReader.cacheWidgetStats(bossName, killCount, personalBestSeconds);
		log.debug("Cached CA boss stats for {} (kc={}, pb={})", bossName, killCount, personalBestSeconds);
	}

	private String readBossName()
	{
		Widget bossNameWidget = client.getWidget(InterfaceID.CaBoss.BOSS_NAME);
		if (bossNameWidget == null)
		{
			return null;
		}

		return Text.removeTags(bossNameWidget.getText()).trim();
	}

	private static void collectText(Widget widget, List<String> lines)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		String text = widget.getText();
		if (text != null)
		{
			String cleaned = Text.removeTags(text).trim();
			if (!cleaned.isEmpty())
			{
				lines.add(cleaned);
			}
		}

		collectChildren(widget.getDynamicChildren(), lines);
		collectChildren(widget.getStaticChildren(), lines);
		collectChildren(widget.getNestedChildren(), lines);
	}

	private static void collectChildren(Widget[] children, List<String> lines)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			collectText(child, lines);
		}
	}
}
