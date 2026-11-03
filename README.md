# Spawn Markers Extended

Spawn Markers Extended records nearby NPC spawn tiles and displays persistent tile markers labeled with the NPC's name. Use it to map spawn locations for hunting, Slayer, or combat training.

Markers are saved by region and remain saved when you disable the plugin or restart RuneLite. The plugin records each distinct tile and plane once; another NPC spawning on an already marked tile does not add a duplicate marker.

![Example spawn tile markers](https://user-images.githubusercontent.com/37679931/97936190-8bd1a800-1d59-11eb-91e7-0f24d8d67471.png)

## NPC name filters

Set **NPC name filter mode** to choose how saved markers are displayed:

- **Off** displays all saved markers.
- **Deny list** hides markers whose NPC names are in the deny list.
- **Allow list** displays only markers whose NPC names are in the allow list.

Enter one name per line or separate names with commas. Matching ignores case and whitespace around names. Filtering only hides markers; it does not delete them.

## Combat level exceptions

By default, NPCs with no combat level are not recorded. Add names to **Allowed NPC names** under **Combat level exceptions** to let those NPCs bypass the combat-level check. These names do not bypass the plugin's other recording conditions, including its range and event-NPC exclusions. This allows, for example, hunter moths and kebbits in the Falconry area to have their spawn tiles marked.

## Managing saved markers

- Select **Clear all saved markers** to permanently remove every saved marker. The control resets after it runs.
- Select **Clear markers in current region** to remove saved markers for the region where you are standing. This also removes that region's recovery copy; the control resets after it runs.
- Open **Advanced** and edit **Saved markers** to remove or change individual entries. Each marker is one line in this format:

  `regionId,regionX,regionY,plane,colorARGB,label`

  Delete a line to remove that marker. Changes are applied when you leave the field. Blank lines and comment lines are ignored.

The plugin records spawn events within 12 tiles of the player and ignores spawn events during its first five seconds after startup or a game-state change. Markers are recorded when NPCs spawn, not when they are killed.
