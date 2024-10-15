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

    public static String l10n(String key, Object... args){
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
