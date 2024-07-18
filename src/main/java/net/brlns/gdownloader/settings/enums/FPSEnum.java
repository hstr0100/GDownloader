package net.brlns.gdownloader.settings.enums;

import lombok.Getter;
import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum FPSEnum implements SettingsEnum{
    FPS_30(30),
    FPS_60(60);

    private final int value;

    private FPSEnum(int valueIn){
        value = valueIn;
    }

    @Override
    public String getDisplayName(){
        return get("enums.fps", value);
    }

    @Override
    public String getTranslationKey(){
        return "";
    }
}
