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

import java.util.regex.Pattern;
import lombok.Getter;

import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum WebFilterEnum implements ISettingsEnum{
    YOUTUBE("Youtube", "^(https?:\\/\\/)?(www\\.)?(youtube\\.com|youtu\\.be)(?!.*(\\/live|\\/playlist|list=)).*$"),
    YOUTUBE_PLAYLIST("Youtube Playlists", "^(https?:\\/\\/)?(www\\.)?youtube\\.com.*(list=|\\/playlist).*$"),
    TWITCH("Twitch", "^(https?:\\/\\/)?(www\\.)?twitch\\.tv(\\/.*)?$"),
    FACEBOOK("Facebook", "^(https?:\\/\\/)?(www\\.)?facebook\\.com(\\/.*)?$"),
    TWITTER("X/Twitter", "^(https?:\\/\\/)?(www\\.)?(x|twitter)\\.com(\\/.*)?$"),
    CRUNCHYROLL("Crunchyroll", "^(https?:\\/\\/)?(www\\.)?crunchyroll\\.com(\\/.*)?$"),
    DROPOUT("Dropout", "^(https?:\\/\\/)?(www\\.)?dropout\\.tv(\\/.*)?$"),
    REDDIT("Reddit", "^(https?:\\/\\/)?(www\\.|old\\.|new\\.)?reddit\\.com(\\/.*)?$"),
    DEFAULT("", "");

    private final String displayName;
    private final Pattern pattern;

    WebFilterEnum(String displayNameIn, String regex){
        this.displayName = displayNameIn;
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public String getDisplayName(){
        return this == DEFAULT ? l10n("enums.web_filter.default") : displayName;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    public boolean matches(String url){
        return this == DEFAULT ? false : pattern.matcher(url).matches();
    }

    public static boolean isYoutubeChannel(String s){
        return s.contains("youtube") && (s.contains("/@") || s.contains("/channel"));
    }

}
