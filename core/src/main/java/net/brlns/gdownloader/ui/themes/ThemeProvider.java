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
package net.brlns.gdownloader.ui.themes;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.brlns.gdownloader.settings.enums.ThemeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ThemeProvider {

    private static final Map<ThemeEnum, AbstractTheme> THEMES = new HashMap<>();

    private static AbstractTheme CURRENT_THEME;

    static {
        THEMES.put(ThemeEnum.DARK, new DarkTheme());
        THEMES.put(ThemeEnum.LIGHT, new LightTheme());
    }

    public static AbstractTheme getTheme() {
        assert CURRENT_THEME != null;

        return CURRENT_THEME;
    }

    public static void setTheme(ThemeEnum themeEnum) {
        assert THEMES.containsKey(themeEnum);

        CURRENT_THEME = THEMES.get(themeEnum);
    }

    public static Color color(UIColors color) {
        return CURRENT_THEME.get(color);
    }
}
