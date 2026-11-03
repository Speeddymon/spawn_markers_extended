package com.spawnmarker;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CurrentRegionMarkerClearActionTest
{
	@Test
	public void resetsControlWithoutClearingWhenNotLoggedIn()
	{
		AtomicInteger clearedRegion = new AtomicInteger(-1);
		AtomicBoolean reset = new AtomicBoolean();

		boolean cleared = CurrentRegionMarkerClearAction.execute(
			GameState.LOGIN_SCREEN,
			new WorldPoint(3200, 3200, 0),
			clearedRegion::set,
			() -> reset.set(true));

		assertFalse(cleared);
		assertEquals(-1, clearedRegion.get());
		assertTrue(reset.get());
	}
}
