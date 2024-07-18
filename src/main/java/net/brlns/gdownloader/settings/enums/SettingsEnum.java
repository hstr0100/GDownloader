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
package net.brlns.gdownloader.settings.enums;

import java.util.Arrays;
import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public interface SettingsEnum{

    String getTranslationKey();

    default String getDisplayName(){
        return get(getTranslationKey());
    }

    public static <T extends Enum<T> & SettingsEnum> T getEnumByIndex(Class<T> enumClass, int index){
        T[] values = enumClass.getEnumConstants();

        if(index < 0 || index >= values.length){
            throw new IllegalArgumentException("Index out of bounds");
        }

        return values[index];
    }

    public static <T extends Enum<T> & SettingsEnum> int getEnumIndex(Class<T> enumClass, T valueToFind){
        T[] values = enumClass.getEnumConstants();

        for(int i = 0; i < values.length; i++){
            if(values[i] == valueToFind){
                return i;
            }
        }

        throw new IllegalArgumentException("Enum constant not found");
    }

    public static <T extends Enum<T> & SettingsEnum> String[] getDisplayNames(Class<T> enumClass){
        T[] values = enumClass.getEnumConstants();

        return Arrays.stream(values)
            .map(T::getDisplayName)
            .toArray(String[]::new);
    }
}
