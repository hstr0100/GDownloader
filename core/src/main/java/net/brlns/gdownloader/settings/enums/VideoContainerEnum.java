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

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public enum VideoContainerEnum implements ISettingsEnum, IContainerEnum {
    DEFAULT,
    MP4,
    MKV,
    WEBM,
    AVI,
    FLV,
    MOV,
    GIF;

    @Override
    public String getValue() {
        if (isDefault()) {
            return MP4.getValue();
        }

        return name().toLowerCase();
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        if (isDefault()) {
            return l10n("enums.containers.video.default");
        }

        return name().toLowerCase();
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    public static VideoContainerEnum[] CONTAINERS;

    public static VideoContainerEnum[] allExceptDefault() {
        if (CONTAINERS != null) {
            return CONTAINERS;
        }

        return CONTAINERS = Arrays.stream(values())
            .filter(c -> !c.isDefault())
            .toArray(VideoContainerEnum[]::new);
    }
}
