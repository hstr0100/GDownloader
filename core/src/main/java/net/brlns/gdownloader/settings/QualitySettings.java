/*
 * Copyright (C) 2024 hstr0100
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
package net.brlns.gdownloader.settings;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.settings.enums.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QualitySettings {

    @Builder.Default
    @JsonProperty("QualitySelector")
    private QualitySelectorEnum selector = QualitySelectorEnum.BEST_VIDEO;

    @Builder.Default
    @JsonAlias({"Container"})
    @JsonProperty("VideoContainer")
    private VideoContainerEnum videoContainer = VideoContainerEnum.MP4;

    @Builder.Default
    @JsonProperty("MinHeight")
    private ResolutionEnum minHeight = ResolutionEnum.RES_144;

    @Builder.Default
    @JsonProperty("MaxHeight")
    private ResolutionEnum maxHeight = ResolutionEnum.RES_1080;

    @Builder.Default
    @JsonProperty("FPS")
    private FPSEnum fps = FPSEnum.FPS_60;

    @Builder.Default
    @JsonProperty("AudioCodec")
    private AudioCodecEnum audioCodec = AudioCodecEnum.NO_CODEC;

    @Builder.Default
    @JsonProperty("AudioContainer")
    private AudioContainerEnum audioContainer = AudioContainerEnum.MP3;

    @Builder.Default
    @JsonProperty("AudioBitrate")
    private AudioBitrateEnum audioBitrate = AudioBitrateEnum.BITRATE_320;

    @Builder.Default
    @JsonProperty("SubtitleContainer")
    private SubtitleContainerEnum subtitleContainer = SubtitleContainerEnum.SRT;

    @Builder.Default
    @JsonProperty("ThumbnailContainer")
    private ThumbnailContainerEnum thumbnailContainer = ThumbnailContainerEnum.PNG;

    // TODO this needs some work
    @JsonIgnore
    public String buildQualitySelector() {
        return "(" + selector.getValue() + "[height>=" + minHeight.getValue() + "][height<=" + maxHeight.getValue() + "][ext=" + videoContainer.getValue() + "][fps=" + fps.getValue() + "]+bestaudio/"
            + selector.getValue() + "[height>=" + minHeight.getValue() + "][height<=" + maxHeight.getValue() + "][ext=" + videoContainer.getValue() + "]+bestaudio/"
            + selector.getValue() + "[ext=" + videoContainer.getValue() + "]+bestaudio/"
            + selector.getValue() + "+bestaudio/best)";
    }

    // TODO
    @JsonIgnore
    public String getTranscodingOptions() {
        return "res:" + maxHeight.getValue() + ",fps,ext,codec:vp9.2";
    }
}
