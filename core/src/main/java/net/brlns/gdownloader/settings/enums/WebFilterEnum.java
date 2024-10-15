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

import java.util.function.Function;
import lombok.Getter;

import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum WebFilterEnum implements ISettingsEnum{
    YOUTUBE("Youtube", s -> s.contains("youtube.com") && !isYoutubePlaylist(s) || s.contains("youtu.be") || isYoutubeChannel(s)),
    YOUTUBE_PLAYLIST("Youtube Playlists", s -> s.contains("youtube.com") && isYoutubePlaylist(s)),
    TWITCH("Twitch", s -> s.contains("twitch.tv")),//best[height<=480]+Audio_Only
    FACEBOOK("Facebook", s -> s.contains("facebook.com")),
    TWITTER("X/Twitter", s -> s.contains("twitter.com") || s.matches("^(https?:\\/\\/)?(www\\.)?x\\.com(\\/.*)?$")),//It was such a better name
    CRUNCHYROLL("Crunchyroll", s -> s.contains("crunchyroll.com")),
    DROPOUT("Dropout", s -> s.contains("dropout.tv")),
    DEFAULT("Download", s -> false);

    private final String displayName;

    private final Function<String, Boolean> pattern;

    private WebFilterEnum(String displayNameIn, Function<String, Boolean> patternIn){
        displayName = displayNameIn;
        pattern = patternIn;
    }

    @Override
    public String getDisplayName(){
        return this == DEFAULT ? l10n("enums.web_filter.default") : displayName;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    public static boolean isYoutubePlaylist(String s){
        return s.contains("youtube") && (s.contains("list=") || s.contains("/playlist"));
    }

    public static boolean isYoutubeChannel(String s){
        return s.contains("youtube") && (s.contains("/@") || s.contains("/channel"));
    }

}
