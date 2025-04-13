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
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.ffmpeg.enums.EncoderPresetEnum;
import net.brlns.gdownloader.lang.ITranslatable;

import static net.brlns.gdownloader.ffmpeg.structs.EncoderPreset.NO_PRESET;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncoderPreset implements ITranslatable {

    public static final EncoderPreset NO_PRESET = new EncoderPreset();

    @JsonProperty("SpeedPresetEnum")
    private EncoderPresetEnum presetEnum = EncoderPresetEnum.NO_PRESET;

    @JsonProperty("SpeedPresetCommand")
    private String ffmpegPresetCommand = "";

    @JsonProperty("FFmpegSpeedPresetName")
    private String ffmpegPresetName = "";

    public EncoderPreset(EncoderPresetEnum presetEnumIn) {
        presetEnum = presetEnumIn;
        ffmpegPresetCommand = "-preset";
        ffmpegPresetName = presetEnumIn.getPresetName();
    }

    public boolean isDefault() {
        return this.equals(NO_PRESET);
    }

    @JsonIgnore
    @Override
    public String getDisplayName() {
        return this.equals(NO_PRESET)
            ? l10n("enums.transcode.speed_preset.no_preset")
            : ffmpegPresetName;
    }
}
