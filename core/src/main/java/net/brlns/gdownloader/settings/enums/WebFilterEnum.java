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

import lombok.Getter;
import net.brlns.gdownloader.settings.filters.*;

/**
 * The sole purpose of this class as of 2024-10-20 is for config migration.
 *
 * Do not use.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@Deprecated
public enum WebFilterEnum implements ISettingsEnum{
    YOUTUBE(YoutubeFilter.class),
    YOUTUBE_PLAYLIST(YoutubePlaylistFilter.class),
    TWITCH(TwitchFilter.class),
    FACEBOOK(FacebookFilter.class),
    TWITTER(XFilter.class),
    CRUNCHYROLL(CrunchyrollFilter.class),
    DROPOUT(DropoutFilter.class),
    REDDIT(RedditFilter.class),
    DEFAULT(GenericFilter.class);

    private final Class<? extends AbstractUrlFilter> filterClass;

    WebFilterEnum(Class<? extends AbstractUrlFilter> filterClass){
        this.filterClass = filterClass;
    }

    @Override
    public String getDisplayName(){
        return "";
    }

    @Override
    public String getTranslationKey(){
        return "";
    }
}
