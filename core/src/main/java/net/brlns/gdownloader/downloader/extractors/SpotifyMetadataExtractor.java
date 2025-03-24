/*
 * Copyright (C) 2025 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.downloader.extractors;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * This class retrieves Spotify metadata by leveraging their oEmbed API
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SpotifyMetadataExtractor extends OEmbedMetadataExtractor {

    private static final Map<String, String> SPECIAL_URLS = new HashMap<>(3);

    static {
        SPECIAL_URLS.put("spotify.com/collection/tracks", l10n("spotify.liked_songs"));
        SPECIAL_URLS.put("spotify.com/collection/playlists", l10n("spotify.all_user_saved_playlists"));
        SPECIAL_URLS.put("spotify.com/collection/albums", l10n("spotify.all_user_saved_albums"));
    }

    @Override
    public boolean canConsume(String urlIn) {
        return urlIn.matches("^(https?:\\/\\/)?(([a-zA-Z0-9-]+)\\.)?spotify\\.(com|link)(\\/.*)?$");
    }

    @Override
    public Optional<MediaInfo> fetchMetadata(String urlIn) throws Exception {
        if (!urlIn.contains("spotify.com") && !urlIn.contains("spotify.link")) {
            throw new IllegalArgumentException("Expected a valid Spotify URL");
        }

        MediaInfo specialMediaInfo = getMediaInfoIfSpecialUrl(urlIn);
        if (specialMediaInfo != null) {
            return Optional.of(specialMediaInfo);
        }

        return super.fetchMetadata(urlIn);
    }

    @Nullable
    private static MediaInfo getMediaInfoIfSpecialUrl(String spotifyUrl) {
        String specialUrlName = SPECIAL_URLS.keySet().stream()
            .filter(spotifyUrl::contains)
            .findFirst()
            .map(SPECIAL_URLS::get)
            .orElse(null);

        if (specialUrlName != null) {
            MediaInfo mediaInfo = new MediaInfo();
            mediaInfo.setChannelId("Spotify");
            mediaInfo.setThumbnail("https://storage.googleapis.com/pr-newsroom-wp/1/2023/05/Spotify_Primary_Logo_RGB_Green.png");
            mediaInfo.setPlaylistTitle(specialUrlName);
            mediaInfo.setTitle(specialUrlName);

            return mediaInfo;
        }

        return null;
    }

    @Override
    protected MediaInfo convertToMediaInfo(OEmbedDTO response, String urlIn) {
        MediaInfo mediaInfo = super.convertToMediaInfo(response, urlIn);

        String trackId = URLUtils.getSpotifyTrackId(urlIn);
        if (trackId != null) {
            mediaInfo.setId(trackId);
        }

        if (response.getTitle() != null && isPlaylist(urlIn)) {
            mediaInfo.setPlaylistTitle(response.getTitle());
        }

        return mediaInfo;
    }

    private static boolean isPlaylist(String spotifyUrl) {
        return spotifyUrl.matches(".*spotify\\.com/(album|artist|playlist)/.*");
    }

    //in: https://open.spotify.com/track/0heJlRkloNhkrBU9ROnM9Y?si=7d802b22d6084c23
    //out: {
    //  "html": "",
    //  "iframe_url": "https://open.spotify.com/embed/track/0heJlRkloNhkrBU9ROnM9Y?si=a11537f5988f4ec1&utm_source=oembed",
    //  "width": 456,
    //  "height": 152,
    //  "version": "1.0",
    //  "provider_name": "Spotify",
    //  "provider_url": "https://spotify.com",
    //  "type": "rich",
    //  "title": "Winterchild",
    //  "thumbnail_url": "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e021b5f2d8c737ed5a780116ba9",
    //  "thumbnail_width": 300,
    //  "thumbnail_height": 300
    //}
}
