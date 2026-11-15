package com.selectivehider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.util.Text;

/**
 * A config list of names, e.g. {@code Tree stump, Acid pool}.
 * Names match case-insensitively and must be the whole name. Quotes around a name are allowed.
 */
final class NameList
{
	static final NameList EMPTY = new NameList(Collections.emptySet());

	/** Names standardised (lowercase, no tags) for matching. */
	final Set<String> names;

	/** Names as the user typed them, kept so rewriting the list doesn't change their case. */
	private final Set<String> rawNames;

	NameList(Collection<String> rawNames)
	{
		Set<String> raw = new LinkedHashSet<>();
		Set<String> standardised = new LinkedHashSet<>();
		for (String name : rawNames)
		{
			String cleaned = name.replace("\"", "").trim();
			String key = Text.standardize(cleaned);
			if (!key.isEmpty() && standardised.add(key))
			{
				raw.add(cleaned);
			}
		}
		this.rawNames = Collections.unmodifiableSet(raw);
		this.names = Collections.unmodifiableSet(standardised);
	}

	static NameList parse(String text)
	{
		if (text == null || text.isBlank())
		{
			return EMPTY;
		}
		Set<String> parts = new LinkedHashSet<>();
		for (String part : text.split("[,\\n]"))
		{
			parts.add(part);
		}
		return new NameList(parts);
	}

	boolean contains(String name)
	{
		return name != null && !names.isEmpty() && names.contains(Text.standardize(name));
	}

	NameList with(String name)
	{
		Set<String> newNames = new LinkedHashSet<>(rawNames);
		newNames.add(name);
		return new NameList(newNames);
	}

	NameList without(String name)
	{
		String key = Text.standardize(name);
		Set<String> newNames = new LinkedHashSet<>();
		for (String raw : rawNames)
		{
			if (!Text.standardize(raw).equals(key))
			{
				newNames.add(raw);
			}
		}
		return new NameList(newNames);
	}

	String serialize()
	{
		return String.join(", ", rawNames);
	}
}
