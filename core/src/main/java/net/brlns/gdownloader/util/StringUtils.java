/*
 * Copyright (C) 2024 @hstr0100
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
package net.brlns.gdownloader.util;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class StringUtils {

    public static String truncate(String input, int length) {
        if (input.length() > length) {
            input = input.substring(0, length - 3) + "...";
        }

        return input;
    }

    public static String getHumanReadableFileSize(long bytes) {
        assert bytes >= 0 && bytes < Long.MAX_VALUE : "Invalid argument. Expected valid positive long";

        String[] units = {"B", "KB", "MB", "GB", "TB", "EB"};
        int unitIndex = 0;

        double size = (double)bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f%s", size, units[unitIndex]);
    }
}
