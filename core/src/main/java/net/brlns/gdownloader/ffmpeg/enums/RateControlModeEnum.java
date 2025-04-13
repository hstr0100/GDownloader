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
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

import static net.brlns.gdownloader.ffmpeg.enums.RateControlModeEnum.*;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum RateControlModeEnum implements ISettingsEnum {
    CBR("cbr", "enums.transcode.rc.cbr"), // Constant Bitrate
    VBR("vbr", "enums.transcode.rc.vbr"), // Variable Bitrate
    CRF("crf", "enums.transcode.rc.crf"), // Constant Rate Factor
    CQP("cqp", "enums.transcode.rc.cqp"), // Constant Quantization Parameter
    DEFAULT("", "enums.transcode.preset.custom");

    private final String mode;
    private final String translationKey;

    @Override
    public String getDisplayName() {
        if (isDefault()) {
            return l10n(translationKey, CRF.name());
        }

        return name() + " - " + l10n(translationKey);
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }
}
