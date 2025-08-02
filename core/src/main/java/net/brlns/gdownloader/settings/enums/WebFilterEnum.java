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
package net.brlns.gdownloader.settings.enums;

import net.brlns.gdownloader.filters.YoutubeFilter;
import net.brlns.gdownloader.filters.CrunchyrollFilter;
import net.brlns.gdownloader.filters.XFilter;
import net.brlns.gdownloader.filters.YoutubePlaylistFilter;
import net.brlns.gdownloader.filters.RedditFilter;
import net.brlns.gdownloader.filters.TwitchFilter;
import net.brlns.gdownloader.filters.GenericFilter;
import net.brlns.gdownloader.filters.FacebookFilter;
import net.brlns.gdownloader.filters.DropoutFilter;
import lombok.Getter;

/**
 * The sole purpose of this enum as of 2024-10-20 is for config migration.
 *
 * Do not use.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@Deprecated
public enum WebFilterEnum implements ISettingsEnum {
    YOUTUBE(YoutubeFilter.ID),
    YOUTUBE_PLAYLIST(YoutubePlaylistFilter.ID),
    TWITCH(TwitchFilter.ID),
    FACEBOOK(FacebookFilter.ID),
    TWITTER(XFilter.ID),
    CRUNCHYROLL(CrunchyrollFilter.ID),
    DROPOUT(DropoutFilter.ID),
    REDDIT(RedditFilter.ID),
    DEFAULT(GenericFilter.ID);

    private final String id;

    private WebFilterEnum(String idIn) {
        id = idIn;
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getTranslationKey() {
        return "";
    }
}
