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
package net.brlns.gdownloader.downloader.enums;

import lombok.Getter;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum DownloadStatusEnum implements ISettingsEnum {
    QUERYING("enums.download_status.querying"),
    STOPPED("enums.download_status.stopped"),
    SKIPPED("enums.download_status.skipped"),
    QUEUED("enums.download_status.queued"),
    STARTING("enums.download_status.starting"),
    RETRYING("enums.download_status.retrying"),
    PREPARING("enums.download_status.preparing"),
    PROCESSING("enums.download_status.processing"),
    POST_PROCESSING("enums.download_status.post_processing"),
    DEDUPLICATING("enums.download_status.deduplicating"),
    DOWNLOADING("enums.download_status.downloading"),
    WAITING("enums.download_status.waiting"),
    COMPLETE("enums.download_status.complete"),
    TRANSCODING("enums.download_status.transcoding"),
    FAILED("enums.download_status.failed"),
    NO_METHOD("enums.download_status.no_method");

    private final String translationKey;

    private DownloadStatusEnum(String translationKeyIn) {
        translationKey = translationKeyIn;
    }
}
