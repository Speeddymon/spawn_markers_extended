package com.spawnmarker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SpawnMarkerPlugin.CONFIG_GROUP)
public interface SpawnMarkerConfig extends Config
{
	String ADVANCED_SECTION = "advanced";
	String NON_COMBAT_SECTION = "nonCombatExceptions";

	@ConfigSection(
		name = "Combat level exceptions",
		description = "Allow listed NPCs to bypass the combat level filter.",
		position = 5
	)
	String nonCombatSection = NON_COMBAT_SECTION;

	@ConfigSection(
		name = "Advanced",
		description = "Edits are saved when focus leaves Saved markers. If malformed text is retained, right-click the Saved markers label and choose Reset to rebuild the editor from saved region data. This does not delete markers; reopen settings if the field remains blank.",
		position = 6,
		closedByDefault = true
	)
	String advancedSection = ADVANCED_SECTION;

	@ConfigItem(
		keyName = "filterMode",
		name = "NPC name filter mode",
		description = "Choose whether to ignore names, hide listed names, or show only listed names.",
		position = 0
	)
	default NpcNameFilterMode filterMode()
	{
		return NpcNameFilterMode.OFF;
	}

	@ConfigItem(
		keyName = "hiddenNpcNames",
		name = "Deny list NPC names",
		description = "NPC names whose markers are hidden in Deny list mode. Enter one name per line or separate names with commas.",
		position = 1
	)
	default String hiddenNpcNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "allowedNpcNames",
		name = "Allow list NPC names",
		description = "NPC names whose markers remain visible in Allow list mode. Enter one name per line or separate names with commas.",
		position = 2
	)
	default String allowedNpcNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "clearMarkers",
		name = "Clear all saved markers",
		description = "Select to permanently delete all saved spawn markers. This control resets after it runs.",
		position = 3
	)
	default boolean clearMarkers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clearCurrentRegionMarkers",
		name = "Clear markers in current region",
		description = "Delete saved markers for the region you are currently standing in, including its recovery copy. This control resets after it runs; you must be logged in.",
		position = 4
	)
	default boolean clearCurrentRegionMarkers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "nonCombatNpcNames",
		name = "Allowed NPC names",
		description = "These NPC names can create markers even when their combat level is 0 or lower. Enter one name per line or separate names with commas.",
		position = 0,
		section = NON_COMBAT_SECTION
	)
	default String nonCombatNpcNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "markerData",
		name = "Saved markers",
		description = "One marker per line: regionId,regionX,regionY,plane,colorARGB,label. Delete a line to remove that marker; edits apply when you leave this field. To recover from malformed text, right-click this label and choose Reset. The editor is rebuilt from saved region data; reopen settings if it remains blank.",
		position = 0,
		section = ADVANCED_SECTION
	)
	default String markerData()
	{
		return "";
	}
}
