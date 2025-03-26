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

import jakarta.annotation.Nullable;
import java.io.File;
import java.text.DecimalFormat;
import java.time.Duration;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class StringUtils {

    private static final DecimalFormat PERCENT_FORMATTER = new DecimalFormat("#0.0");

    public static String formatPercent(double percent) {
        return PERCENT_FORMATTER.format(percent);
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

    public static String convertTime(long timeInMillis) {
        // If the time exceeds 24 hours, return "n/a". Tough luck buddy
        if (timeInMillis >= Duration.ofDays(1).toMillis()) {
            return "n/a";
        }

        Duration duration = Duration.ofMillis(timeInMillis);

        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        StringBuilder timeString = new StringBuilder();

        // Add hours if non-zero
        if (hours > 0) {
            timeString.append(hours).append(":");
        }

        // Add minutes if non-zero or if there are hours
        if (minutes > 0 || hours > 0) {
            if (timeString.length() > 0) {
                timeString.append(String.format("%02d:", minutes));
            } else {
                timeString.append(minutes).append(":");
            }
        }

        // Add seconds
        timeString.append(String.format("%02d", seconds));

        return timeString.toString();
    }

    public static String getStringAfterLastSeparator(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        } else {
            while (filePath.endsWith(File.separator)) {
                filePath = filePath.substring(0, filePath.length() - 1);
            }

            if (!filePath.contains(File.separator)) {
                return filePath;
            }

            String fileName = filePath.substring(filePath.lastIndexOf(File.separator) + 1);

            return !fileName.isEmpty() ? fileName : filePath;
        }
    }

    public static boolean nullOrEmpty(@Nullable String input) {
        return input == null || input.isEmpty();
    }

    public static boolean notNullOrEmpty(@Nullable String input) {
        return input != null && !input.trim().isEmpty();
    }
}
