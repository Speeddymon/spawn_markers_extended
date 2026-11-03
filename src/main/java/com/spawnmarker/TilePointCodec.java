package com.spawnmarker;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.awt.Color;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class TilePointCodec
{
	private static final Type POINT_LIST_TYPE = new TypeToken<List<TilePoint>>()
	{
	}.getType();

	private TilePointCodec()
	{
	}

	static Gson createGson(Gson clientGson)
	{
		return clientGson.newBuilder()
			.registerTypeAdapter(Color.class, new JsonSerializer<Color>()
			{
				@Override
				public JsonElement serialize(Color color, Type type, JsonSerializationContext context)
				{
					JsonObject json = new JsonObject();
					json.addProperty("value", color.getRGB());
					json.addProperty("falpha", 0.0f);
					return json;
				}
			})
			.registerTypeAdapter(Color.class, new JsonDeserializer<Color>()
			{
				@Override
				public Color deserialize(JsonElement json, Type type, JsonDeserializationContext context)
					throws JsonParseException
				{
					if (json.isJsonPrimitive())
					{
						return new Color(json.getAsInt(), true);
					}

					if (json.isJsonObject())
					{
						JsonElement value = json.getAsJsonObject().get("value");
						if (value != null && value.isJsonPrimitive())
						{
							return new Color(value.getAsInt(), true);
						}
					}

					throw new JsonParseException("Invalid saved tile marker color: " + json);
				}
			})
			.create();
	}

	static String serialize(Gson gson, Collection<TilePoint> points)
	{
		return gson.toJson(points);
	}

	static List<TilePoint> deserialize(Gson gson, String json)
	{
		List<TilePoint> points = gson.fromJson(json, POINT_LIST_TYPE);
		return points == null ? Collections.emptyList() : points;
	}
}
