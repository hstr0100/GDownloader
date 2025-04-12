/*
 * Copyright (C) 2025 hstr0100
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.ffmpeg.structs.EncoderPreset;

import static net.brlns.gdownloader.ffmpeg.enums.VideoCodecEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum EncoderPresetEnum {
    // x264/x265 software presets
    ULTRAFAST("ultrafast", List.of(H264, H265)),
    SUPERFAST("superfast", List.of(H264, H265)),
    VERYFAST("veryfast", List.of(H264, H265)),
    FASTER("faster", List.of(H264, H265)),
    FAST("fast", List.of(H264, H265)),
    MEDIUM("medium", List.of(H264, H265)),
    SLOW("slow", List.of(H264, H265)),
    SLOWER("slower", List.of(H264, H265)),
    VERYSLOW("veryslow", List.of(H264, H265)),
    // SW AV1/VP9 and all HW encoders use the system mapper
    SYSTEM_MAPPER("", List.of()),
    NO_PRESET("", List.of());

    private final String presetName;
    private final List<VideoCodecEnum> videoCodecs;

    private EncoderPresetEnum(String presetNameIn, List<VideoCodecEnum> videoCodecsIn) {
        presetName = presetNameIn;
        videoCodecs = videoCodecsIn;
    }

    public EncoderPreset toPreset() {
        return new EncoderPreset(this);
    }

    public static Optional<EncoderPresetEnum> findByNameAndCodec(
        String name, @NonNull VideoCodecEnum codec) {
        return Arrays.stream(values())
            .filter(p -> p.getPresetName().equalsIgnoreCase(name)
            && p.getVideoCodecs().stream().anyMatch(c -> c == codec))
            .findFirst();
    }

    public static List<EncoderPreset> getPresetsForCodec(@NonNull VideoCodecEnum codec) {
        return Arrays.stream(values())
            .filter(preset -> preset.getVideoCodecs().contains(codec))
            .map(preset -> preset.toPreset())
            .collect(Collectors.toList());
    }
}
