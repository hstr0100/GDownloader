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

import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class LightTheme extends AbstractTheme {

    public LightTheme() {
        super();
    }

    @Override
    protected void initColors() {
        put(ICON, new Color(135, 135, 135));
        put(ICON_ACTIVE, new Color(56, 192, 251));
        put(ICON_INACTIVE, new Color(239, 83, 80));
        put(ICON_HOVER, new Color(80, 80, 80));
        put(ICON_CLOSE, new Color(224, 64, 6));
        put(BACKGROUND, Color.WHITE);
        put(TEXT_AREA_BACKGROUND, new Color(242, 242, 242));
        put(TEXT_AREA_FOREGROUND, Color.DARK_GRAY);
        put(FOREGROUND, Color.DARK_GRAY);
        put(SIDE_PANEL, new Color(242, 242, 242));
        put(MEDIA_CARD_HOVER, new Color(218, 218, 218));
        put(MEDIA_CARD, new Color(242, 242, 242));
        put(MEDIA_CARD_SELECTED, new Color(200, 200, 200));
        put(MEDIA_CARD_THUMBNAIL, new Color(180, 180, 180));
        put(LIGHT_TEXT, new Color(102, 102, 102));
        put(BUTTON_FOREGROUND, Color.DARK_GRAY);
        put(BUTTON_BACKGROUND, Color.WHITE);
        put(BUTTON_HOVER, new Color(135, 135, 135));
        put(SCROLL_BAR_FOREGROUND, new Color(135, 135, 135));
        put(SLIDER_FOREGROUND, new Color(135, 135, 135));
        put(CHECK_BOX_HOVER, new Color(80, 80, 80));
        put(SIDE_PANEL_SELECTED, new Color(218, 218, 218));
        put(TOOLTIP_BACKGROUND, new Color(230, 230, 230));
        put(TOOLTIP_FOREGROUND, Color.DARK_GRAY);
        put(COMBO_BOX_BACKGROUND, new Color(242, 242, 242));
        put(COMBO_BOX_FOREGROUND, Color.DARK_GRAY);
        put(COMBO_BOX_SELECTION_BACKGROUND, Color.LIGHT_GRAY);
        put(COMBO_BOX_SELECTION_FOREGROUND, Color.WHITE);
        put(SLIDER_TRACK, Color.LIGHT_GRAY);
        put(COMBO_BOX_BUTTON_FOREGROUND, Color.LIGHT_GRAY);
        put(COMBO_BOX_BUTTON_BACKGROUND, Color.WHITE);
        put(MENU_ITEM_ARMED, Color.LIGHT_GRAY);
        put(MENU_ITEM_PRESSED, Color.GRAY);
        put(QUEUE_ACTIVE_ICON, new Color(29, 233, 182));
        put(TOAST_BACKGROUND, new Color(230, 230, 230, 230));
        put(TOAST_ERROR, Color.RED);
        put(TOAST_WARNING, new Color(255, 214, 0));
        put(TOAST_INFO, new Color(30, 136, 229));
        put(SETTINGS_ROW_BACKGROUND_LIGHT, Color.WHITE);
        put(SETTINGS_ROW_BACKGROUND_DARK, new Color(251, 251, 251));
    }

    @Override
    public String getAppIconPath() {
        return "/assets/app_icon_light.png";
    }

    @Override
    public String getTrayIconPath() {
        return "/assets/tray_icon_light.png";
    }
}
