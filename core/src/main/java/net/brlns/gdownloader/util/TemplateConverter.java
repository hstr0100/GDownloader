/*
 * Copyright (C) 2025 @hstr0100
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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class TemplateConverter {

    private static final Map<String, String> SPOTDL_TEMPLATE_MAP = new HashMap<>();

    static {
        // Mapping from yt-dlp template variables to spotdl variables
        SPOTDL_TEMPLATE_MAP.put("title", "title");
        SPOTDL_TEMPLATE_MAP.put("uploader", "artists");
        SPOTDL_TEMPLATE_MAP.put("creator", "artists");
        SPOTDL_TEMPLATE_MAP.put("artist", "artist");
        SPOTDL_TEMPLATE_MAP.put("album", "album");
        SPOTDL_TEMPLATE_MAP.put("channel", "album-artist");
        SPOTDL_TEMPLATE_MAP.put("track", "title");
        SPOTDL_TEMPLATE_MAP.put("genre", "genre");
        SPOTDL_TEMPLATE_MAP.put("duration", "duration");
        SPOTDL_TEMPLATE_MAP.put("duration_string", "duration");
        SPOTDL_TEMPLATE_MAP.put("release_year", "year");
        SPOTDL_TEMPLATE_MAP.put("release_date", "original-date");
        SPOTDL_TEMPLATE_MAP.put("upload_date", "original-date");
        SPOTDL_TEMPLATE_MAP.put("track_number", "track-number");
        SPOTDL_TEMPLATE_MAP.put("n_entries", "tracks-count");
        SPOTDL_TEMPLATE_MAP.put("playlist", "list-name");
        SPOTDL_TEMPLATE_MAP.put("playlist_index", "list-position");
        SPOTDL_TEMPLATE_MAP.put("playlist_title", "list-name");
        SPOTDL_TEMPLATE_MAP.put("playlist_count", "list-length");
        SPOTDL_TEMPLATE_MAP.put("ext", "output-ext");
        SPOTDL_TEMPLATE_MAP.put("disc_number", "disc-number");
        SPOTDL_TEMPLATE_MAP.put("id", "track-id");
        SPOTDL_TEMPLATE_MAP.put("publisher", "publisher");
        SPOTDL_TEMPLATE_MAP.put("isrc", "isrc");
    }

    /**
     * Converts yt-dlp naming templates to spotDL
     */
    public static String convertTemplateForSpotDL(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Use a regex pattern to match yt-dlp template variables with all their formatting options.
        // You've earned yourself a royal cookie if you can understand this entire regex.
        Pattern pattern = Pattern.compile("%\\(([^>,:&|\\)]*)(?:[>,:&|][^\\)]*)?\\)([-#0+ ]*\\d*\\.?\\d*[diouxXheEfFgGcrsBlqDSUj])?");

        try {
            Matcher matcher = pattern.matcher(input);

            // Check if there are unbalanced parentheses which indicate malformed templates
            int openCount = 0;
            int closeCount = 0;
            for (char c : input.toCharArray()) {
                if (c == '(') {
                    openCount++;
                }
                if (c == ')') {
                    closeCount++;
                }
            }

            // If unbalanced, return original string
            if (openCount != closeCount) {
                return input;
            }

            StringBuffer result = new StringBuffer();

            // Process each match
            while (matcher.find()) {
                String baseVar = matcher.group(1);

                // Check if this is an empty field name
                if (baseVar.isEmpty()) {
                    matcher.appendReplacement(result, "{}");
                    continue;
                }

                // Handle object traversal and arithmetic
                String fieldName = baseVar;
                if (baseVar.contains(".")) {
                    fieldName = baseVar.split("\\.")[0]; // Get the part before first dot
                } else if (baseVar.contains("+") || baseVar.contains("-") || baseVar.contains("*")) {
                    // For arithmetic expressions, extract the variable name
                    fieldName = baseVar.split("[+\\-*]")[0];
                }

                // Look up the corresponding spotdl variable
                String spotdlVar = SPOTDL_TEMPLATE_MAP.getOrDefault(fieldName, fieldName);

                // For complex formatting that spotdl doesn't support, just map the base variable
                String replacement = "{" + spotdlVar + "}";

                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }

            // Append the remainder of the input string
            matcher.appendTail(result);

            return result.toString();
        } catch (Exception e) {
            // Something went wrong, return the original string
            log.error("Failed to parse naming template: {}", input);
            return input;
        }
    }
}
