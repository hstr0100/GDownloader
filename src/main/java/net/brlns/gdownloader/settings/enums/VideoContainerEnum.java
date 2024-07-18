package net.brlns.gdownloader.settings.enums;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public enum VideoContainerEnum implements SettingsEnum{
    MP4,
    MKV,
    WEBM;

    public String getValue(){
        return name().toLowerCase();
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    @Override
    public String getDisplayName(){
        return name().toLowerCase();
    }
}
