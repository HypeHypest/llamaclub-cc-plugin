package com.llamaclub.util;

import org.junit.Assert;
import org.junit.Test;

public class QuestUtilsTest
{
	@Test
	public void parsesStandardQuestCompletionLine()
	{
		Assert.assertEquals(
			"The Corsair Curse",
			QuestUtils.parseQuestWidget("You have completed The Corsair Curse!")
		);
	}

	@Test
	public void parsesQuotedQuestCompletionLine()
	{
		Assert.assertEquals(
			"One Small Favour",
			QuestUtils.parseQuestWidget("'One Small Favour' completed!")
		);
	}

	@Test
	public void parsesCongratulationsChatLine()
	{
		Assert.assertEquals(
			"Dragon Slayer I",
			QuestUtils.parseQuestWidget("Congratulations! You have completed Dragon Slayer I.")
		);
	}

	@Test
	public void parsesModernQuestChatLineWithColorTags()
	{
		Assert.assertEquals(
			"The Corsair Curse",
			QuestUtils.parseQuestWidget(
				"Congratulations, you've completed a quest: <col=081190>The Corsair Curse</col>"
			)
		);
	}

	@Test
	public void parsesDoricQuestLine()
	{
		Assert.assertEquals(
			"Doric's Quest",
			QuestUtils.parseQuestWidget("You have completed Doric's Quest!")
		);
	}

	@Test
	public void skipsPartialCompletion()
	{
		Assert.assertNull(
			QuestUtils.parseQuestWidget("You have... kind of... completed the Hazeel Cult Quest!")
		);
	}

	@Test
	public void skipsTreasureTrailCompletionScroll()
	{
		Assert.assertNull(
			QuestUtils.parseQuestWidget("Well done, you've completed the Treasure Trail!")
		);
	}

	@Test
	public void skipsTreasureTrailCountChatLine()
	{
		Assert.assertNull(
			QuestUtils.parseQuestWidget(
				"<col=ef1020>You have completed 9 easy Treasure Trails.</col>"
			)
		);
	}

	@Test
	public void skipsLootDropChatLine()
	{
		Assert.assertNull(
			QuestUtils.parseQuestWidget(
				"<col=005f00>Slick Gecko received a drop: 18 x Mind rune</col> <col=106f10>(Brutus)</col>"
			)
		);
	}

	@Test
	public void normalizeQuestKeyStripsMarkup()
	{
		Assert.assertEquals(
			"vampyre slayer",
			QuestUtils.normalizeQuestKey("Vampyre Slayer")
		);
		Assert.assertEquals(
			"vampyre slayer",
			QuestUtils.normalizeQuestKey("<col=081190>Vampyre Slayer</col>")
		);
	}
}
