package net.brlns.gdownloader.settings.enums;

import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum PlayListOptionEnum implements SettingsEnum{
    DOWNLOAD_PLAYLIST("enums.playlist_option.download_playlist"),
    DOWNLOAD_SINGLE("enums.playlist_option.download_single"),
    ALWAYS_ASK("enums.playlist_option.always_ask");

    private final String translationKey;

    private PlayListOptionEnum(String translationKeyIn){
        translationKey = translationKeyIn;
    }
}
