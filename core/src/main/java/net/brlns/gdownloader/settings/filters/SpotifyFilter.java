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
package net.brlns.gdownloader.settings.filters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyFilter extends GenericFilter {

    public static final String ID = "spotify";

    @SuppressWarnings("this-escape")
    public SpotifyFilter() {
        setId(ID);
        setFilterName("Spotify");
        setUrlRegex("^(https?:\\/\\/)?(([a-zA-Z0-9-]+)\\.)?spotify\\.com(\\/.*)?$");
        setVideoNamePattern("");
        setAudioNamePattern("%(artist)s/%(album)s/%(title)s.%(ext)s");// Both yt-dlp and spotDL templates are supported here.
        setEmbedThumbnailAndMetadata(true);
    }
}
