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
public class LightTheme extends AbstractTheme{

    public LightTheme(){
        super();
    }

    @Override
    protected void initColors(){
        add(ICON, new Color(128, 128, 128));
        add(ICON_ACTIVE, new Color(56, 192, 251));
        add(ICON_HOVER, new Color(80, 80, 80));
        add(ICON_CLOSE, new Color(224, 64, 6));
        add(BACKGROUND, Color.WHITE);
        add(TEXT_AREA_BACKGROUND, new Color(242, 242, 242));
        add(TEXT_AREA_FOREGROUND, Color.DARK_GRAY);
        add(FOREGROUND, Color.DARK_GRAY);
        add(SIDE_PANEL, new Color(242, 242, 242));
        add(MEDIA_CARD_HOVER, new Color(218, 218, 218));
        add(MEDIA_CARD, new Color(242, 242, 242));
        add(MEDIA_CARD_THUMBNAIL, new Color(180, 180, 180));
        add(LIGHT_TEXT, new Color(102, 102, 102));
        add(BUTTON_FOREGROUND, Color.DARK_GRAY);
        add(BUTTON_BACKGROUND, Color.WHITE);
        add(BUTTON_HOVER, new Color(128, 128, 128));
        add(SLIDER_FOREGROUND, new Color(100, 100, 100));
        add(SIDE_PANEL_SELECTED, new Color(218, 218, 218));
        add(TOOLTIP_BACKGROUND, new Color(230, 230, 230));
        add(TOOLTIP_FOREGROUND, Color.DARK_GRAY);
        add(COMBO_BOX_BACKGROUND, new Color(242, 242, 242));
        add(COMBO_BOX_FOREGROUND, Color.DARK_GRAY);
        add(COMBO_BOX_SELECTION_BACKGROUND, Color.LIGHT_GRAY);
        add(COMBO_BOX_SELECTION_FOREGROUND, Color.WHITE);
        add(SLIDER_TRACK, Color.LIGHT_GRAY);
        add(COMBO_BOX_BUTTON_FOREGROUND, Color.LIGHT_GRAY);
        add(COMBO_BOX_BUTTON_BACKGROUND, Color.WHITE);
    }

}
