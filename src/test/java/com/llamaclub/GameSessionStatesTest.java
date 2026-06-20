package com.llamaclub;

import net.runelite.api.GameState;
import org.junit.Assert;
import org.junit.Test;

public class GameSessionStatesTest
{
	@Test
	public void doesNotEndSessionDuringLoadingOrHopping()
	{
		Assert.assertFalse(GameSessionStates.shouldEndPlaySession(GameState.LOGGED_IN, GameState.LOADING));
		Assert.assertFalse(GameSessionStates.shouldEndPlaySession(GameState.LOGGED_IN, GameState.HOPPING));
		Assert.assertFalse(GameSessionStates.shouldEndPlaySession(GameState.LOGGED_IN, GameState.LOGGING_IN));
	}

	@Test
	public void endsSessionOnLogoutOrDisconnect()
	{
		Assert.assertTrue(GameSessionStates.shouldEndPlaySession(GameState.LOGGED_IN, GameState.LOGIN_SCREEN));
		Assert.assertTrue(GameSessionStates.shouldEndPlaySession(GameState.LOGGED_IN, GameState.CONNECTION_LOST));
	}
}
