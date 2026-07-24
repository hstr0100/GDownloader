/*
 * Copyright (C) 2026 hstr0100
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

import jakarta.annotation.Nullable;
import lombok.Getter;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum QueueFilterEnum implements ISettingsEnum {
    ALL("enums.filter_by.all", null),
    RUNNING("enums.filter_by.running", QueueCategoryEnum.RUNNING),
    SCHEDULED("enums.filter_by.scheduled", QueueCategoryEnum.SCHEDULED),
    QUEUED("enums.filter_by.queued", QueueCategoryEnum.QUEUED),
    COMPLETED("enums.filter_by.completed", QueueCategoryEnum.COMPLETED),
    FAILED("enums.filter_by.failed", QueueCategoryEnum.FAILED);

    private final String translationKey;

    @Nullable
    private final QueueCategoryEnum category;

    private QueueFilterEnum(String translationKeyIn, @Nullable QueueCategoryEnum categoryIn) {
        translationKey = translationKeyIn;
        category = categoryIn;
    }

    public boolean matches(@Nullable QueueCategoryEnum entryCategory) {
        return this == ALL || category == entryCategory;
    }
}
