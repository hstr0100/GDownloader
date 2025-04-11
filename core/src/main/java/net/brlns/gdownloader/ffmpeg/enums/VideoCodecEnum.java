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

import jakarta.annotation.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

import static net.brlns.gdownloader.settings.enums.VideoContainerEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum VideoCodecEnum {
    NO_CODEC("", "", List.of()),
    H264("h264", "H264", List.of(MP4, MKV, WEBM, AVI, FLV, MOV)),
    H265("h265", "HEVC", List.of(MP4, MKV, MOV)),
    VP9("vp9", "VP9", List.of(MP4, MKV, WEBM)),// while VP9 is technically supported by MP4, client compatibility is not great.
    AV1("av1", "AV1", List.of(MP4, MKV, WEBM, MOV));

    private final String codecName;
    private final String vaapiName;
    private final List<VideoContainerEnum> supportedContainers;

    public boolean isSupportedByContainer(VideoContainerEnum container) {
        return supportedContainers.contains(container);
    }

    @Nullable
    public static VideoCodecEnum getFallbackCodec(@NonNull VideoContainerEnum container) {
        return switch (container) {
            case WEBM ->
                VP9;
            case AVI, FLV, MP4, MOV, MKV ->
                H264;
            default ->
                null;
        };
    }
}
