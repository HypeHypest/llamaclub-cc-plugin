package com.llamaclub.clan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ClanJoinDateScraperTest
{
	@Test
	public void toIsoDate_parsesOsrsMemberListFormat()
	{
		assertEquals("2024-06-21", ClanJoinDateScraper.toIsoDate("21-Jun-2024"));
		assertEquals("2024-04-01", ClanJoinDateScraper.toIsoDate("1-Apr-2024"));
		assertEquals("2026-03-09", ClanJoinDateScraper.toIsoDate("9-Mar-2026"));
		assertEquals("2024-06-21", ClanJoinDateScraper.toIsoDate("21-June-2024"));
	}

	@Test
	public void toIsoDate_rejectsInvalidValues()
	{
		assertNull(ClanJoinDateScraper.toIsoDate(null));
		assertNull(ClanJoinDateScraper.toIsoDate(""));
		assertNull(ClanJoinDateScraper.toIsoDate("Not available"));
	}

	@Test
	public void normalizeUsername_usesJagexFormat()
	{
		assertEquals("guru_gecko", ClanJoinDateScraper.normalizeUsername("Guru Gecko"));
	}
}
