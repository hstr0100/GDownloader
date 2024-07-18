package net.brlns.gdownloader.settings.enums;

import java.util.Locale;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum LanguageEnum implements SettingsEnum{
    ENGLISH(Locale.ENGLISH),
    BRAZIL_PORTUGUESE(new Locale("pt", "BR"));

    private final Locale locale;

    private LanguageEnum(Locale localeIn){
        locale = localeIn;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    @Override
    public String getDisplayName(){
        return locale.getDisplayName(Locale.getDefault());
    }
}
