package com.spawnmarker;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

import java.util.function.IntConsumer;

final class CurrentRegionMarkerClearAction
{
	private CurrentRegionMarkerClearAction()
	{
	}

	static boolean execute(GameState gameState, WorldPoint playerLocation, IntConsumer clearRegion, Runnable resetControl)
	{
		try
		{
			if (gameState != GameState.LOGGED_IN || playerLocation == null)
			{
				return false;
			}

			clearRegion.accept(playerLocation.getRegionID());
			return true;
		}
		finally
		{
			resetControl.run();
		}
	}
}
