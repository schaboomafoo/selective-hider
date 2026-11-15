package com.selectivehider;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Selective Hider",
	description = "Hides everything, then lets you choose what to draw by category or name. Requires a GPU plugin.",
	tags = {"hide", "hider", "blindfold", "greenscreen", "render", "pvm", "scenery", "entity", "npc", "object"}
)
@Slf4j
public class SelectiveHiderPlugin extends Plugin implements RenderCallback
{
	/** Config keys whose effect is baked in when an area loads, so changing them needs a reload. */
	private static final Set<String> RELOAD_KEYS = Set.of(
		"showScenery", "showTerrain", "showSpawnedObjects", "showGroundItems",
		"whitelistObjects", "blacklistObjects", "pinnedWhitelistObjects", "pinnedBlacklistObjects"
	);

	private static final String KEY_WHITELIST_NPCS = "whitelistNpcs";
	private static final String KEY_BLACKLIST_NPCS = "blacklistNpcs";
	private static final String KEY_LEARNED_OBJECTS = "learnedObjectIds";
	private static final String KEY_FORGET_LEARNED = "forgetLearnedObjects";
	private static final String KEY_PINNED_WHITELIST = "pinnedWhitelistObjects";
	private static final String KEY_PINNED_BLACKLIST = "pinnedBlacklistObjects";
	private static final String KEY_FORGET_PINNED = "forgetPinnedObjects";

	private static final Color WHITELIST_COLOR = new Color(0x4C, 0xD9, 0x4C);
	private static final Color BLACKLIST_COLOR = new Color(0xFF, 0x55, 0x55);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SelectiveHiderOverlay overlay;

	@Inject
	private SelectiveHiderConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	/**
	 * Immutable snapshot of the config. drawTile/drawObject run on the map loader
	 * thread during area loads, so they read this rather than the config proxy.
	 */
	private volatile Settings settings = Settings.DEFAULT;

	/** Hashes of mechanic objects currently in the area (spawned mid-play, no menu options). */
	private final Set<Long> spawnedObjectHashes = ConcurrentHashMap.newKeySet();

	/** Object IDs that have been seen spawning that way, remembered across areas and sessions. */
	private final Set<Integer> learnedObjectIds = ConcurrentHashMap.newKeySet();

	/**
	 * Object ID -> standardised name. Object definitions can only be read on the client
	 * thread, but area uploads happen on the map loader thread, so names are cached here.
	 */
	private final Map<Integer, String> objectNames = new ConcurrentHashMap<>();

	/** Object IDs whose name was needed during an upload but wasn't cached yet. */
	private final Set<Integer> unresolvedObjectIds = ConcurrentHashMap.newKeySet();

	/** Tick on which the current area finished loading; spawns on or before it are part of the load. */
	private int areaLoadedTick = Integer.MAX_VALUE;

	private final Set<String> loggedUnknown = ConcurrentHashMap.newKeySet();

	@Provides
	SelectiveHiderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SelectiveHiderConfig.class);
	}

	@Override
	protected void startUp()
	{
		settings = Settings.from(config);
		learnedObjectIds.clear();
		learnedObjectIds.addAll(parseIdCsv(config.learnedObjectIds()));
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Already in an area: treat everything present as part of the load
			clientThread.invoke(() -> areaLoadedTick = client.getTickCount());
		}

		renderCallbackManager.register(this);
		overlayManager.add(overlay);
		reloadArea();
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(this);
		overlayManager.remove(overlay);
		spawnedObjectHashes.clear();
		unresolvedObjectIds.clear();
		loggedUnknown.clear();
		// Re-upload the area so hidden terrain and scenery come back
		reloadArea();
	}

	// ------------------------------------------------------------------ config

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SelectiveHiderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if (KEY_LEARNED_OBJECTS.equals(key))
		{
			// Internal storage written by the plugin itself; nothing to re-read
			return;
		}
		if (KEY_FORGET_LEARNED.equals(key))
		{
			if (Boolean.parseBoolean(event.getNewValue()))
			{
				learnedObjectIds.clear();
				saveLearnedIds();
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, KEY_FORGET_LEARNED, "false");
				reloadArea();
			}
			return;
		}
		if (KEY_FORGET_PINNED.equals(key))
		{
			if (Boolean.parseBoolean(event.getNewValue()))
			{
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, KEY_PINNED_WHITELIST, "");
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, KEY_PINNED_BLACKLIST, "");
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, KEY_FORGET_PINNED, "false");
			}
			return;
		}

		settings = Settings.from(config);
		if (RELOAD_KEYS.contains(key))
		{
			reloadArea();
		}
	}

	/** Forces the current area to be uploaded again so scene-load decisions are re-evaluated. */
	private void reloadArea()
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.setGameState(GameState.LOADING);
			}
		});
	}

	// ------------------------------------------------------- shift-click menu

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.shiftClickMenu() || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		Settings s = settings;

		if (entry.getType() == MenuAction.EXAMINE_NPC)
		{
			NPC npc = entry.getNpc();
			if (npc == null)
			{
				return;
			}
			String name = npc.getName();
			if (name == null || name.isEmpty())
			{
				return;
			}
			addListEntries(event, name,
				s.whitelistNpcs.contains(name), KEY_WHITELIST_NPCS,
				s.blacklistNpcs.contains(name), KEY_BLACKLIST_NPCS);
		}
		else if (entry.getType() == MenuAction.EXAMINE_OBJECT)
		{
			WorldView wv = client.getWorldView(entry.getWorldViewId());
			if (wv == null)
			{
				return;
			}
			TileObject object = findTileObject(wv, event.getActionParam0(), event.getActionParam1(), event.getIdentifier());
			if (object == null)
			{
				return;
			}
			ObjectComposition def = resolvedDefinition(object.getId());
			String name = def == null ? null : def.getName();
			if (name == null || name.isEmpty() || "null".equals(name))
			{
				return;
			}
			PinnedObject pinned = pin(name, object, wv.getScene());
			addPinnedEntries(event, pinned, name, object.getId(),
				s.pinnedWhitelist.contains(pinned), s.pinnedBlacklist.contains(pinned));
		}
	}

	private PinnedObject pin(String name, TileObject object, Scene scene)
	{
		return new PinnedObject(name, templateLocation(object, scene));
	}

	/** The object's tile; in instances, the template tile, so it's the same on every visit. */
	private static WorldPoint templateLocation(TileObject object, Scene scene)
	{
		LocalPoint local = object.getLocalLocation();
		if (local == null || scene == null)
		{
			return object.getWorldLocation();
		}
		return WorldPoint.fromLocalInstance(scene, local, object.getPlane());
	}

	/** Finds the object a menu entry refers to. Same approach as the core Object Markers plugin. */
	private TileObject findTileObject(WorldView wv, int sceneX, int sceneY, int id)
	{
		Scene scene = wv.getScene();
		if (scene == null)
		{
			return null;
		}
		Tile[][][] tiles = scene.getTiles();
		int plane = wv.getPlane();
		if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
		{
			return null;
		}
		Tile tile = tiles[plane][sceneX][sceneY];
		if (tile == null)
		{
			return null;
		}
		if (objectIdEquals(tile.getWallObject(), id))
		{
			return tile.getWallObject();
		}
		if (objectIdEquals(tile.getDecorativeObject(), id))
		{
			return tile.getDecorativeObject();
		}
		if (objectIdEquals(tile.getGroundObject(), id))
		{
			return tile.getGroundObject();
		}
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (objectIdEquals(gameObject, id))
				{
					return gameObject;
				}
			}
		}
		return null;
	}

	private boolean objectIdEquals(TileObject object, int id)
	{
		if (object == null)
		{
			return false;
		}
		if (object.getId() == id)
		{
			return true;
		}
		// Examine sends the transformed (impostor) id, so check those too
		ObjectComposition def = client.getObjectDefinition(object.getId());
		if (def != null && def.getImpostorIds() != null)
		{
			for (int impostorId : def.getImpostorIds())
			{
				if (impostorId == id)
				{
					return true;
				}
			}
		}
		return false;
	}

	private void addPinnedEntries(MenuEntryAdded event, PinnedObject pinned, String name, int objectId,
		boolean whitelisted, boolean blacklisted)
	{
		String target = event.getTarget();
		int idx = -1;
		client.getMenu().createMenuEntry(idx--)
			.setOption(ColorUtil.wrapWithColorTag(blacklisted ? "Hider un-blacklist" : "Hider blacklist", BLACKLIST_COLOR))
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> togglePinned(pinned, name, objectId, "blacklist", KEY_PINNED_BLACKLIST, !blacklisted, KEY_PINNED_WHITELIST));
		client.getMenu().createMenuEntry(idx)
			.setOption(ColorUtil.wrapWithColorTag(whitelisted ? "Hider un-whitelist" : "Hider whitelist", WHITELIST_COLOR))
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> togglePinned(pinned, name, objectId, "whitelist", KEY_PINNED_WHITELIST, !whitelisted, KEY_PINNED_BLACKLIST));
	}

	/** Adds or removes one specific object. Adding removes it from the opposite list. */
	private void togglePinned(PinnedObject pinned, String name, int objectId, String listName, String key, boolean add, String oppositeKey)
	{
		Set<PinnedObject> list = new LinkedHashSet<>(PinnedObject.parseAll(configManager.getConfiguration(SelectiveHiderConfig.GROUP, key)));
		if (add)
		{
			Set<PinnedObject> opposite = new LinkedHashSet<>(PinnedObject.parseAll(configManager.getConfiguration(SelectiveHiderConfig.GROUP, oppositeKey)));
			if (opposite.remove(pinned))
			{
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, oppositeKey, PinnedObject.serializeAll(opposite));
			}
			if (KEY_PINNED_BLACKLIST.equals(key) && learnedObjectIds.remove(objectId))
			{
				saveLearnedIds();
			}
			list.add(pinned);
		}
		else
		{
			list.remove(pinned);
		}
		configManager.setConfiguration(SelectiveHiderConfig.GROUP, key, PinnedObject.serializeAll(list));

		Color color = "blacklist".equals(listName) ? BLACKLIST_COLOR : WHITELIST_COLOR;
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("Selective Hider: " + (add ? "Added this " : "Removed this ") + name
				+ (add ? " to the " : " from the ") + ColorUtil.wrapWithColorTag(listName, color) + ".")
			.build());
	}

	/** Shift-click options for NPCs, which are added to the name lists. */
	private void addListEntries(MenuEntryAdded event, String name,
		boolean whitelisted, String whitelistKey,
		boolean blacklisted, String blacklistKey)
	{
		String target = event.getTarget();

		int idx = -1;
		client.getMenu().createMenuEntry(idx--)
			.setOption(ColorUtil.wrapWithColorTag(blacklisted ? "Hider un-blacklist" : "Hider blacklist", BLACKLIST_COLOR))
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleList(name, "blacklist", blacklistKey, !blacklisted, whitelistKey));
		client.getMenu().createMenuEntry(idx)
			.setOption(ColorUtil.wrapWithColorTag(whitelisted ? "Hider un-whitelist" : "Hider whitelist", WHITELIST_COLOR))
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleList(name, "whitelist", whitelistKey, !whitelisted, blacklistKey));
	}

	/**
	 * Adds the name to (or removes it from) one list, keeping the other entries in it.
	 * Adding also removes the name from the opposite list, so it's never on both.
	 */
	private void toggleList(String name, String listName, String key, boolean add, String oppositeKey)
	{
		NameList list = NameList.parse(configManager.getConfiguration(SelectiveHiderConfig.GROUP, key));
		if (add)
		{
			NameList opposite = NameList.parse(configManager.getConfiguration(SelectiveHiderConfig.GROUP, oppositeKey));
			if (opposite.contains(name))
			{
				configManager.setConfiguration(SelectiveHiderConfig.GROUP, oppositeKey, opposite.without(name).serialize());
			}
			configManager.setConfiguration(SelectiveHiderConfig.GROUP, key, list.with(name).serialize());
		}
		else
		{
			configManager.setConfiguration(SelectiveHiderConfig.GROUP, key, list.without(name).serialize());
		}

		// The settings panel doesn't refresh while it's open, so confirm in chat instead
		String verb = add ? "Added" : "Removed";
		String preposition = add ? "to" : "from";
		Color color = "blacklist".equals(listName) ? BLACKLIST_COLOR : WHITELIST_COLOR;
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("Selective Hider: " + verb + " " + name + " "
				+ preposition + " the " + ColorUtil.wrapWithColorTag(listName, color) + ".")
			.build());
	}

	private void saveLearnedIds()
	{
		configManager.setConfiguration(SelectiveHiderConfig.GROUP, KEY_LEARNED_OBJECTS,
			new TreeSet<>(learnedObjectIds).stream().map(String::valueOf).collect(Collectors.joining(",")));
	}

	/** Reads the plugin's own internal list of learned object IDs. */
	private static Set<Integer> parseIdCsv(String csv)
	{
		Set<Integer> ids = new HashSet<>();
		if (csv == null)
		{
			return ids;
		}
		for (String part : csv.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// Skip anything malformed
			}
		}
		return ids;
	}

	// ------------------------------------------------- mechanic object learning

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			spawnedObjectHashes.clear();
			areaLoadedTick = Integer.MAX_VALUE;
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			areaLoadedTick = client.getTickCount();
		}
	}

	/**
	 * If an area upload needed object names that weren't cached yet, look them up now and
	 * reload once if any of them is on a name list. After that they're cached, so it won't repeat.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (unresolvedObjectIds.isEmpty())
		{
			return;
		}

		Settings s = settings;
		boolean affected = false;
		for (Integer id : unresolvedObjectIds)
		{
			String name = cacheObjectName(id);
			affected |= s.whitelistObjects.names.contains(name) || s.blacklistObjects.names.contains(name)
				|| s.pinnedNames.contains(name);
		}
		unresolvedObjectIds.clear();
		if (affected)
		{
			reloadArea();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		onObjectSpawned(e.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		onObjectDespawned(e.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned e)
	{
		onObjectSpawned(e.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned e)
	{
		onObjectDespawned(e.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned e)
	{
		onObjectSpawned(e.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned e)
	{
		onObjectDespawned(e.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		onObjectSpawned(e.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		onObjectDespawned(e.getGroundObject());
	}

	private void onObjectSpawned(TileObject object)
	{
		if (object == null)
		{
			return;
		}

		int id = object.getId();
		Settings s = settings;
		if (s.hasObjectNames())
		{
			// Cache the name now, on the client thread, ready for the upload
			objectNames.computeIfAbsent(id, this::lookupObjectName);
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getTickCount() <= areaLoadedTick)
		{
			// Part of the area loading, i.e. ordinary scenery
			return;
		}

		if (matchesObject(s.blacklistObjects, id) || matchesObject(s.whitelistObjects, id))
		{
			// Already decided by the user; don't learn it
			return;
		}

		if (!hasNoMenuOptions(id))
		{
			// Doors, trees, rocks, etc. are scenery, not mechanics
			return;
		}

		spawnedObjectHashes.add(object.getHash());
		if (learnedObjectIds.add(id))
		{
			saveLearnedIds();
		}
	}

	private void onObjectDespawned(TileObject object)
	{
		if (object != null)
		{
			spawnedObjectHashes.remove(object.getHash());
		}
	}

	private ObjectComposition resolvedDefinition(int objectId)
	{
		ObjectComposition def = client.getObjectDefinition(objectId);
		if (def != null && def.getImpostorIds() != null)
		{
			ObjectComposition impostor = def.getImpostor();
			if (impostor != null)
			{
				return impostor;
			}
		}
		return def;
	}

	private boolean hasNoMenuOptions(int objectId)
	{
		ObjectComposition def = resolvedDefinition(objectId);
		if (def == null)
		{
			return false;
		}
		String[] actions = def.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null && !action.isEmpty())
				{
					return false;
				}
			}
		}
		return true;
	}

	/** Client thread only. */
	private String lookupObjectName(int objectId)
	{
		ObjectComposition def = resolvedDefinition(objectId);
		return def == null || def.getName() == null ? "" : Text.standardize(def.getName());
	}

	/** Client thread only. */
	private String cacheObjectName(int objectId)
	{
		return objectNames.computeIfAbsent(objectId, this::lookupObjectName);
	}

	/**
	 * Gets an object's name from any thread. Off the client thread an uncached name
	 * returns null and is queued to be looked up on the next game tick.
	 */
	private String objectName(int objectId)
	{
		String name = objectNames.get(objectId);
		if (name != null)
		{
			return name;
		}
		if (client.isClientThread())
		{
			return cacheObjectName(objectId);
		}
		unresolvedObjectIds.add(objectId);
		return null;
	}

	private boolean matchesObject(NameList list, int objectId)
	{
		if (list.names.isEmpty())
		{
			return false;
		}
		String name = objectName(objectId);
		return name != null && list.names.contains(name);
	}

	private static boolean matchesNpc(NameList list, NPC npc)
	{
		return list.contains(npc.getName());
	}

	// --------------------------------------------------------- render decisions

	/** Terrain. Called by the map loader thread when an area is uploaded. */
	@Override
	public boolean drawTile(Scene scene, Tile tile)
	{
		return settings.showTerrain;
	}

	/**
	 * Called during area upload for every object, and every frame for animated objects
	 * and temporary entities (which arrive wrapped in a GameObject).
	 * Hiding here stops the model being drawn but keeps its clickbox.
	 */
	@Override
	public boolean drawObject(Scene scene, TileObject object)
	{
		Settings s = settings;

		if (object instanceof GameObject)
		{
			Renderable renderable = ((GameObject) object).getRenderable();
			Boolean entity = entityDecision(renderable, s);
			if (entity != null)
			{
				return entity;
			}
			if (renderable != null && !isSceneryRenderable(renderable))
			{
				// Something we don't recognise, e.g. a model placed by another plugin.
				// Always draw it; users can turn that plugin off if they don't want it.
				if (loggedUnknown.add(renderable.getClass().getName()))
				{
					log.debug("Selective Hider: drawing unrecognised renderable {}", renderable.getClass().getName());
				}
				return true;
			}
		}

		if (object instanceof ItemLayer)
		{
			return s.showGroundItems;
		}

		return sceneryDecision(scene, object, s);
	}

	/**
	 * Called several times per frame for entities. Returning false here removes the entity
	 * completely, including its clickbox, and lets other entities on the same tile be added.
	 * <p>
	 * Hidden other players and pets are removed this way: if they were only hidden visually,
	 * the client would still treat them as occupying the tile, and an NPC walking under them
	 * would be drawn incorrectly. This matches the core Entity Hider plugin. Hidden NPCs keep
	 * their clickboxes and are only hidden visually, in drawObject.
	 */
	@Override
	public boolean addEntity(Renderable renderable, boolean ui)
	{
		Settings s = settings;
		Boolean entity = entityDecision(renderable, s);
		if (entity == null || entity)
		{
			return true;
		}
		if (ui)
		{
			return !s.hideHiddenEntityUi;
		}
		return !hiddenByPlayerOrPetCategory(renderable, s);
	}

	/**
	 * True for other players and pets hidden by their category toggle. Anything hidden
	 * because its ID or name is blacklisted keeps its clickbox, so clickboxes are never
	 * removed by ID.
	 */
	private boolean hiddenByPlayerOrPetCategory(Renderable renderable, Settings s)
	{
		if (renderable instanceof Player)
		{
			return renderable != client.getLocalPlayer() && !s.showOtherPlayers;
		}
		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			NPCComposition composition = npc.getComposition();
			return composition != null && composition.isFollower()
				&& !s.showPets && !matchesNpc(s.blacklistNpcs, npc);
		}
		return false;
	}

	/**
	 * Decides for NPCs, players, projectiles, spotanims, ground items and boats.
	 * Returns null if the renderable isn't one of those.
	 */
	private Boolean entityDecision(Renderable renderable, Settings s)
	{
		if (renderable instanceof Player)
		{
			return renderable == client.getLocalPlayer() ? s.showSelf : s.showOtherPlayers;
		}

		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			if (matchesNpc(s.blacklistNpcs, npc))
			{
				return false;
			}
			if (matchesNpc(s.whitelistNpcs, npc))
			{
				return true;
			}
			NPCComposition composition = npc.getComposition();
			if (composition != null && composition.isFollower())
			{
				return s.showPets;
			}
			return s.showNpcs;
		}

		if (renderable instanceof Projectile)
		{
			return s.showProjectiles;
		}

		if (renderable instanceof GraphicsObject)
		{
			return s.showSpotanims;
		}

		if (renderable instanceof TileItem)
		{
			return s.showGroundItems;
		}

		if (renderable instanceof Scene)
		{
			return boatDecision((Scene) renderable, s);
		}

		return null;
	}

	private boolean boatDecision(Scene boatScene, Settings s)
	{
		if (s.showBoats)
		{
			return true;
		}
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return true;
		}
		WorldEntity boat = top.worldEntities().byIndex(boatScene.getWorldViewId());
		if (boat == null)
		{
			return true;
		}
		// Always draw the boat you're on
		return boat.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER;
	}

	private static boolean isSceneryRenderable(Renderable renderable)
	{
		return renderable instanceof Model
			|| renderable instanceof ModelData
			|| renderable instanceof DynamicObject;
	}

	private boolean sceneryDecision(Scene scene, TileObject object, Settings s)
	{
		int id = object.getId();

		// Individually clicked objects beat name lists: they're the more specific choice
		if (!s.pinnedNames.isEmpty())
		{
			String name = objectName(id);
			if (name != null && s.pinnedNames.contains(name))
			{
				PinnedObject key = new PinnedObject(name, templateLocation(object, scene));
				if (s.pinnedBlacklist.contains(key))
				{
					return false;
				}
				if (s.pinnedWhitelist.contains(key))
				{
					return true;
				}
			}
		}

		if (matchesObject(s.blacklistObjects, id))
		{
			return false;
		}
		if (matchesObject(s.whitelistObjects, id))
		{
			return true;
		}
		if (s.showSpawnedObjects
			&& (learnedObjectIds.contains(id) || spawnedObjectHashes.contains(object.getHash())))
		{
			return true;
		}
		return s.showScenery;
	}

	// ---------------------------------------------------------------- settings

	/** Immutable copy of the config, safe to read from any thread. */
	private static final class Settings
	{
		static final Settings DEFAULT = new Settings();

		final boolean showSelf;
		final boolean showOtherPlayers;
		final boolean showNpcs;
		final boolean showPets;
		final boolean showProjectiles;
		final boolean showSpotanims;
		final boolean showGroundItems;
		final boolean showSpawnedObjects;
		final boolean showScenery;
		final boolean showTerrain;
		final boolean showBoats;
		final boolean hideHiddenEntityUi;

		final NameList whitelistNpcs;
		final NameList whitelistObjects;
		final NameList blacklistNpcs;
		final NameList blacklistObjects;
		final Set<PinnedObject> pinnedWhitelist;
		final Set<PinnedObject> pinnedBlacklist;
		/** Names of all pinned objects, for a cheap first check. */
		final Set<String> pinnedNames;

		private Settings()
		{
			showSelf = true;
			showOtherPlayers = false;
			showNpcs = true;
			showPets = false;
			showProjectiles = true;
			showSpotanims = true;
			showGroundItems = true;
			showSpawnedObjects = true;
			showScenery = false;
			showTerrain = true;
			showBoats = false;
			hideHiddenEntityUi = false;
			whitelistNpcs = whitelistObjects = NameList.EMPTY;
			blacklistNpcs = blacklistObjects = NameList.EMPTY;
			pinnedWhitelist = pinnedBlacklist = Collections.emptySet();
			pinnedNames = Collections.emptySet();
		}

		private Settings(SelectiveHiderConfig c)
		{
			showSelf = c.showSelf();
			showOtherPlayers = c.showOtherPlayers();
			showNpcs = c.showNpcs();
			showPets = c.showPets();
			showProjectiles = c.showProjectiles();
			showSpotanims = c.showSpotanims();
			showGroundItems = c.showGroundItems();
			showSpawnedObjects = c.showSpawnedObjects();
			showScenery = c.showScenery();
			showTerrain = c.showTerrain();
			showBoats = c.showBoats();
			hideHiddenEntityUi = c.hideHiddenEntityUi();
			whitelistNpcs = NameList.parse(c.whitelistNpcs());
			whitelistObjects = NameList.parse(c.whitelistObjects());
			blacklistNpcs = NameList.parse(c.blacklistNpcs());
			blacklistObjects = NameList.parse(c.blacklistObjects());
			pinnedWhitelist = PinnedObject.parseAll(c.pinnedWhitelistObjects());
			pinnedBlacklist = PinnedObject.parseAll(c.pinnedBlacklistObjects());
			Set<String> names = new HashSet<>();
			pinnedWhitelist.forEach(o -> names.add(o.name));
			pinnedBlacklist.forEach(o -> names.add(o.name));
			pinnedNames = Collections.unmodifiableSet(names);
		}

		boolean hasObjectNames()
		{
			return !whitelistObjects.names.isEmpty() || !blacklistObjects.names.isEmpty() || !pinnedNames.isEmpty();
		}

		static Settings from(SelectiveHiderConfig c)
		{
			return new Settings(c);
		}
	}
}
