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
import java.util.List;
import java.util.stream.Collectors;

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

    public static String formatBitrate(int kbps) {
        if (kbps < 1000) {
            return kbps + " Kbps";
        } else {
            double mbps = kbps / 1000.0;
            if (mbps < 10) {
                return String.format("%.2f Mbps", mbps);
            } else if (mbps < 100) {
                return String.format("%.1f Mbps", mbps);
            } else {
                return String.format("%d Mbps", (int)mbps);
            }
        }
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

    public static String escapeAndBuildCommandLine(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }

        return arguments.stream()
            .map(StringUtils::escapeCommandLineArgument)
            .collect(Collectors.joining(" "));
    }

    private static String escapeCommandLineArgument(String argument) {
        if (argument == null || argument.isEmpty()) {
            return "\"\"";
        }

        boolean needsQuotes = false;
        for (int i = 0; i < argument.length(); i++) {
            char c = argument.charAt(i);
            if (Character.isWhitespace(c) || isSpecialCommandLineChar(c)) {
                needsQuotes = true;
                break;
            }
        }

        if (!needsQuotes) {
            return argument;
        }

        StringBuilder sb = new StringBuilder(argument.length() + 10);// Some buffer for quotes and escapes
        sb.append('"');

        for (int i = 0; i < argument.length(); i++) {
            char c = argument.charAt(i);

            switch (c) {
                // Backslashes need special handling, because Windows.
                case '\\' -> {
                    // Count consecutive backslashes
                    int backslashCount = 1;
                    while (i + 1 < argument.length() && argument.charAt(i + 1) == '\\') {
                        backslashCount++;
                        i++;
                    }

                    // If backslashes are followed by a quote or at the end, double them
                    if (i + 1 == argument.length()) {
                        // At the end of the string
                        for (int j = 0; j < backslashCount * 2; j++) {
                            sb.append('\\');
                        }
                    } else if (argument.charAt(i + 1) == '"') {
                        // Before a quote
                        for (int j = 0; j < backslashCount * 2; j++) {
                            sb.append('\\');
                        }
                    } else {
                        // Regular backslashes
                        for (int j = 0; j < backslashCount; j++) {
                            sb.append('\\');
                        }
                    }
                }
                case '"' -> // Escape quotes
                    sb.append("\\\"");
                default -> // Add other characters as-is
                    sb.append(c);
            }
        }

        sb.append('"');

        return sb.toString().trim();
    }

    private static boolean isSpecialCommandLineChar(char c) {
        return c == '"' || c == '\\' || c == '\'' || c == '&'
            || c == '|' || c == ';' || c == '<' || c == '>'
            || c == '(' || c == ')' || c == '$' || c == '`'
            || c == '!' || c == '*' || c == '/';
    }

    public static String removeEmojis(String text) {
        String regex = "[\\x{1F000}-\\x{1FFFF}]|[\\x{2600}-\\x{27BF}]"
            + "|[\\x{1F300}-\\x{1F64F}]|[\\x{1F680}-\\x{1F6FF}]";

        return text.replaceAll(regex, "");
    }
}
