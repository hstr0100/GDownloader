package net.brlns.gdownloader.settings.enums;

import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum QualitySelectorEnum implements SettingsEnum{
    BEST_VIDEO("bestvideo", "enums.quality_selector.bestvideo"),
    BEST("best", "enums.quality_selector.best"),
    WORST("worst", "enums.quality_selector.worst");

    private final String value;
    private final String translationKey;

    private QualitySelectorEnum(String valueIn, String translationKeyIn){
        value = valueIn;
        translationKey = translationKeyIn;
    }
}
