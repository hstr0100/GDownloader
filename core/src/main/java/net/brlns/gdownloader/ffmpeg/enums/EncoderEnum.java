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
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import static net.brlns.gdownloader.ffmpeg.enums.EncoderTypeEnum.*;
import static net.brlns.gdownloader.ffmpeg.enums.VideoCodecEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum EncoderEnum {
    NO_ENCODER("", NO_CODEC, null),
    // Software encoding presets
    H264_SOFTWARE("libx264", H264, SOFTWARE),
    H265_SOFTWARE("libx265", H265, SOFTWARE),
    VP9_SOFTWARE("libvpx-vp9", VP9, SOFTWARE),
    // AV1 Software encoding is NOT recommended unless you have a reasonably modern CPU.
    AV1_SOFTWARE("libaom-av1", AV1, SOFTWARE),
    // NVIDIA NVENC presets - Absolutely thoroughly untested
    H264_NVENC("h264_nvenc", H264, NVENC),
    H265_NVENC("hevc_nvenc", H265, NVENC),
    AV1_NVENC("av1_nvenc", AV1, NVENC),
    // Intel QuickSync presets
    H264_QSV("h264_qsv", H264, QSV),
    H265_QSV("hevc_qsv", H265, QSV),
    AV1_QSV("av1_qsv", AV1, QSV),
    VP9_QSV("vp9_qsv", VP9, QSV),
    // AMD AMF presets
    H264_AMF("h264_amf", H264, AMF),
    H265_AMF("hevc_amf", H265, AMF),
    AV1_AMF("av1_amf", AV1, AMF),
    // VAAPI presets
    H264_VAAPI("h264_vaapi", H264, VAAPI),
    H265_VAAPI("hevc_vaapi", H265, VAAPI),
    VP9_VAAPI("vp9_vaapi", VP9, VAAPI),
    AV1_VAAPI("av1_vaapi", AV1, VAAPI),
    // V4L2M2M presets
    H264_V4L2M2M("h264_v4l2m2m", H264, V4L2M2M),
    H265_V4L2M2M("hevc_v4l2m2m", H265, V4L2M2M),
    // Auto detection mode per codec
    H264_AUTO("", H264, EncoderTypeEnum.AUTO),
    H265_AUTO("", H265, EncoderTypeEnum.AUTO),
    VP9_AUTO("", VP9, EncoderTypeEnum.AUTO),
    AV1_AUTO("", AV1, EncoderTypeEnum.AUTO);

    private final String ffmpegCodecName;
    private final VideoCodecEnum videoCodec;
    private final EncoderTypeEnum encoderType;

    public static Optional<EncoderEnum> findByName(@NonNull String name) {
        return Arrays.stream(values())
            .filter(
                e -> e.getFfmpegCodecName().equalsIgnoreCase(name)
                && e.getEncoderType() != EncoderTypeEnum.AUTO
            ).findFirst();
    }
}
