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
public class DarkTheme extends AbstractTheme{

    public DarkTheme(){
        super();
    }

    @Override
    protected void initColors(){
        add(ICON, Color.WHITE);
        add(ICON_ACTIVE, new Color(56, 192, 251));
        add(ICON_HOVER, new Color(128, 128, 128));
        add(ICON_CLOSE, new Color(224, 64, 6));
        add(BACKGROUND, Color.DARK_GRAY);
        add(TEXT_AREA_BACKGROUND, Color.LIGHT_GRAY);
        add(TEXT_AREA_FOREGROUND, Color.BLACK);
        add(FOREGROUND, new Color(244, 244, 244));
        add(SIDE_PANEL, new Color(134, 134, 134));
        add(MEDIA_CARD_HOVER, new Color(56, 56, 56));
        add(MEDIA_CARD, new Color(80, 80, 80));
        add(MEDIA_CARD_THUMBNAIL, new Color(74, 74, 74));
        add(LIGHT_TEXT, Color.LIGHT_GRAY);
        add(BUTTON_FOREGROUND, Color.DARK_GRAY);
        add(BUTTON_BACKGROUND, Color.WHITE);
        add(BUTTON_HOVER, new Color(128, 128, 128));
        add(SCROLL_BAR_FOREGROUND, Color.WHITE);
        add(SLIDER_FOREGROUND, Color.WHITE);
        add(CHECK_BOX_HOVER, new Color(128, 128, 128));
        add(SIDE_PANEL_SELECTED, new Color(93, 93, 93));
        add(TOOLTIP_BACKGROUND, Color.DARK_GRAY);
        add(TOOLTIP_FOREGROUND, Color.WHITE);
        add(COMBO_BOX_FOREGROUND, Color.DARK_GRAY);
        add(COMBO_BOX_BACKGROUND, Color.LIGHT_GRAY);
        add(COMBO_BOX_SELECTION_BACKGROUND, Color.DARK_GRAY.brighter());
        add(COMBO_BOX_SELECTION_FOREGROUND, Color.LIGHT_GRAY);
        add(SLIDER_TRACK, Color.LIGHT_GRAY);
        add(COMBO_BOX_BUTTON_FOREGROUND, Color.WHITE);
        add(COMBO_BOX_BUTTON_BACKGROUND, Color.DARK_GRAY);
        add(MENU_ITEM_ARMED, Color.GRAY);
        add(MENU_ITEM_PRESSED, Color.LIGHT_GRAY);
        add(QUEUE_ACTIVE_ICON, new Color(255, 138, 101));
    }

    @Override
    public String getAppIconPath(){
        return "/assets/app_icon_dark.png";
    }

    @Override
    public String getTrayIconPath(){
        return "/assets/tray_icon_dark.png";
    }

}
