package com.musicfavourites;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "Music Favourites"
)
public class MusicFavouritesPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private MusicFavouritesConfig config;

    @Provides
    MusicFavouritesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MusicFavouritesConfig.class);
    }
}
