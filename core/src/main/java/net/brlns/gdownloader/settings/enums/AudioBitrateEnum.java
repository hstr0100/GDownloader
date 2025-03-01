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

import static net.brlns.gdownloader.lang.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum AudioBitrateEnum implements ISettingsEnum {
    NO_AUDIO(0, ""),
    BITRATE_32(32, "32kbps"),
    BITRATE_64(64, "64kbps"),
    BITRATE_96(96, "96kbps"),
    BITRATE_128(128, "128kbps"),
    BITRATE_160(160, "160kbps"),
    BITRATE_192(192, "192kbps"),
    BITRATE_256(256, "256kbps"),
    BITRATE_320(320, "320kbps"),
    BITRATE_384(384, "384kbps"),
    BITRATE_448(448, "448kbps"),
    BITRATE_512(512, "512kbps"),
    BITRATE_640(640, "640kbps"),
    BITRATE_768(768, "768kbps"),
    BITRATE_896(896, "896kbps"),
    BITRATE_1024(1024, "1024kbps");

    private final int value;
    private final String displayName;

    private AudioBitrateEnum(int valueIn, String displayNameIn) {
        value = valueIn;
        displayName = displayNameIn;
    }

    @Override
    public String getDisplayName() {
        return this == NO_AUDIO ? l10n("enums.audio.no_audio") : displayName;
    }

    @Override
    public String getTranslationKey() {
        return "";
    }
}
