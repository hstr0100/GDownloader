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

import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum QualitySelectorEnum implements ISettingsEnum {
    BEST_VIDEO("bestvideo", "enums.quality_selector.bestvideo"),
    BEST("best*", "enums.quality_selector.best"),
    WORST_VIDEO("worstvideo", "enums.quality_selector.worstvideo"),
    WORST("worst*", "enums.quality_selector.worst");

    private final String value;
    private final String translationKey;

    private QualitySelectorEnum(String valueIn, String translationKeyIn) {
        value = valueIn;
        translationKey = translationKeyIn;
    }
}
