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

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class URLUtils {

    @Nullable
    public static String getVideoId(String youtubeUrl) {
        try {
            URL url = new URI(youtubeUrl).toURL();
            String host = url.getHost();

            if (host != null && host.contains("youtube.com")) {
                String videoId = getParameter(url, "v");
                if (videoId != null) {
                    return videoId;
                }
            }
        } catch (Exception e) {
            log.debug("Invalid url {}", youtubeUrl, e.getLocalizedMessage());
        }

        return null;
    }

    @Nullable
    public static String filterVideo(String youtubeUrl) {
        String videoId = getVideoId(youtubeUrl);
        if (videoId != null) {
            return "https://www.youtube.com/watch?v=" + videoId;
        }

        return youtubeUrl;
    }

    @Nullable
    public static String filterPlaylist(String youtubeUrl) {
        try {
            URL url = new URI(youtubeUrl).toURL();
            String host = url.getHost();

            if (host != null && host.contains("youtube.com")) {
                String videoId = getParameter(url, "list");
                if (videoId != null) {
                    return "https://www.youtube.com/playlist?list=" + videoId;
                }
            }

            return null;
        } catch (MalformedURLException | URISyntaxException e) {
            log.debug("Invalid url {} {}", youtubeUrl, e.getLocalizedMessage());
        }

        return null;
    }

    @Nullable
    public static String getParameter(URL url, String parameterName) {
        String query = url.getQuery();

        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && keyValue[0].equals(parameterName)) {
                    return keyValue[1];
                }
            }
        }

        return null;
    }

    public static String removeParameter(URL url, String parameterName) throws Exception {
        String query = url.getQuery();

        if (query == null || query.isEmpty()) {
            return url.toString();
        }

        Map<String, String> queryParams = parseQueryString(query);

        queryParams.remove(parameterName);

        String newQuery = buildQueryString(queryParams);

        String baseUrl = url.toString().split("\\?")[0];
        return baseUrl + (newQuery.isEmpty() ? "" : "?" + newQuery);
    }

    private static Map<String, String> parseQueryString(String query) throws UnsupportedEncodingException {
        Map<String, String> queryParams = new LinkedHashMap<>();

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], "UTF-8");
                String value = URLDecoder.decode(parts[1], "UTF-8");
                queryParams.put(key, value);
            }
        }

        return queryParams;
    }

    private static String buildQueryString(Map<String, String> queryParams) throws UnsupportedEncodingException {
        StringBuilder queryString = new StringBuilder();

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (queryString.length() > 0) {
                queryString.append("&");
            }

            queryString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            queryString.append("=");
            queryString.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return queryString.toString();
    }

}
