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
package net.brlns.gdownloader.ui.menu;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class RightClickMenuEntries extends LinkedHashMap<String, IMenuEntry> {

    public static RightClickMenuEntries fromMap(Map<String, IMenuEntry> mapIn) {
        RightClickMenuEntries menuEntries = new RightClickMenuEntries();
        menuEntries.putAll(mapIn);

        return menuEntries;
    }
}
