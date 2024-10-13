package com.musicfavourites;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.SpriteID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
    name = "Music Favourites",
    conflicts = "Music"
)
public class MusicFavouritesPlugin extends Plugin {
    @Inject
    private Client client;

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

    @Provides
    MusicFavouritesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MusicFavouritesConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        loadFavouriteTracks();
        addToggleFavouritesButton();
    }

    @Override
    protected void shutDown() throws Exception {
        favouriteTracks.clear();
        removeToggleFavouritesButton();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        if (widgetLoaded.getGroupId() != InterfaceID.MUSIC) {
            return;
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

        addToggleFavouritesButton();
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

    private void addToggleFavouritesButton() {
        Widget musicHeader = client.getWidget(ComponentID.MUSIC_CONTAINER);
        if (musicHeader == null) {
            return;
        }
        musicHeader.deleteAllChildren();

        toggleFavouritesButton = musicHeader.createChild(0, WidgetType.GRAPHIC);
        toggleFavouritesButton.setSpriteId(SpriteID.PRAYER_RAPID_RESTORE);
        toggleFavouritesButton.setOriginalWidth(18);
        toggleFavouritesButton.setOriginalHeight(17);
        toggleFavouritesButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
        toggleFavouritesButton.setOriginalX(5);
        toggleFavouritesButton.setOriginalY(32);
        toggleFavouritesButton.setAction(0, "View");
        toggleFavouritesButton.setName("Favourites");
        toggleFavouritesButton.setHasListener(true);
        toggleFavouritesButton.setOnOpListener((JavaScriptCallback) event -> {
            toggleFavouritesView();
        });
        toggleFavouritesButton.revalidate();
    }

    private void removeToggleFavouritesButton() {
        Widget musicHeader = client.getWidget(ComponentID.MUSIC_CONTAINER);
        if (musicHeader == null) {
            return;
        }
        musicHeader.deleteAllChildren();
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

        Collection<Widget> tracksToShow = isFavouritesShown ? getSortedFavoriteTracks() : allTracks;

        for (Widget favoriteTrack : getSortedFavoriteTracks()) {
            log.info(favoriteTrack.getText());
        }

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
        config.setFavouriteTracks(favouriteTrackNames);
    }

    private List<Widget> getSortedFavoriteTracks() {
        return favouriteTracks.stream()
            .sorted(Comparator.comparingInt(allTracks::indexOf))
            .collect(Collectors.toList());
    }
}
