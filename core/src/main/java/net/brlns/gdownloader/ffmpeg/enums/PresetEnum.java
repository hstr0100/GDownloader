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

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.brlns.gdownloader.ffmpeg.structs.EncoderPreset;
import net.brlns.gdownloader.ffmpeg.structs.EncoderProfile;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

import static net.brlns.gdownloader.ffmpeg.enums.EncoderEnum.*;
import static net.brlns.gdownloader.ffmpeg.enums.RateControlModeEnum.*;
import static net.brlns.gdownloader.ffmpeg.structs.EncoderPreset.NO_PRESET;
import static net.brlns.gdownloader.ffmpeg.structs.EncoderProfile.NO_PROFILE;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum PresetEnum implements ISettingsEnum {
    // TODO: more presets
    // User defined
    CUSTOM("enums.transcode.preset.custom", NO_ENCODER, NO_PRESET, NO_PROFILE, CRF, 0, 0),
    // H264 Presets
    H264_QUALITY("enums.transcode.preset.quality", H264_AUTO, NO_PRESET, NO_PROFILE, CRF, 18, 0),
    H264_MEDIUM("enums.transcode.preset.medium", H264_AUTO, NO_PRESET, NO_PROFILE, CRF, 23, 0),
    H264_FAST("enums.transcode.preset.fast", H264_AUTO, NO_PRESET, NO_PROFILE, CRF, 28, 0),
    H264_HIGH_BITRATE("enums.transcode.preset.high_bitrate", H264_AUTO, NO_PRESET, NO_PROFILE, VBR, 0, 10000),
    // H265 Presets
    H265_QUALITY("enums.transcode.preset.quality", H265_AUTO, NO_PRESET, NO_PROFILE, CRF, 20, 0),
    H265_MEDIUM("enums.transcode.preset.medium", H265_AUTO, NO_PRESET, NO_PROFILE, CRF, 26, 0),
    H265_FAST("enums.transcode.preset.fast", H265_AUTO, NO_PRESET, NO_PROFILE, CRF, 32, 0),
    H265_HIGH_BITRATE("enums.transcode.preset.high_bitrate", H265_AUTO, NO_PRESET, NO_PROFILE, VBR, 0, 8000),
    // VP9 Presets
    VP9_QUALITY("enums.transcode.preset.quality", VP9_AUTO, NO_PRESET, NO_PROFILE, CRF, 20, 0),
    VP9_MEDIUM("enums.transcode.preset.medium", VP9_AUTO, NO_PRESET, NO_PROFILE, CRF, 27, 0),
    VP9_FAST("enums.transcode.preset.fast", VP9_AUTO, NO_PRESET, NO_PROFILE, CRF, 35, 0),
    // AV1 Presets
    AV1_QUALITY("enums.transcode.preset.quality", AV1_AUTO, NO_PRESET, NO_PROFILE, CRF, 18, 0),
    AV1_MEDIUM("enums.transcode.preset.medium", AV1_AUTO, NO_PRESET, NO_PROFILE, CRF, 23, 0),
    AV1_FAST("enums.transcode.preset.fast", AV1_AUTO, NO_PRESET, NO_PROFILE, CRF, 30, 0);

    private final String presetTranslationKey;
    private final EncoderEnum encoder;
    private final EncoderPreset speedPreset;
    private final EncoderProfile profile;
    private final RateControlModeEnum rateControlMode;
    private final int rateControlValue;
    private final int bitrate;

    @Override
    public String getDisplayName() {
        if (isDefault()) {
            return l10n(presetTranslationKey);
        }

        return encoder.getVideoCodec().getDisplayName() + " - " + l10n(presetTranslationKey);
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    public boolean isDefault() {
        return this == CUSTOM;
    }

    public void applyToConfig(FFmpegConfig config) {
        config.setVideoEncoder(encoder);
        config.setSpeedPreset(speedPreset);
        config.setProfile(profile);
        config.setRateControlMode(rateControlMode);
        config.setRateControlValue(rateControlValue);
        config.setVideoBitrate(bitrate);
    }

    public boolean isPresetMatch(FFmpegConfig config) {
        if (encoder != config.getVideoEncoder()
            || speedPreset != config.getSpeedPreset()
            || profile != config.getProfile()
            || rateControlMode != config.getRateControlMode()) {
            return false;
        }

        if (rateControlMode == RateControlModeEnum.CRF
            || rateControlMode == RateControlModeEnum.CQP) {
            if (rateControlValue != config.getRateControlValue()) {
                return false;
            }
        } else if (rateControlMode == RateControlModeEnum.VBR
            || rateControlMode == RateControlModeEnum.CBR) {
            if (bitrate != config.getVideoBitrate()) {
                return false;
            }
        }

        return true;
    }
}
