package com.spawnmarker;

import org.junit.Test;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TilePointCodecTest
{
	@Test
	public void readsLegacyColorJson()
	{
		String json = "[{\"regionId\":4912,\"regionX\":26,\"regionY\":32,\"z\":0," +
			"\"color\":{\"value\":-37888,\"falpha\":0.0},\"label\":\"Imp\"}]";

		List<TilePoint> points = TilePointCodec.deserialize(json);

		assertEquals(1, points.size());
		assertEquals(new Color(255, 108, 0), points.get(0).getColor());
	}

	@Test
	public void roundTripsColorWithoutReflectingJdkInternals()
	{
		TilePoint point = new TilePoint(4912, 26, 32, 0, new Color(255, 108, 0), "Imp");

		String json = TilePointCodec.serialize(Collections.singletonList(point));
		List<TilePoint> roundTripped = TilePointCodec.deserialize(json);

		assertTrue(json.contains("\"value\":-37888"));
		assertEquals(new Color(255, 108, 0), roundTripped.get(0).getColor());
	}
}
