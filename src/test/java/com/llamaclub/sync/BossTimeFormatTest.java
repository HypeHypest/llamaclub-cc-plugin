package com.llamaclub.sync;

import org.junit.Assert;
import org.junit.Test;

public class BossTimeFormatTest
{
	@Test
	public void parsesCaBossPersonalBestFormat()
	{
		Assert.assertEquals(4.2, BossTimeFormat.parseTimeString("0:04.20"), 0.01);
		Assert.assertEquals(6.0, BossTimeFormat.parseTimeString("0:06.0"), 0.01);
	}

	@Test
	public void formatsCaBossPersonalBestFormat()
	{
		Assert.assertEquals("0:04.2", BossTimeFormat.formatSeconds(4.2));
		Assert.assertEquals("1:15.0", BossTimeFormat.formatSeconds(75.0));
	}
}
