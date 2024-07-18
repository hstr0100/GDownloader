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
