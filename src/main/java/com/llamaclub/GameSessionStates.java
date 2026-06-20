package com.llamaclub;

import net.runelite.api.GameState;

final class GameSessionStates
{
	private GameSessionStates()
	{
	}

	/**
	 * True when the player is leaving an active session (logout/disconnect),
	 * but not during transient states like world hops or area loading.
	 */
	static boolean shouldEndPlaySession(GameState previous, GameState current)
	{
		return previous == GameState.LOGGED_IN
			&& !isTransientGameState(current);
	}

	static boolean isTransientGameState(GameState state)
	{
		return state == GameState.LOGGED_IN
			|| state == GameState.HOPPING
			|| state == GameState.LOADING
			|| state == GameState.LOGGING_IN;
	}
}
