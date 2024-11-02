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
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.AudioBitrateEnum;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenericFilter extends AbstractUrlFilter{

    public static final String ID = "default";

    @SuppressWarnings("this-escape")
    public GenericFilter(){
        setId(ID);
        setVideoNamePattern("%(title).60s (%(resolution)s).%(ext)s");
        setAudioNamePattern(getVideoNamePattern().replace("%(resolution)s", "%(audio_bitrate)s"));
        setEmbedThumbnailAndMetadata(false);
    }

    @JsonIgnore
    @Override
    public String getDisplayName(){
        String name = getFilterName();
        if(name.isEmpty()){
            name = l10n("enums.web_filter.default");
        }

        return name;
    }

    @JsonIgnore
    @Override
    protected List<String> buildArguments(DownloadTypeEnum typeEnum, GDownloader main, File savePath){
        Settings config = main.getConfig();
        QualitySettings quality = getQualitySettings();
        AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

        List<String> arguments = new ArrayList<>();

        switch(typeEnum){
            case ALL -> {
                //For backwards compatibility. This should have been a list.
                for(String arg : config.getExtraYtDlpArguments().split(" ")){
                    if(!arg.isEmpty()){
                        arguments.add(arg);
                    }
                }

                if(config.isRandomIntervalBetweenDownloads()){
                    arguments.addAll(List.of(
                        "--max-sleep-interval",
                        "45",
                        "--min-sleep-interval",
                        "25"
                    ));
                }

                if(config.isReadCookiesFromBrowser()){
                    arguments.addAll(List.of(
                        "--cookies-from-browser",
                        main.getBrowserForCookies().getName()
                    ));
                }

                if(GDownloader.isWindows()){
                    //NTFS shenanigans ahead
                    //TODO: query registry for longpath support status
                    arguments.addAll(List.of(
                        "--windows-filenames",
                        "--trim-filenames",
                        String.valueOf(240)//Give some extra room for fragment files
                    ));
                }
            }
            case VIDEO -> {
                VideoContainerEnum videoContainer = quality.getVideoContainer();

                arguments.addAll(List.of(
                    "-o",
                    savePath.getAbsolutePath() + "/" + getVideoNamePattern(),
                    "-f",
                    getQualitySettings().buildQualitySelector(),
                    "--merge-output-format",
                    videoContainer.getValue()
                ));

                if(isEmbedThumbnailAndMetadata()){
                    arguments.addAll(List.of(
                        "--embed-thumbnail",
                        "--embed-metadata",
                        "--embed-chapters"
                    ));

                    switch(quality.getVideoContainer()){
                        case MKV, MP4, WEBM ->
                            arguments.addAll(List.of(
                                "--embed-subs",
                                "--sub-langs",
                                "all,-live_chat"
                            ));
                    }
                }

                if(config.isTranscodeAudioToAAC()){
                    arguments.addAll(List.of(
                        "--postprocessor-args",
                        //Opus is not supported by some native video players
                        "ffmpeg:-c:a aac -b:a " + (audioBitrate == AudioBitrateEnum.NO_AUDIO ? 320 : audioBitrate.getValue()) + "k"
                    ));
                }
            }
            case AUDIO -> {
                if(audioBitrate != AudioBitrateEnum.NO_AUDIO){
                    String audioPatternWithBitrate = getAudioNamePattern()
                        .replace("%(audio_bitrate)s", audioBitrate.getValue() + "kbps");

                    arguments.addAll(List.of(
                        "-o",
                        savePath.getAbsolutePath() + "/" + audioPatternWithBitrate,
                        "-f",
                        "bestaudio",
                        "--extract-audio",
                        "--audio-format",
                        quality.getAudioContainer().getValue(),
                        "--audio-quality",
                        audioBitrate.getValue() + "k"
                    ));

                    if(isEmbedThumbnailAndMetadata()){
                        arguments.addAll(List.of(
                            "--embed-thumbnail",
                            "--embed-metadata"
                        ));
                    }
                }
            }
            case THUMBNAILS -> {
                arguments.addAll(List.of(
                    "-o",
                    savePath.getAbsolutePath() + "/" + getVideoNamePattern(),
                    "--write-thumbnail",
                    "--skip-download",
                    "--convert-thumbnails",
                    quality.getThumbnailContainer().getValue()
                ));
            }
            case SUBTITLES -> {
                arguments.addAll(List.of(
                    "-o",
                    savePath.getAbsolutePath() + "/" + getVideoNamePattern(),
                    "--all-subs",
                    "--skip-download",
                    "--sub-format",
                    quality.getSubtitleContainer().getValue(),
                    "--convert-subs",
                    quality.getSubtitleContainer().getValue()
                ));
            }
            default ->
                throw new IllegalArgumentException();
        }

        return arguments;
    }

    @JsonIgnore
    @Override
    public boolean areCookiesRequired(){
        return false;
    }

    @JsonIgnore
    @Override
    public boolean canAcceptUrl(String url, GDownloader main){
        return true;
    }

}
