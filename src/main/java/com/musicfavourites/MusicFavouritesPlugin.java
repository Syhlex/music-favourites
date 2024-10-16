package com.musicfavourites;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.music.MusicPlugin;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
    name = "Music Favourites"
)
public class MusicFavouritesPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ChatboxPanelManager chatboxPanelManager;

    @Inject
    private MusicFavouritesConfig config;

    private static final int FAVOURITE_OPTION_INDEX = 2;
    private static final String ADD_FAVOURITE_OPTION_TEXT = "Add to Favourites";
    private static final String REMOVE_FAVOURITE_OPTION_TEXT = "Remove from Favourites";

    private List<Widget> allTracks;
    private Widget scrollContainer;

    private boolean isFavouritesShown = false;
    private Widget toggleFavouritesButton;
    private final Set<Widget> favouriteTracks = new HashSet<>();

    private boolean isCoreMusicPluginEnabled = false;
    private Widget musicSearchButton;
    private Widget musicFilterButton;
    private MusicState currentMusicFilter = MusicState.ALL;
    private String filterText = "";

    @Provides
    MusicFavouritesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MusicFavouritesConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        clientThread.invokeLater(this::initializePlugin);
    }

    @Override
    protected void shutDown() throws Exception {
        clientThread.invokeLater(this::teardownPlugin);
    }

    @Subscribe(priority = -1) // Run after MusicPlugin
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        if (widgetLoaded.getGroupId() != InterfaceID.MUSIC) {
            return;
        }

        initializePlugin();
    }

    @Subscribe
    public void onPluginChanged(PluginChanged pluginChanged) {
        if (pluginChanged.getPlugin() instanceof MusicPlugin) {
            isCoreMusicPluginEnabled = pluginManager.isPluginEnabled(pluginChanged.getPlugin());
            clientThread.invokeLater(this::addButtons);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals(MusicFavouritesConfig.GROUP) && configChanged.getKey().equals("favouriteTracks")) {
            clientThread.invokeLater(() -> {
                loadFavouriteTracks();
                updateMusicListUI();
            });
        }
    }

    private void initializePlugin() {
        Plugin musicPlugin = pluginManager.getPlugins().stream()
            .filter(plugin -> plugin instanceof MusicPlugin)
            .findAny()
            .orElse(null);

        if (pluginManager.isPluginEnabled(musicPlugin)) {
            isCoreMusicPluginEnabled = true;
        }

        Widget musicList = client.getWidget(ComponentID.MUSIC_LIST);
        scrollContainer = client.getWidget(ComponentID.MUSIC_SCROLL_CONTAINER);

        if (musicList == null) {
            return;
        }

        allTracks = Arrays.stream(musicList.getDynamicChildren())
            .sorted(Comparator.comparingInt(Widget::getRelativeY))
            .collect(Collectors.toList());

        loadFavouriteTracks();

        for (Widget track : allTracks) {
            track.setAction(FAVOURITE_OPTION_INDEX, favouriteTracks.contains(track) ?
                REMOVE_FAVOURITE_OPTION_TEXT
                : ADD_FAVOURITE_OPTION_TEXT
            );

            track.setOnOpListener((JavaScriptCallback) event -> {
                int optionIndex = event.getOp() - 1;
                if (optionIndex == FAVOURITE_OPTION_INDEX) {
                    toggleFavourite(track);
                }
            });
        }

        addButtons();
        updateMusicListUI();
    }

    private void teardownPlugin() {
        isFavouritesShown = false;
        favouriteTracks.clear();
        removeButtons();
        updateMusicListUI();

        for (Widget track : allTracks) {
            track.setAction(FAVOURITE_OPTION_INDEX, null);
        }

        chatboxPanelManager.close();
    }

    private void toggleFavourite(Widget track) {
        if (favouriteTracks.contains(track)) {
            favouriteTracks.remove(track);
            track.setAction(FAVOURITE_OPTION_INDEX, ADD_FAVOURITE_OPTION_TEXT);
        } else {
            favouriteTracks.add(track);
            track.setAction(FAVOURITE_OPTION_INDEX, REMOVE_FAVOURITE_OPTION_TEXT);
        }

        if (isFavouritesShown) {
            updateMusicListUI();
        }

        updateFavouriteTracksConfig();
    }

    private void addButtons() {
        Widget musicContainer = client.getWidget(ComponentID.MUSIC_CONTAINER);
        if (musicContainer == null) {
            return;
        }
        musicContainer.deleteAllChildren();

        toggleFavouritesButton = musicContainer.createChild(0, WidgetType.GRAPHIC);
        toggleFavouritesButton.setSpriteId(isFavouritesShown ? SpriteID.PRAYER_REDEMPTION : SpriteID.PRAYER_RAPID_RESTORE);
        toggleFavouritesButton.setOriginalWidth(18);
        toggleFavouritesButton.setOriginalHeight(17);
        toggleFavouritesButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
        toggleFavouritesButton.setOriginalX(isCoreMusicPluginEnabled ? 45 : 5);
        toggleFavouritesButton.setOriginalY(32);
        toggleFavouritesButton.setAction(0, "View");
        toggleFavouritesButton.setName(isFavouritesShown ? "All" : "Favourites");
        toggleFavouritesButton.setHasListener(true);
        toggleFavouritesButton.setOnOpListener((JavaScriptCallback) event -> {
            toggleFavouritesView();
        });
        toggleFavouritesButton.revalidate();

        if (isCoreMusicPluginEnabled) {
            musicSearchButton = musicContainer.createChild(-1, WidgetType.GRAPHIC);
            musicSearchButton.setSpriteId(SpriteID.GE_SEARCH);
            musicSearchButton.setOriginalWidth(18);
            musicSearchButton.setOriginalHeight(17);
            musicSearchButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
            musicSearchButton.setOriginalX(5);
            musicSearchButton.setOriginalY(32);
            musicSearchButton.setHasListener(true);
            musicSearchButton.setAction(1, "Open");
            musicSearchButton.setOnOpListener((JavaScriptCallback) e -> openSearch());
            musicSearchButton.setName("Search");
            musicSearchButton.revalidate();

            musicFilterButton = musicContainer.createChild(-1, WidgetType.GRAPHIC);
            musicFilterButton.setSpriteId(SpriteID.MINIMAP_ORB_PRAYER);
            musicFilterButton.setOriginalWidth(15);
            musicFilterButton.setOriginalHeight(15);
            musicFilterButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
            musicFilterButton.setOriginalX(25);
            musicFilterButton.setOriginalY(34);
            musicFilterButton.setHasListener(true);
            musicFilterButton.setAction(1, "Toggle");
            musicFilterButton.setOnOpListener((JavaScriptCallback) e -> toggleStatus());
            musicFilterButton.setName("All");
            musicFilterButton.revalidate();
        }
    }

    private void removeButtons() {
        Widget musicContainer = client.getWidget(ComponentID.MUSIC_CONTAINER);
        if (musicContainer == null) {
            return;
        }

        if (!isCoreMusicPluginEnabled) {
            musicContainer.deleteAllChildren();
        } else {
            toggleFavouritesButton.setHidden(true);
            toggleFavouritesButton = null; // Eligible for garbage collection
        }
    }

    private void toggleFavouritesView() {
        isFavouritesShown = !isFavouritesShown;
        updateMusicListUI();
        toggleFavouritesButton.setSpriteId(isFavouritesShown
            ? SpriteID.PRAYER_REDEMPTION
            : SpriteID.PRAYER_RAPID_RESTORE
        );
        toggleFavouritesButton.setName(isFavouritesShown ? "All" : "Favourites");
    }

    private void updateMusicListUI() {
        for (Widget track : allTracks) {
            track.setHidden(true);
        }

        Collection<Widget> tracksToShow = (isFavouritesShown ? getSortedFavoriteTracks() : allTracks)
            .stream()
            .filter(track -> track.getText().toLowerCase().contains(filterText))
            .filter(track -> currentMusicFilter == MusicState.ALL || track.getTextColor() == currentMusicFilter.getColor())
            .collect(Collectors.toList());

        int y = 3;

        for (Widget track : tracksToShow) {
            track.setHidden(false);
            track.setOriginalY(y);
            track.revalidate();
            y += track.getHeight();
        }

        y += 3;

        int newHeight = 0;

        if (scrollContainer.getScrollHeight() > 0) {
            newHeight = (scrollContainer.getScrollY() * y) / scrollContainer.getScrollHeight();
        }

        scrollContainer.setScrollHeight(y);
        scrollContainer.revalidateScroll();

        client.runScript(
            ScriptID.UPDATE_SCROLLBAR,
            ComponentID.MUSIC_SCROLLBAR,
            ComponentID.MUSIC_SCROLL_CONTAINER,
            newHeight
        );
    }

    private void loadFavouriteTracks() {
        if (allTracks == null) {
            return;
        }

        favouriteTracks.clear();

        String favouriteTracksConfig = config.favouriteTracks();
        if (!favouriteTracksConfig.isBlank()) {
            Set<String> savedTrackNames = new HashSet<>(Arrays.asList(favouriteTracksConfig.split(",")));

            for (Widget track : allTracks) {
                if (savedTrackNames.contains(track.getText())) {
                    favouriteTracks.add(track);
                }
            }
        }
    }

    private void updateFavouriteTracksConfig() {
        String favouriteTrackNames = favouriteTracks.stream()
            .map(Widget::getText)
            .collect(Collectors.joining(","));
        configManager.setConfiguration(MusicFavouritesConfig.GROUP, "favouriteTracks", favouriteTrackNames);
    }

    private List<Widget> getSortedFavoriteTracks() {
        return favouriteTracks.stream()
            .sorted(Comparator.comparingInt(allTracks::indexOf))
            .collect(Collectors.toList());
    }

    // MusicPlugin musicFilterButton and musicSearchButton logic modified to integrate with MusicFavouritesPlugin

    @AllArgsConstructor
    @Getter
    private enum MusicState {
        NOT_FOUND(0xff0000, "Locked", SpriteID.MINIMAP_ORB_HITPOINTS),
        FOUND(0xdc10d, "Unlocked", SpriteID.MINIMAP_ORB_HITPOINTS_POISON),
        ALL(0, "All", SpriteID.MINIMAP_ORB_PRAYER);

        private final int color;
        private final String name;
        private final int spriteID;
    }

    private void toggleStatus() {
        MusicState[] states = MusicState.values();
        currentMusicFilter = states[(currentMusicFilter.ordinal() + 1) % states.length];
        musicFilterButton.setSpriteId(currentMusicFilter.getSpriteID());
        musicFilterButton.setName(currentMusicFilter.getName());
        updateMusicListUI();
        client.playSoundEffect(SoundEffectID.UI_BOOP);
    }

    private void openSearch() {
        updateFilter("");
        client.playSoundEffect(SoundEffectID.UI_BOOP);
        musicSearchButton.setAction(1, "Close");
        musicSearchButton.setOnOpListener((JavaScriptCallback) e -> closeSearch());
        ChatboxTextInput searchInput = chatboxPanelManager.openTextInput("Search music list")
            .onChanged(s -> clientThread.invokeLater(() ->
                updateFilter(s.trim())
            ))
            .onDone(s -> false)
            .onClose(() ->
            {
                clientThread.invokeLater(() -> updateFilter(""));
                musicSearchButton.setOnOpListener((JavaScriptCallback) e -> openSearch());
                musicSearchButton.setAction(1, "Open");
            })
            .build();
    }

    private void closeSearch() {
        updateFilter("");
        chatboxPanelManager.close();
        client.playSoundEffect(SoundEffectID.UI_BOOP);
    }

    private void updateFilter(String input) {
        filterText = input.toLowerCase();
        updateMusicListUI();
    }
}
