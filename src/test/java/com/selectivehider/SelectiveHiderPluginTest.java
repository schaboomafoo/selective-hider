package com.selectivehider;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Run this to launch RuneLite with the plugin loaded (add -ea to the VM options).
 */
public class SelectiveHiderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SelectiveHiderPlugin.class);
		RuneLite.main(args);
	}
}
