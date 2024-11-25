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
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;

import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class YoutubeFilter extends GenericFilter {

    public static final String ID = "youtube";

    @SuppressWarnings("this-escape")
    public YoutubeFilter() {
        setId(ID);
        setFilterName("Youtube");
        setUrlRegex("^(https?:\\/\\/)?(www\\.)?(youtube\\.com|youtu\\.be)(?!.*(\\/live|\\/playlist|list=)).*$");
        setVideoNamePattern("%(title).60s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s");
        setAudioNamePattern("%(title).60s (%(audio_bitrate)s).%(ext)s");
        setEmbedThumbnailAndMetadata(true);
    }

    @JsonIgnore
    @Override
    protected List<String> buildArguments(AbstractDownloader downloader, DownloadTypeEnum typeEnum, DownloadManager manager, File savePath, String inputUrl) {
        Settings config = manager.getMain().getConfig();

        List<String> arguments = super.buildArguments(downloader, typeEnum, manager, savePath, inputUrl);

        switch (downloader.getDownloaderId()) {
            case YT_DLP -> {
                switch (typeEnum) {
                    case ALL -> {
                        if (config.isUseSponsorBlock()) {
                            arguments.addAll(List.of(
                                "--sponsorblock-mark",
                                "sponsor,intro,outro,selfpromo,interaction,music_offtopic"
                            ));
                        }
                    }
                    case VIDEO -> {
                        if (isEmbedThumbnailAndMetadata()) {
                            arguments.addAll(List.of(
                                "--parse-metadata",
                                "description:(?s)(?P<meta_comment>.+)"
                            ));
                        }
                    }
                    case AUDIO -> {
                        if (isEmbedThumbnailAndMetadata()) {
                            arguments.addAll(List.of(
                                "--parse-metadata",
                                "description:(?s)(?P<meta_comment>.+)"
                            ));
                        }
                    }
                    case SUBTITLES -> {
                        if (config.isDownloadAutoGeneratedSubtitles()) {
                            // Extremely discouraged: this can trigger rate limiting extremely fast. Aside from being slow.
                            // Of note: embedding these vtt files into video files does not seem to work. Not even with --convert-subs.
                            arguments.add("--write-auto-sub");
                        }
                    }
                }
            }
            default -> {

            }
        }

        return arguments;
    }

    @JsonIgnore
    @Override
    public boolean canAcceptUrl(String url, GDownloader main) {
        return !isYoutubeChannel(url) || isYoutubeChannel(url) && main.getConfig().isDownloadYoutubeChannels();
    }

    @JsonIgnore
    public static boolean isYoutubeChannel(String s) {
        return s.contains("youtube") && (s.contains("/@") || s.contains("/channel"));
    }

}
