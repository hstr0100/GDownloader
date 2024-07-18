package net.brlns.gdownloader;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import net.brlns.gdownloader.settings.enums.LanguageEnum;

/**
 * I did write a more robust localization system for one of my previous projects,
 * but using something similar to it here would be overkill
 *
 * @author Gabriel / hstr0100 / vertx010
 */
public class Language{

    private static ResourceBundle LANGUAGE_BUNDLE;

    public static String get(String key, Object... args){
        if(LANGUAGE_BUNDLE == null){
            throw new RuntimeException("Language was not initialized");
        }

        String pattern = LANGUAGE_BUNDLE.getString(key);
        if(pattern == null){
            throw new RuntimeException("Unknown language key: " + key);
        }

        return MessageFormat.format(pattern, args);
    }

    public static void initLanguage(LanguageEnum language){
        Locale.setDefault(language.getLocale());

        LANGUAGE_BUNDLE = ResourceBundle.getBundle("lang/language");
    }
}
