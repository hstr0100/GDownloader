/*
 * Copyright (C) 2024 hstr0100
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
package net.brlns.gdownloader.filters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BiliBiliFilter extends GenericFilter {

    public static final String ID = "bilibili";

    @SuppressWarnings("this-escape")
    public BiliBiliFilter() {
        setId(ID);
        setFilterName("BiliBili");
        setUrlRegex("^(https?:\\/\\/)?(www\\.)?bilibili\\.com(\\/.*)?$");
        setVideoNamePattern("%(title).60s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s");
        setAudioNamePattern(getVideoNamePattern().replace("%(resolution)s", "%(audio_bitrate)s"));
        setEmbedThumbnailAndMetadata(false);
    }
}
