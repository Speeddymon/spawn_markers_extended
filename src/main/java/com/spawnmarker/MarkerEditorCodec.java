package com.spawnmarker;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class MarkerEditorCodec
{
	private static final String HEADER = "# regionId,regionX,regionY,plane,colorARGB,label";
	private static final Color DEFAULT_MARKER_COLOR = new Color(255, 108, 0);

	private MarkerEditorCodec()
	{
	}

	static String serialize(Collection<TilePoint> points)
	{
		List<TilePoint> sortedPoints = new ArrayList<>(points);
		sortedPoints.sort(Comparator
			.comparingInt(TilePoint::getRegionId)
			.thenComparingInt(TilePoint::getRegionX)
			.thenComparingInt(TilePoint::getRegionY)
			.thenComparingInt(TilePoint::getZ)
			.thenComparing(point -> point.getLabel() == null ? "" : point.getLabel()));

		StringBuilder text = new StringBuilder(HEADER);
		for (TilePoint point : sortedPoints)
		{
			Color color = point.getColor();
			String label = point.getLabel() == null ? "" : point.getLabel()
				.replace('\r', ' ')
				.replace('\n', ' ');

			text.append('\n')
				.append(point.getRegionId()).append(',')
				.append(point.getRegionX()).append(',')
				.append(point.getRegionY()).append(',')
				.append(point.getZ()).append(',')
				.append(color == null ? DEFAULT_MARKER_COLOR.getRGB() : color.getRGB()).append(',')
				.append(label);
		}

		return text.toString();
	}

	static List<TilePoint> deserialize(String text)
	{
		List<TilePoint> points = new ArrayList<>();
		if (text == null || text.trim().isEmpty())
		{
			return points;
		}

		String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i].trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}

			String[] fields = line.split(",", 6);
			if (fields.length != 6)
			{
				throw invalidLine(i + 1, "expected six comma-separated fields");
			}

			try
			{
				int regionId = Integer.parseInt(fields[0].trim());
				int regionX = Integer.parseInt(fields[1].trim());
				int regionY = Integer.parseInt(fields[2].trim());
				int plane = Integer.parseInt(fields[3].trim());
				String colorValue = fields[4].trim();
				if (colorValue.isEmpty())
				{
					throw invalidLine(i + 1, "colorARGB is required");
				}

				Color color = new Color(Integer.parseInt(colorValue), true);
				String label = fields[5].trim();

				if (regionId < 0 || regionId > 65535 || regionX < 0 || regionX > 63
					|| regionY < 0 || regionY > 63 || plane < 0 || plane > 3)
				{
					throw invalidLine(i + 1, "coordinates are outside valid bounds");
				}

				points.add(new TilePoint(regionId, regionX, regionY, plane, color, label.isEmpty() ? null : label));
			}
			catch (NumberFormatException e)
			{
				throw invalidLine(i + 1, "coordinates or color are not valid integers");
			}
		}

		return points;
	}

	private static IllegalArgumentException invalidLine(int lineNumber, String reason)
	{
		return new IllegalArgumentException("Invalid saved marker data on line " + lineNumber + ": " + reason);
	}
}
