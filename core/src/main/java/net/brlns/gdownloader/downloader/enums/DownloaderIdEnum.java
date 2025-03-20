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
public enum DownloaderIdEnum implements ISettingsEnum {
    YT_DLP("yt-dlp"),
    GALLERY_DL("gallery-dl"),
    SPOTDL("spotDL"),
    DIRECT_HTTP("http");

    private final String displayName;

    private DownloaderIdEnum(String displayNameIn) {
        displayName = displayNameIn;
    }

    @Override
    public String getTranslationKey() {
        return "";
    }
}
