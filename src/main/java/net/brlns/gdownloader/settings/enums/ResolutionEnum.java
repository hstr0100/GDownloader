package net.brlns.gdownloader.settings.enums;

import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum ResolutionEnum implements SettingsEnum{
    RES_144(144, "144p"),
    RES_240(240, "240p"),
    RES_360(360, "360p"),
    RES_480(480, "480p"),
    RES_720(720, "720p"),
    RES_1080(1080, "1080p"),
    RES_2160(2160, "4K");

    private final int value;
    private final String displayName;

    private ResolutionEnum(int valueIn, String displayNameIn){
        value = valueIn;
        displayName = displayNameIn;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    public boolean isResolutionValid(ResolutionEnum minResolution, ResolutionEnum maxResolution){
        return minResolution.getValue() <= maxResolution.getValue();
    }

    public ResolutionEnum getValidMin(ResolutionEnum min){
        if(isResolutionValid(min, this)){
            return min;
        }

        Optional<ResolutionEnum> result = Stream.of(values())
            .filter(e -> isResolutionValid(e, this))
            .max((e1, e2) -> Integer.compare(e1.getValue(), e2.getValue()));

        return result.orElse(min);
    }

    public ResolutionEnum getValidMax(ResolutionEnum max){
        if(isResolutionValid(this, max)){
            return max;
        }

        Optional<ResolutionEnum> result = Stream.of(values())
            .filter(e -> isResolutionValid(this, e))
            .min((e1, e2) -> Integer.compare(e1.getValue(), e2.getValue()));

        return result.orElse(max);
    }
}
