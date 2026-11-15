package com.selectivehider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

/**
 * One specific object picked with the shift-click menu, identified by its name and tile.
 * In instances the tile is the instance's template tile, so it's the same on every visit.
 */
final class PinnedObject
{
	final String name;
	final int x;
	final int y;
	final int plane;

	PinnedObject(String name, WorldPoint point)
	{
		this(name, point.getX(), point.getY(), point.getPlane());
	}

	private PinnedObject(String name, int x, int y, int plane)
	{
		this.name = Text.standardize(name);
		this.x = x;
		this.y = y;
		this.plane = plane;
	}

	/** Stored as {@code name|x|y|plane}, one entry per line. */
	static Set<PinnedObject> parseAll(String text)
	{
		if (text == null || text.isBlank())
		{
			return Collections.emptySet();
		}
		Set<PinnedObject> result = new LinkedHashSet<>();
		for (String line : text.split("\\n"))
		{
			String[] parts = line.split("\\|");
			if (parts.length != 4)
			{
				continue;
			}
			try
			{
				result.add(new PinnedObject(parts[0], Integer.parseInt(parts[1].trim()),
					Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())));
			}
			catch (NumberFormatException ignored)
			{
				// Skip malformed entries
			}
		}
		return Collections.unmodifiableSet(result);
	}

	static String serializeAll(Set<PinnedObject> objects)
	{
		return objects.stream()
			.map(o -> o.name + "|" + o.x + "|" + o.y + "|" + o.plane)
			.collect(Collectors.joining("\n"));
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof PinnedObject))
		{
			return false;
		}
		PinnedObject other = (PinnedObject) o;
		return x == other.x && y == other.y && plane == other.plane && name.equals(other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, x, y, plane);
	}
}
