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

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ffmpeg.enums.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: jackson mappings
@Data
@Builder
@Slf4j
public class FFmpegConfig {

    @Builder.Default
    private EncoderEnum videoEncoder = EncoderEnum.AUTO;
    @Builder.Default
    private AudioCodecEnum audioCodec = AudioCodecEnum.AAC;
    @Builder.Default
    private EncoderPreset speedPreset = EncoderPreset.NO_PRESET;
    @Builder.Default
    private EncoderProfile profile = EncoderProfile.NO_PROFILE;
    @Builder.Default
    private RateControlModeEnum rateControlMode = RateControlModeEnum.CQP;
    @Builder.Default
    private int rateControlValue = 23;
    @Builder.Default
    private int videoBitrate = 5000;
    @Builder.Default
    private AudioBitrateEnum audioBitrate = AudioBitrateEnum.BITRATE_128;

}
