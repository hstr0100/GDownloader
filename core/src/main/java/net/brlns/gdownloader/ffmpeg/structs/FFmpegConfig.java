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
package net.brlns.gdownloader.ffmpeg.structs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.brlns.gdownloader.ffmpeg.enums.*;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FFmpegConfig {

    @JsonIgnore
    public static final FFmpegConfig DEFAULT = getDefault();
    @JsonIgnore
    public static final FFmpegConfig COMPATIBLE_PRESET = getCompatiblePreset();

    @NonNull
    @Builder.Default
    @JsonProperty("VideoEncoder")
    private EncoderEnum videoEncoder = EncoderEnum.NO_ENCODER;

    @NonNull
    @Builder.Default
    @JsonProperty("VideoContainer")
    private VideoContainerEnum videoContainer = VideoContainerEnum.DEFAULT;

    @NonNull
    @Builder.Default
    @JsonProperty("SpeedPreset")
    private EncoderPreset speedPreset = EncoderPreset.NO_PRESET;

    @NonNull
    @Builder.Default
    @JsonProperty("Profile")
    private EncoderProfile profile = EncoderProfile.NO_PROFILE;

    @NonNull
    @Builder.Default
    @JsonProperty("RateControlMode")
    private RateControlModeEnum rateControlMode = RateControlModeEnum.CRF;

    @Builder.Default
    @JsonProperty("RateControlValue")
    private int rateControlValue = 23;

    @Builder.Default
    @JsonProperty("VideoBitrate")
    private int videoBitrate = 5000;

    @NonNull
    @Builder.Default
    @JsonProperty("AudioCodec")
    private AudioCodecEnum audioCodec = AudioCodecEnum.NO_CODEC;

    @NonNull
    @Builder.Default
    @JsonProperty("AudioBitrate")
    private AudioBitrateEnum audioBitrate = AudioBitrateEnum.BITRATE_256;

    @JsonIgnore
    public String getFileSuffix() {
        StringBuilder builder = new StringBuilder();
        boolean hasVideoCodec = videoEncoder.getVideoCodec() != VideoCodecEnum.NO_CODEC;
        boolean hasAudioCodec = audioCodec != AudioCodecEnum.NO_CODEC;

        if (hasVideoCodec) {
            builder.append(videoEncoder.getVideoCodec().getCodecName());
        }

        if (hasAudioCodec) {
            if (!hasVideoCodec) {
                builder.append(audioCodec.getCodecName());
            } else {
                builder.append("_").append(audioCodec.getCodecName());
            }
        }

        if (builder.isEmpty()) {
            return "remux";
        }

        return builder.toString();
    }

    @JsonIgnore
    public static boolean isDefault(@NonNull FFmpegConfig config) {
        return config.equals(DEFAULT);
    }

    public static boolean isCompatiblePreset(@NonNull FFmpegConfig config) {
        return config.equals(COMPATIBLE_PRESET);
    }

    @JsonIgnore
    public static FFmpegConfig getDefault() {
        return FFmpegConfig.builder().build();
    }

    @JsonIgnore
    public static FFmpegConfig getCompatiblePreset() {
        return FFmpegConfig.builder()
            .videoEncoder(EncoderEnum.H264_AUTO)
            .videoContainer(VideoContainerEnum.MP4)
            .audioCodec(AudioCodecEnum.AAC)
            .audioBitrate(AudioBitrateEnum.BITRATE_256)
            .build();
    }
}
