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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * This class retrieves Spotify metadata by leveraging their oEmbed API
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SpotifyMetadataExtractor {

    private static final Map<String, String> SPECIAL_URLS = new HashMap<>(3);

    static {
        SPECIAL_URLS.put("spotify.com/collection/tracks", l10n("spotify.liked_songs"));
        SPECIAL_URLS.put("spotify.com/collection/playlists", l10n("spotify.all_user_saved_playlists"));
        SPECIAL_URLS.put("spotify.com/collection/albums", l10n("spotify.all_user_saved_albums"));
    }

    private static final String OEMBED_API_URL = "https://open.spotify.com/oembed?url=";

    private static final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_2)
        .build();

    @Nullable
    public static MediaInfo queryMetadata(String spotifyUrl) throws Exception {
        if (!spotifyUrl.contains("spotify.com/")) {
            throw new IllegalArgumentException("URL must be a valid Spotify URL");
        }

        MediaInfo specialMediaInfo = getMediaInfoIfSpecialUrl(spotifyUrl);
        if (specialMediaInfo != null) {
            return specialMediaInfo;
        }

        String encodedUrl = URLEncoder.encode(spotifyUrl, StandardCharsets.UTF_8.toString());
        String apiUrl = OEMBED_API_URL + encodedUrl;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", URLUtils.GLOBAL_USER_AGENT)
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        if (body.isEmpty() || !body.startsWith("{")) {
            return null;
        }

        //log.info(body);
        SpotifyOEmbedDTO dto = GDownloader.OBJECT_MAPPER.readValue(body, SpotifyOEmbedDTO.class);

        return convertToMediaInfo(dto, spotifyUrl);
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

    private static MediaInfo convertToMediaInfo(SpotifyOEmbedDTO response, String spotifyUrl) {
        MediaInfo mediaInfo = new MediaInfo();

        String trackId = URLUtils.getSpotifyTrackId(spotifyUrl);
        if (trackId != null) {
            mediaInfo.setId(trackId);
        }

        if (response.getTitle() != null) {
            String title = response.getTitle();
            int dashIndex = title.indexOf(" - ");
            if (dashIndex != -1) {
                mediaInfo.setChannelId(title.substring(0, dashIndex).trim());
            }

            mediaInfo.setTitle(title);

            if (isPlaylist(spotifyUrl)) {
                mediaInfo.setPlaylistTitle(title);
            }
        }

        mediaInfo.setThumbnail(response.getThumbnailUrl());
        mediaInfo.setWidth(response.getWidth());
        mediaInfo.setHeight(response.getHeight());

        return mediaInfo;
    }

    private static boolean isPlaylist(String spotifyUrl) {
        return spotifyUrl.contains("spotify.com/album/")
            || spotifyUrl.contains("spotify.com/artist/")
            || spotifyUrl.contains("spotify.com/playlist/");
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
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyOEmbedDTO {

        private String html;
        private String type;
        private String version;
        private String title;

        @JsonProperty("provider_name")
        private String providerName;

        @JsonProperty("provider_url")
        private String providerUrl;

        private Integer width;
        private Integer height;

        @JsonProperty("thumbnail_url")
        private String thumbnailUrl;

        @JsonProperty("thumbnail_width")
        private Integer thumbnailWidth;

        @JsonProperty("thumbnail_height")
        private Integer thumbnailHeight;
    }
}
