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

/**
 * @author Gabriel / hstr0100 / vertx010
 */
//TODO fonts
public abstract class AbstractTheme{

    private final Map<UIColors, Color> colors = new HashMap<>();

    @SuppressWarnings("this-escape")
    public AbstractTheme(){
        init();
    }

    private void init(){
        initColors();

        for(UIColors color : UIColors.values()){
            if(!colors.containsKey(color)){
                throw new RuntimeException("Color key " + color + " not defined");
            }
        }
    }

    protected void add(UIColors key, Color color){
        assert !colors.containsKey(key);

        colors.put(key, color);
    }

    public Color get(UIColors color){
        return colors.get(color);
    }

    protected abstract void initColors();

}
