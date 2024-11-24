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

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum AudioCodecEnum implements ISettingsEnum, IContainerEnum {
    NO_CODEC(""),
    MP3("libmp3lame"),
    AAC("aac"),
    FLAC("flac"),
    ALAC("alac"),
    OPUS("libopus"),
    VORBIS("libvorbis");

    private final String ffmpegCodecName;

    private AudioCodecEnum(String ffmpegCodecNameIn) {
        ffmpegCodecName = ffmpegCodecNameIn;
    }

    @Override
    public String getValue() {
        return name().toLowerCase();
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return this == NO_CODEC ? l10n("enums.audio_codec.no_codec") : name().toLowerCase();
    }
}
