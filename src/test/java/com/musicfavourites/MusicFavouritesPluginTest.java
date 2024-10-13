package com.musicfavourites;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MusicFavouritesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MusicFavouritesPlugin.class);
		RuneLite.main(args);
	}
}