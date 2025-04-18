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

import lombok.Getter;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum DownloadPriorityEnum implements ISettingsEnum {
    MAXIMUM("enums.download_priority.maximum", "/assets/priority-maximum.png", 100),
    HIGH("enums.download_priority.high", "/assets/priority-high.png", 50),
    NORMAL("enums.download_priority.normal", "/assets/priority-normal.png", 0),
    LOW("enums.download_priority.low", "/assets/priority-low.png", -50),
    MINIMUM("enums.download_priority.minimum", "/assets/priority-minimum.png", -100);

    private final String translationKey;
    private final String iconAsset;
    private final int weight;

    private DownloadPriorityEnum(String translationKeyIn, String iconAssetIn, int weightIn) {
        translationKey = translationKeyIn;
        iconAsset = iconAssetIn;
        weight = weightIn;
    }
}
