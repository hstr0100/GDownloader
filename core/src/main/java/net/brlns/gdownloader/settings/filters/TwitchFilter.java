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
package net.brlns.gdownloader.settings.filters;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.File;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.enums.QualitySelectorEnum;
import net.brlns.gdownloader.settings.enums.ResolutionEnum;

import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwitchFilter extends GenericFilter{

    public static final String ID = "twitch";

    @SuppressWarnings("this-escape")
    public TwitchFilter(){
        setId(ID);
        setFilterName("Twitch");
        setUrlRegex("^(https?:\\/\\/)?(www\\.)?twitch\\.tv(\\/.*)?$");
        setVideoNamePattern("%(title).60s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s");
        setAudioNamePattern(getVideoNamePattern().replace("%(resolution)s", "%(audio_bitrate)s"));
        setEmbedThumbnailAndMetadata(false);
        setQualitySettings(QualitySettings.builder()
            .selector(QualitySelectorEnum.WORST)
            .minHeight(ResolutionEnum.RES_480)
            .maxHeight(ResolutionEnum.RES_720)
            .build());
    }

    @JsonIgnore
    @Override
    protected List<String> buildArguments(DownloadTypeEnum typeEnum, GDownloader main, File savePath){
        List<String> arguments = super.buildArguments(typeEnum, main, savePath);

        switch(typeEnum){
            case ALL -> {
                arguments.addAll(List.of(
                    "--verbose",
                    "--continue",
                    "--hls-prefer-native"
                ));
            }
            case VIDEO -> {
                if(isEmbedThumbnailAndMetadata()){
                    arguments.addAll(List.of(
                        "--parse-metadata",
                        ":%(?P<is_live>)"
                    ));
                }
            }
        }

        return arguments;
    }
}
