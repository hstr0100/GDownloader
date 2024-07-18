package net.brlns.gdownloader.settings.enums;

import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum AudioBitrateEnum implements SettingsEnum{
    BITRATE_192(192, "192kbps"),
    BITRATE_256(256, "256kbps"),
    BITRATE_320(320, "320kbps");

    private final int value;
    private final String displayName;

    private AudioBitrateEnum(int valueIn, String displayNameIn){
        value = valueIn;
        displayName = displayNameIn;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }
}
