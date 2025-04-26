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
package net.brlns.gdownloader.downloader.enums;

import java.util.Comparator;
import lombok.Getter;
import net.brlns.gdownloader.downloader.QueueEntry;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum QueueSortOrderEnum implements ISettingsEnum {
    URL("enums.sort_order.url", (e1, e2) -> {
        return e1.getUrl().compareTo(e2.getUrl());
    }),
    URL_REVERSE("enums.sort_order.url_reverse", (e1, e2) -> {
        return e2.getUrl().compareTo(e1.getUrl());
    }),
    TITLE("enums.sort_order.title", (e1, e2) -> {
        MediaInfo info1 = e1.getMediaInfo();
        MediaInfo info2 = e2.getMediaInfo();

        if (info1 == null && info2 == null) {
            return 0;
        } else if (info1 == null || nullOrEmpty(info1.getTitle())) {
            return 1;
        } else if (info2 == null || nullOrEmpty(info2.getTitle())) {
            return -1;
        } else {
            return info1.getTitle().compareTo(info2.getTitle());
        }
    }),
    TITLE_REVERSE("enums.sort_order.title_reverse", (e1, e2) -> {
        MediaInfo info1 = e1.getMediaInfo();
        MediaInfo info2 = e2.getMediaInfo();

        if (info1 == null && info2 == null) {
            return 0;
        } else if (info1 == null || nullOrEmpty(info1.getTitle())) {
            return -1;
        } else if (info2 == null || nullOrEmpty(info2.getTitle())) {
            return 1;
        } else {
            return info2.getTitle().compareTo(info1.getTitle());
        }
    }),
    STATUS("enums.sort_order.status", (e1, e2) -> {
        return Integer.compare(
            e1.getCurrentQueueCategory().getComparatorOrder(),
            e2.getCurrentQueueCategory().getComparatorOrder()
        );
    }),
    ADDED("enums.sort_order.added", (e1, e2) -> {
        return Long.compare(e1.getDownloadId(), e2.getDownloadId());
    }),
    ADDED_REVERSE("enums.sort_order.added_reverse", (e1, e2) -> {
        return Long.compare(e2.getDownloadId(), e1.getDownloadId());
    }),
    SEQUENCE("enums.sort_order.sequence", (e1, e2) -> {
        if (e1.getCurrentSequence() == null || e2.getCurrentSequence() == null) {
            return 0;
        }

        return Long.compare(e1.getCurrentSequence(), e2.getCurrentSequence());
    });

    private final Comparator<QueueEntry> comparator;
    private final String translationKey;

    private QueueSortOrderEnum(String translationKeyIn, Comparator<QueueEntry> comparatorIn) {
        translationKey = translationKeyIn;
        comparator = comparatorIn;
    }
}
