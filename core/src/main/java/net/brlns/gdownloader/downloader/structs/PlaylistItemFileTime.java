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
package net.brlns.gdownloader.downloader.structs;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;

import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public class PlaylistItemFileTime {

    public static final String MARKER = "GDL_FILETIME|";

    private final String filePath;

    @Nullable
    private final LocalDateTime uploadTime;

    private PlaylistItemFileTime(String filePathIn, @Nullable LocalDateTime uploadTimeIn) {
        filePath = filePathIn;
        uploadTime = uploadTimeIn;
    }

    @Nullable
    public static PlaylistItemFileTime parse(String line) {
        if (!line.startsWith(MARKER)) {
            return null;
        }

        String remainder = line.substring(MARKER.length());
        int separatorIndex = remainder.indexOf('|');
        if (separatorIndex < 0) {
            return null;
        }

        String uploadDate = remainder.substring(0, separatorIndex);
        String filePath = remainder.substring(separatorIndex + 1);

        if (filePath.isEmpty()) {
            return null;
        }

        LocalDateTime uploadTime = null;
        if (notNullOrEmpty(uploadDate) && !uploadDate.equals("NA")) {
            try {
                uploadTime = LocalDate.parse(uploadDate,
                    DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
            } catch (Exception e) {
                // caller falls back to now()
            }
        }

        return new PlaylistItemFileTime(filePath, uploadTime);
    }
}
