package com.selectivehider;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SelectiveHiderConfig.GROUP)
public interface SelectiveHiderConfig extends Config
{
	String GROUP = "selectivehider";

	// ---------------------------------------------------------------- sections

	@ConfigSection(
		name = "Show categories",
		description = "Everything is hidden unless its category is ticked here or its name is whitelisted.",
		position = 0
	)
	String categories = "categories";

	@ConfigSection(
		name = "Whitelist (always show)",
		description = "Names that are always drawn, even when their category is hidden.",
		position = 1,
		closedByDefault = true
	)
	String whitelist = "whitelist";

	@ConfigSection(
		name = "Blacklist (always hide)",
		description = "Names that are always hidden, even when their category is shown. The blacklist beats the whitelist.",
		position = 2,
		closedByDefault = true
	)
	String blacklist = "blacklist";

	@ConfigSection(
		name = "Advanced",
		description = "Menu options, hitbars and learned mechanic objects.",
		position = 3,
		closedByDefault = true
	)
	String advanced = "advanced";

	// -------------------------------------------------------------- categories

	@ConfigItem(keyName = "showSelf", name = "Yourself", description = "Draw your own character.",
		position = 0, section = categories)
	default boolean showSelf()
	{
		return true;
	}

	@ConfigItem(keyName = "showOtherPlayers", name = "Other players", description = "Draw other players.",
		position = 1, section = categories)
	default boolean showOtherPlayers()
	{
		return false;
	}

	@ConfigItem(keyName = "showNpcs", name = "NPCs", description = "Draw all NPCs (except pets, which have their own toggle).",
		position = 2, section = categories)
	default boolean showNpcs()
	{
		return true;
	}

	@ConfigItem(keyName = "showPets", name = "Pets / followers", description = "Draw pets and other followers.",
		position = 3, section = categories)
	default boolean showPets()
	{
		return false;
	}

	@ConfigItem(keyName = "showProjectiles", name = "Projectiles", description = "Draw projectiles (arrows, spells, boss attacks in flight).",
		position = 4, section = categories)
	default boolean showProjectiles()
	{
		return true;
	}

	@ConfigItem(keyName = "showSpotanims", name = "Spotanims (tile effects)",
		description = "Draw graphics placed on tiles, e.g. AoE markers, falling-object shadows, splashes.<br>"
			+ "Graphics attached to an NPC or player follow that NPC/player instead.",
		position = 5, section = categories)
	default boolean showSpotanims()
	{
		return true;
	}

	@ConfigItem(keyName = "showGroundItems", name = "Ground items", description = "Draw items lying on the ground.",
		position = 6, section = categories)
	default boolean showGroundItems()
	{
		return true;
	}

	@ConfigItem(keyName = "showSpawnedObjects", name = "Mechanic objects (auto)",
		description = "Draw objects that appear mid-play and have no menu options (acid pools, hazards, etc.),<br>"
			+ "even while scenery is hidden, and remember them for next time.<br>"
			+ "Blacklisted objects are never learned, so blacklist anything unwanted, like Tree stump.",
		position = 7, section = categories)
	default boolean showSpawnedObjects()
	{
		return true;
	}

	@ConfigItem(keyName = "showScenery", name = "Scenery", description = "Draw all game objects: walls, trees, decorations, animated objects.",
		position = 8, section = categories)
	default boolean showScenery()
	{
		return false;
	}

	@ConfigItem(keyName = "showTerrain", name = "Terrain", description = "Draw the ground itself.",
		position = 9, section = categories)
	default boolean showTerrain()
	{
		return false;
	}

	@ConfigItem(keyName = "showBoats", name = "Other boats", description = "Draw other players' boats. Your own boat is always drawn.",
		position = 10, section = categories)
	default boolean showBoats()
	{
		return false;
	}

	@ConfigItem(keyName = "showUi", name = "Interface", description = "Untick to remove ALL interface elements, e.g. for a greenscreen.",
		position = 11, section = categories)
	default boolean showUi()
	{
		return true;
	}

	// --------------------------------------------------------------- whitelist

	@ConfigItem(keyName = "whitelistNpcs", name = "NPCs", description = "Comma-separated NPC names to always draw, e.g. Great Olm, Sol Heredit",
		position = 0, section = whitelist)
	default String whitelistNpcs()
	{
		return "";
	}

	@ConfigItem(keyName = "whitelistObjects", name = "Objects", description = "Comma-separated object names to always draw, e.g. Acid pool",
		position = 1, section = whitelist)
	default String whitelistObjects()
	{
		return "";
	}

	// --------------------------------------------------------------- blacklist

	@ConfigItem(keyName = "blacklistNpcs", name = "NPCs", description = "Comma-separated NPC names to always hide, e.g. Rat, Giant rat",
		position = 0, section = blacklist)
	default String blacklistNpcs()
	{
		return "";
	}

	@ConfigItem(keyName = "blacklistObjects", name = "Objects", description = "Comma-separated object names to always hide, e.g. Tree stump, Rocks",
		position = 1, section = blacklist)
	default String blacklistObjects()
	{
		return "";
	}

	// ---------------------------------------------------------------- advanced

	@ConfigItem(keyName = "shiftClickMenu", name = "Shift-click menu",
		description = "Adds 'Hider whitelist' and 'Hider blacklist' when you shift-right-click an NPC or object.<br>"
			+ "NPCs are added to the lists by name. Objects are remembered individually<br>"
			+ "(just the one you clicked); to affect every object with a name, type it in the lists.",
		position = 0, section = advanced)
	default boolean shiftClickMenu()
	{
		return true;
	}

	@ConfigItem(keyName = "hideHiddenEntityUi", name = "Hide hitbars of hidden",
		description = "Also hide hitsplats, health bars and overheads of entities that are hidden.",
		position = 1, section = advanced)
	default boolean hideHiddenEntityUi()
	{
		return false;
	}

	@ConfigItem(keyName = "forgetLearnedObjects", name = "Forget learned objects",
		description = "Tick to forget every object 'Mechanic objects (auto)' has learned. It unticks itself.<br>"
			+ "To stop just one object showing, blacklist it by name instead.",
		position = 2, section = advanced)
	default boolean forgetLearnedObjects()
	{
		return false;
	}

	@ConfigItem(keyName = "forgetPinnedObjects", name = "Forget clicked objects",
		description = "Tick to forget every specific object you've whitelisted or blacklisted with the<br>"
			+ "shift-click menu. It unticks itself. Name lists aren't affected.",
		position = 3, section = advanced)
	default boolean forgetPinnedObjects()
	{
		return false;
	}

	/** Internal storage for the shift-click menu: objects as name + tile. Hidden, never typed in. */
	@ConfigItem(keyName = "pinnedWhitelistObjects", name = "", description = "", hidden = true)
	default String pinnedWhitelistObjects()
	{
		return "";
	}

	/** Internal storage for the shift-click menu: objects as name + tile. Hidden, never typed in. */
	@ConfigItem(keyName = "pinnedBlacklistObjects", name = "", description = "", hidden = true)
	default String pinnedBlacklistObjects()
	{
		return "";
	}

	/** Internal storage for 'Mechanic objects (auto)'. Hidden: filled by the plugin, never typed in. */
	@ConfigItem(keyName = "learnedObjectIds", name = "", description = "", hidden = true)
	default String learnedObjectIds()
	{
		return "";
	}
}
