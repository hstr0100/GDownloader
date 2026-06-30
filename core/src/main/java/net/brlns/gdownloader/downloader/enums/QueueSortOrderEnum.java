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
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum QueueSortOrderEnum implements ISettingsEnum {
    URL("enums.sort_order.url",
        Comparator.comparing(QueueEntry::getUrl)),
    URL_REVERSE("enums.sort_order.url_reverse",
        Comparator.comparing(QueueEntry::getUrl).reversed()),
    TITLE("enums.sort_order.title",
        Comparator.comparing(QueueSortOrderEnum::titleOrNull,
            Comparator.nullsLast(String::compareTo))),
    TITLE_REVERSE("enums.sort_order.title_reverse",
        Comparator.comparing(QueueSortOrderEnum::titleOrNull,
            Comparator.nullsLast(String::compareTo)).reversed()),
    STATUS("enums.sort_order.status",
        Comparator.comparingInt(e -> e.getCurrentQueueCategory().getComparatorOrder())),
    ADDED("enums.sort_order.added",
        Comparator.comparingLong(QueueEntry::getDownloadId)),
    ADDED_REVERSE("enums.sort_order.added_reverse",
        Comparator.comparingLong(QueueEntry::getDownloadId).reversed()),
    SEQUENCE("enums.sort_order.sequence",
        Comparator.comparing(QueueEntry::getCurrentSequence,
            Comparator.nullsLast(Long::compareTo))),
    PLAYLIST("enums.sort_order.playlist",
        Comparator.comparing(QueueEntry::isPlaylist).reversed()
            .thenComparing(QueueEntry::getCurrentSequence,
                Comparator.nullsLast(Long::compareTo))
            .thenComparingLong(QueueEntry::getDownloadId));

    private final Comparator<QueueEntry> comparator;
    private final String translationKey;

    private QueueSortOrderEnum(String translationKeyIn, Comparator<QueueEntry> comparatorIn) {
        translationKey = translationKeyIn;
        comparator = comparatorIn;
    }

    private static String titleOrNull(QueueEntry entry) {
        return entry.getMediaInfo() != null && !nullOrEmpty(entry.getMediaInfo().getTitle())
            ? entry.getMediaInfo().getTitle()
            : null;
    }
}
