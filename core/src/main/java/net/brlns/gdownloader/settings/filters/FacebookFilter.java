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
public class FacebookFilter extends GenericFilter {

    public static final String ID = "facebook";

    @SuppressWarnings("this-escape")
    public FacebookFilter() {
        setId(ID);
        setFilterName("Facebook");
        setUrlRegex("^(https?:\\/\\/)?(www\\.)?facebook\\.com(\\/.*)?$");
        setVideoNamePattern("%(title).60s (%(upload_date)s %(resolution)s).%(ext)s");
        setAudioNamePattern(getVideoNamePattern().replace("%(resolution)s", "%(audio_bitrate)s"));
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
                        if (!config.isRandomIntervalBetweenDownloads()) {
                            // In my experience, Facebook pretty much requires this. So we add it regardless of settings.
                            arguments.addAll(List.of(
                                "--max-sleep-interval",
                                "30",
                                "--min-sleep-interval",
                                "15"
                            ));
                        }
                    }
                }
            }
            default -> {

            }
        }

        return arguments;
    }
}
