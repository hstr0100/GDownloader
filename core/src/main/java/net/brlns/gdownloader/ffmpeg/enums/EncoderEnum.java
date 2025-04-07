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

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum EncoderEnum {
    NO_ENCODER("", VideoCodecEnum.NO_CODEC, null),
    // Software encoding presets
    H264_SOFTWARE("libx264", VideoCodecEnum.H264, EncoderTypeEnum.SOFTWARE),
    H265_SOFTWARE("libx265", VideoCodecEnum.H265, EncoderTypeEnum.SOFTWARE),
    VP9_SOFTWARE("libvpx-vp9", VideoCodecEnum.VP9, EncoderTypeEnum.SOFTWARE),
    // AV1 Software encoding is NOT recommended unless you have a reasonably modern CPU.
    AV1_SOFTWARE("libaom-av1", VideoCodecEnum.AV1, EncoderTypeEnum.SOFTWARE),
    // NVIDIA NVENC presets - Absolutely thoroughly untested
    H264_NVENC("h264_nvenc", VideoCodecEnum.H264, EncoderTypeEnum.NVENC),
    H265_NVENC("hevc_nvenc", VideoCodecEnum.H265, EncoderTypeEnum.NVENC),
    AV1_NVENC("av1_nvenc", VideoCodecEnum.AV1, EncoderTypeEnum.NVENC),
    // Intel QuickSync presets
    H264_QSV("h264_qsv", VideoCodecEnum.H264, EncoderTypeEnum.QSV),
    H265_QSV("hevc_qsv", VideoCodecEnum.H265, EncoderTypeEnum.QSV),
    AV1_QSV("av1_qsv", VideoCodecEnum.AV1, EncoderTypeEnum.QSV),
    VP9_QSV("vp9_qsv", VideoCodecEnum.VP9, EncoderTypeEnum.QSV),
    // AMD AMF presets
    H264_AMF("h264_amf", VideoCodecEnum.H264, EncoderTypeEnum.AMF),
    H265_AMF("hevc_amf", VideoCodecEnum.H265, EncoderTypeEnum.AMF),
    AV1_AMF("av1_amf", VideoCodecEnum.AV1, EncoderTypeEnum.AMF),
    // VAAPI presets
    H264_VAAPI("h264_vaapi", VideoCodecEnum.H264, EncoderTypeEnum.VAAPI),
    H265_VAAPI("hevc_vaapi", VideoCodecEnum.H265, EncoderTypeEnum.VAAPI),
    VP9_VAAPI("vp9_vaapi", VideoCodecEnum.VP9, EncoderTypeEnum.VAAPI),
    AV1_VAAPI("av1_vaapi", VideoCodecEnum.AV1, EncoderTypeEnum.VAAPI),
    // V4L2M2M presets
    H264_V4L2M2M("h264_v4l2m2m", VideoCodecEnum.H264, EncoderTypeEnum.V4L2M2M),
    H265_V4L2M2M("hevc_v4l2m2m", VideoCodecEnum.H265, EncoderTypeEnum.V4L2M2M),
    // Auto detection mode per codec
    H264_AUTO("libx264", VideoCodecEnum.H264, EncoderTypeEnum.AUTO),
    H265_AUTO("libx265", VideoCodecEnum.H265, EncoderTypeEnum.AUTO),
    VP9_AUTO("libvpx-vp9", VideoCodecEnum.VP9, EncoderTypeEnum.AUTO),
    AV1_AUTO("libaom-av1", VideoCodecEnum.AV1, EncoderTypeEnum.AUTO),
    // Generic auto detection mode
    AUTO("libx264", VideoCodecEnum.H264, EncoderTypeEnum.AUTO);

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
