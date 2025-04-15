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
package net.brlns.gdownloader.ffmpeg.enums;

import jakarta.annotation.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.settings.enums.VideoContainerEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum AudioCodecEnum implements ISettingsEnum {
    NO_CODEC("", "", List.of()),
    MP3("mp3", "libmp3lame", List.of(MP4, MKV, AVI, FLV, MOV)),
    AAC("aac", "aac", List.of(MP4, MKV, WEBM, AVI, FLV, MOV)),
    AC3("ac3", "ac3", List.of(MP4, MKV, AVI)),
    PCM("pcm", "pcm_s16le", List.of(MKV, AVI, MOV)),
    FLAC("flac", "flac", List.of(MKV, MP4, AVI)),
    ALAC("alac", "alac", List.of(MP4, MKV, MOV)),
    OPUS("opus", "libopus", List.of(MKV, WEBM, MP4)),
    VORBIS("vorbis", "libvorbis", List.of(MKV, WEBM));

    private final String codecName;
    private final String ffmpegCodecName;
    private final List<VideoContainerEnum> supportedContainers;

    public boolean isSupportedByContainer(VideoContainerEnum container) {
        return supportedContainers.contains(container);
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return isDefault() ? l10n("enums.audio_codec.no_codec") : name().toLowerCase();
    }

    public boolean isDefault() {
        return this == NO_CODEC;
    }

    @Nullable
    public static AudioCodecEnum getFallbackCodec(@NonNull VideoContainerEnum container) {
        return switch (container) {
            case WEBM ->
                OPUS;
            case AVI, FLV ->
                MP3;
            case MP4, MOV, MKV ->
                AAC;
            default ->
                null;
        };
    }
}
