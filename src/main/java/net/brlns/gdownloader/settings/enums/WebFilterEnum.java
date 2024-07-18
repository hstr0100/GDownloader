package net.brlns.gdownloader.settings.enums;

import java.util.function.Function;
import lombok.Getter;
import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum WebFilterEnum implements SettingsEnum{
    YOUTUBE("Youtube", s -> (s.contains("youtube.com/watch?v=") || s.contains("youtube.com/embed")) && !isYoutubePlaylist(s) || s.contains("youtu.be")),
    YOUTUBE_PLAYLIST("Youtube - PLAYLIST", s -> s.contains("youtube.com/") && isYoutubePlaylist(s)),
    TWITCH("Twitch", s -> s.contains("twitch.tv")),//best[height<=480]+Audio_Only
    FACEBOOK("Facebook", s -> s.contains("facebook.com")),
    TWITTER("Twitter", s -> s.contains("twitter") || s.contains("x.com")),
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
        return this == DEFAULT ? get("enums.web_filter.default") : displayName;
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
