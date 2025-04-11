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
import lombok.Builder;
import lombok.Data;
import net.brlns.gdownloader.ffmpeg.enums.*;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: jackson mappings
@Data
@Builder
public class FFmpegConfig {

    @Builder.Default
    private EncoderEnum videoEncoder = EncoderEnum.NO_ENCODER;
    @Builder.Default
    private VideoContainerEnum videoContainer = VideoContainerEnum.MP4;
    @Builder.Default
    private AudioCodecEnum audioCodec = AudioCodecEnum.NO_CODEC;
    @Builder.Default
    private EncoderPreset speedPreset = EncoderPreset.NO_PRESET;
    @Builder.Default
    private EncoderProfile profile = EncoderProfile.NO_PROFILE;
    @Builder.Default
    private RateControlModeEnum rateControlMode = RateControlModeEnum.CRF;
    @Builder.Default
    private int rateControlValue = 23;
    @Builder.Default
    private int videoBitrate = 5000;
    @Builder.Default
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
}
