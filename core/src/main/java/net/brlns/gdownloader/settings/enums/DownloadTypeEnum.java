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
package net.brlns.gdownloader.settings.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum DownloadTypeEnum implements ISettingsEnum {
    ALL((DownloaderIdEnum)null),
    // Downloads will also follow this specific order
    VIDEO(DownloaderIdEnum.YT_DLP),
    AUDIO(DownloaderIdEnum.YT_DLP),
    SUBTITLES(DownloaderIdEnum.YT_DLP),
    THUMBNAILS(DownloaderIdEnum.YT_DLP),
    GALLERY(DownloaderIdEnum.GALLERY_DL),
    SPOTIFY(DownloaderIdEnum.SPOTDL),
    DIRECT(DownloaderIdEnum.DIRECT_HTTP);

    private static final Map<DownloaderIdEnum, List<DownloadTypeEnum>> CACHE = new HashMap<>();

    private final DownloaderIdEnum[] downloaderIds;

    private DownloadTypeEnum(DownloaderIdEnum... downloaderIdsIn) {
        downloaderIds = downloaderIdsIn;
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return name().toLowerCase();
    }

    /**
     * Returns the download types associated with the given downloader ID.
     *
     * @param downloaderId the DownloaderIdEnum to filter by.
     * @return a list of DownloadTypeEnum values associated with the given DownloaderIdEnum.
     */
    public static List<DownloadTypeEnum> getForDownloaderId(DownloaderIdEnum downloaderId) {
        return CACHE.computeIfAbsent(downloaderId, id -> {
            return Arrays.stream(values())
                .filter(downloadType -> downloadType.downloaderIds != null
                && Arrays.asList(downloadType.downloaderIds).contains(id))
                .collect(Collectors.toList());
        });
    }
}
