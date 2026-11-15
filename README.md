# Selective Hider

Hides everything in the game world, then lets you choose what to draw.

- **Show categories**: yourself, other players, NPCs, pets, projectiles, spotanims (tile effects), ground items, mechanic objects, scenery, terrain, other boats, and the interface.
- **Whitelist / Blacklist**: NPC and object names to always show or always hide, e.g. `Tree stump, Rocks`. The blacklist beats the whitelist, which beats the categories.
- **Shift-right-click** an NPC or object to whitelist or blacklist it. NPCs are added by name; objects are remembered individually (just the one you clicked, by name and tile). A chat message confirms the change.
- **Mechanic objects (auto)**: shows objects that appear mid-play and have no menu options (acid pools and similar), and remembers them. Blacklisted objects are never learned.

## Screenshots
<img width="1002" height="504" alt="image" src="https://github.com/user-attachments/assets/06b316f2-30d9-43d6-a16f-5281cbe129d4" />
<img width="640" height="360" alt="clip" src="https://github.com/user-attachments/assets/0390c040-76a1-4897-a8a5-d991ed8bd944" />
<img width="1001" height="503" alt="image" src="https://github.com/user-attachments/assets/e73d59e5-2470-48c6-b2d8-64ba4dd65caa" />

Hidden NPCs and scenery are only hidden visually and stay clickable. Hidden other players and pets are removed entirely, as in the core Entity Hider plugin, so NPCs walking under them draw correctly. Models added by other plugins are always drawn.

Requires a GPU plugin. Changes to scenery, terrain, ground items or the object lists reload the current area.
