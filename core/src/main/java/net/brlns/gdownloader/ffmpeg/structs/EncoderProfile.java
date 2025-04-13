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
import net.brlns.gdownloader.ffmpeg.enums.EncoderProfileEnum;
import net.brlns.gdownloader.lang.ITranslatable;

import static net.brlns.gdownloader.ffmpeg.structs.EncoderProfile.NO_PROFILE;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncoderProfile implements ITranslatable {

    public static final EncoderProfile NO_PROFILE = new EncoderProfile();

    @JsonProperty("ProfileEnum")
    private EncoderProfileEnum profileEnum = EncoderProfileEnum.NO_PROFILE;
    @JsonProperty("FFmpegProfileName")
    private String ffmpegProfileName = "";

    public EncoderProfile(EncoderProfileEnum profileEnumIn) {
        profileEnum = profileEnumIn;
        ffmpegProfileName = profileEnum.getProfileName();
    }

    public boolean isDefault() {
        return this.equals(NO_PROFILE);
    }

    @JsonIgnore
    @Override
    public String getDisplayName() {
        return this.equals(NO_PROFILE)
            ? l10n("enums.transcode.profile.no_profile")
            : ffmpegProfileName;
    }
}
