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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.ffmpeg.structs.EncoderProfile;

import static net.brlns.gdownloader.ffmpeg.enums.VideoCodecEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum EncoderProfileEnum {
    // x264 software profiles
    H264_BASELINE("baseline", H264),
    H264_MAIN("main", H264),
    H264_HIGH("high", H264),
    H264_HIGH444P("high444p", H264),
    // x265 software profiles
    H265_MAIN("main", H265),
    H265_MAIN10("main10", H265),
    H265_MAIN_422_10("main422-10", H265),
    // SW AV1/VP9 and all HW encoders use the system mapper
    SYSTEM_MAPPER("", null),
    NO_PROFILE("", null);

    private final String profileName;
    private final VideoCodecEnum videoCodec;

    public static Optional<EncoderProfileEnum> findByNameAndCodec(String name, @NonNull VideoCodecEnum codec) {
        return Arrays.stream(values())
            .filter(p -> p.getProfileName().equalsIgnoreCase(name)
            && p.getVideoCodec() == codec)
            .findFirst();
    }

    public static List<EncoderProfile> getProfilesForCodec(@NonNull VideoCodecEnum codec) {
        return Arrays.stream(values())
            .filter(profile -> profile.getVideoCodec() == codec)
            .map(profile -> new EncoderProfile(profile, profile.getProfileName()))
            .collect(Collectors.toList());
    }

}
