package com.musicfavourites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MusicFavouritesConfig.GROUP)
public interface MusicFavouritesConfig extends Config {
    String GROUP = "musicfavourites";

    @ConfigItem(
        keyName = "favouriteTracks",
        name = "Favourite Tracks",
        description = "List of favourite track names"
    )
    default String favouriteTracks() {
        return "";
    }
}
