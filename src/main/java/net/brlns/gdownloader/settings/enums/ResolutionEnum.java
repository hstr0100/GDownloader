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

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum ResolutionEnum implements ISettingsEnum{
    RES_144(144, "144p"),
    RES_240(240, "240p"),
    RES_360(360, "360p"),
    RES_480(480, "480p"),
    RES_720(720, "720p"),
    RES_1080(1080, "1080p"),
    RES_2160(2160, "4K");

    private final int value;
    private final String displayName;

    private ResolutionEnum(int valueIn, String displayNameIn){
        value = valueIn;
        displayName = displayNameIn;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    public boolean isResolutionValid(ResolutionEnum minResolution, ResolutionEnum maxResolution){
        return minResolution.getValue() <= maxResolution.getValue();
    }

    public ResolutionEnum getValidMin(ResolutionEnum min){
        if(isResolutionValid(min, this)){
            return min;
        }

        Optional<ResolutionEnum> result = Stream.of(values())
            .filter(e -> isResolutionValid(e, this))
            .max(Comparator.comparingInt(ResolutionEnum::getValue));

        return result.orElse(min);
    }

    public ResolutionEnum getValidMax(ResolutionEnum max){
        if(isResolutionValid(this, max)){
            return max;
        }

        Optional<ResolutionEnum> result = Stream.of(values())
            .filter(e -> isResolutionValid(this, e))
            .min(Comparator.comparingInt(ResolutionEnum::getValue));

        return result.orElse(max);
    }
}
