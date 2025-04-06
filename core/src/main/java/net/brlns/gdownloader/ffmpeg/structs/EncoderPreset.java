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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.ffmpeg.enums.EncoderPresetEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: jackson mappings
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncoderPreset {

    public static final EncoderPreset NO_PRESET = new EncoderPreset();

    private EncoderPresetEnum presetEnum = EncoderPresetEnum.NO_PRESET;
    private String ffmpegPresetCommand = "";
    private String ffmpegPresetName = "";

}
