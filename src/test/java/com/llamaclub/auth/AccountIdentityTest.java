package com.llamaclub.auth;

import org.junit.Assert;
import org.junit.Test;

public class AccountIdentityTest
{
	@Test
	public void normalizeUsernameTrimsDisplayFormatting()
	{
		Assert.assertEquals("Giddy Gecko", AccountIdentity.normalizeUsername("Giddy Gecko"));
	}
}
