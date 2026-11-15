package com.selectivehider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * A config list of IDs and quoted names, e.g. {@code 1234, 5678, "Tree stump"}.
 * Names match case-insensitively and must be the whole name.
 */
final class IdNameList
{
	static final IdNameList EMPTY = new IdNameList(Collections.emptySet(), Collections.emptySet());

	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
	private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

	/** IDs in the list. */
	final Set<Integer> ids;

	/** Names in the list, standardised (lowercase, no tags) for matching. */
	final Set<String> names;

	/** Names as the user typed them, kept so rewriting the list doesn't change their case. */
	private final Set<String> rawNames;

	IdNameList(Collection<Integer> ids, Collection<String> rawNames)
	{
		this.ids = Collections.unmodifiableSet(new TreeSet<>(ids));
		Set<String> raw = new LinkedHashSet<>();
		Set<String> standardised = new LinkedHashSet<>();
		for (String name : rawNames)
		{
			String trimmed = name.trim();
			String key = Text.standardize(trimmed);
			if (!key.isEmpty() && standardised.add(key))
			{
				raw.add(trimmed);
			}
		}
		this.rawNames = Collections.unmodifiableSet(raw);
		this.names = Collections.unmodifiableSet(standardised);
	}

	static IdNameList parse(String text)
	{
		if (text == null || text.isBlank())
		{
			return EMPTY;
		}

		Set<String> rawNames = new LinkedHashSet<>();
		Matcher m = QUOTED.matcher(text);
		while (m.find())
		{
			rawNames.add(m.group(1));
		}
		String withoutNames = QUOTED.matcher(text).replaceAll(" ");

		Set<Integer> ids = new LinkedHashSet<>();
		for (String part : SEPARATORS.split(withoutNames))
		{
			if (part.isEmpty())
			{
				continue;
			}
			try
			{
				ids.add(Integer.parseInt(part));
			}
			catch (NumberFormatException ignored)
			{
				// Ignore anything that isn't a number or a quoted name
			}
		}
		return new IdNameList(ids, rawNames);
	}

	IdNameList with(int id)
	{
		Set<Integer> newIds = new LinkedHashSet<>(ids);
		newIds.add(id);
		return new IdNameList(newIds, rawNames);
	}

	IdNameList without(int id)
	{
		Set<Integer> newIds = new LinkedHashSet<>(ids);
		newIds.remove(id);
		return new IdNameList(newIds, rawNames);
	}

	String serialize()
	{
		StringBuilder sb = new StringBuilder();
		for (Integer id : ids)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(id);
		}
		for (String name : rawNames)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append('"').append(name).append('"');
		}
		return sb.toString();
	}
}
