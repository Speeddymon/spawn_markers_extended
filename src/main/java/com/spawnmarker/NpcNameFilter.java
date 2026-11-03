package com.spawnmarker;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class NpcNameFilter
{
	private enum Mode
	{
		DISABLED,
		DENY_LIST,
		ALLOW_LIST
	}

	private final Set<String> names;
	private final Mode mode;

	private NpcNameFilter(Set<String> names, Mode mode)
	{
		this.names = names;
		this.mode = mode;
	}

	static NpcNameFilter from(String configuredNames)
	{
		return denyList(configuredNames);
	}

	static NpcNameFilter disabled()
	{
		return new NpcNameFilter(Collections.emptySet(), Mode.DISABLED);
	}

	static NpcNameFilter denyList(String configuredNames)
	{
		return new NpcNameFilter(parseNames(configuredNames), Mode.DENY_LIST);
	}

	static NpcNameFilter allowList(String configuredNames)
	{
		return new NpcNameFilter(parseNames(configuredNames), Mode.ALLOW_LIST);
	}

	private static Set<String> parseNames(String configuredNames)
	{
		if (configuredNames == null || configuredNames.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		return Arrays.stream(configuredNames.split("[,\\r\\n]+"))
			.map(String::trim)
			.filter(name -> !name.isEmpty())
			.map(NpcNameFilter::normalize)
			.collect(Collectors.toUnmodifiableSet());
	}

	boolean hides(String npcName)
	{
		if (npcName == null || mode == Mode.DISABLED)
		{
			return false;
		}

		boolean listed = names.contains(normalize(npcName));
		return mode == Mode.ALLOW_LIST ? !listed : listed;
	}

	boolean contains(String npcName)
	{
		return npcName != null && names.contains(normalize(npcName));
	}

	private static String normalize(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
