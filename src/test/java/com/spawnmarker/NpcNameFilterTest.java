package com.spawnmarker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcNameFilterTest
{
	@Test
	public void matchesCommaSeparatedNamesIgnoringCaseAndWhitespace()
	{
		NpcNameFilter filter = NpcNameFilter.from(" Goblin, Giant rat ");

		assertTrue(filter.hides("goblin"));
		assertTrue(filter.hides("  GIANT RAT  "));
	}

	@Test
	public void requiresAnExactNameMatch()
	{
		NpcNameFilter filter = NpcNameFilter.from("Goblin");

		assertFalse(filter.hides("Goblin guard"));
		assertFalse(filter.hides("Gob"));
	}

	@Test
	public void ignoresEmptyEntriesAndNullNames()
	{
		NpcNameFilter filter = NpcNameFilter.from(" , , ");

		assertFalse(filter.hides("Goblin"));
		assertFalse(filter.hides(null));
		assertFalse(filter.hides("  "));
	}

	@Test
	public void removingANameFromConfigurationUnhidesIt()
	{
		NpcNameFilter before = NpcNameFilter.from("Goblin, Giant rat");
		NpcNameFilter after = NpcNameFilter.from("Giant rat");

		assertTrue(before.hides("Goblin"));
		assertFalse(after.hides("Goblin"));
	}
}
