# Selective Hider

Hides everything in the game world, then lets you choose what to draw.

- **Show categories**: yourself, other players, NPCs, pets, projectiles, spotanims (tile effects), ground items, mechanic objects, scenery, terrain, other boats, and the interface.
- **Whitelist / Blacklist**: NPC, object and spotanim IDs to always show or always hide. NPCs and objects can also be listed by name in quotes, e.g. `"Tree stump"`. The blacklist beats the whitelist, which beats the categories.
- **Shift-right-click** an NPC or object to whitelist or blacklist it without looking up its ID. A chat message confirms the change.
- **Mechanic objects (auto)**: shows objects that appear mid-play and have no menu options (acid pools and similar), and remembers their IDs. Blacklisted objects are never learned.

## Screenshots

Hidden NPCs and scenery are only hidden visually and stay clickable. Hidden other players and pets are removed entirely, as in the core Entity Hider plugin, so NPCs walking under them draw correctly. Models added by other plugins are always drawn.

Requires a GPU plugin. Changes to scenery, terrain, ground items or object IDs reload the current area.
