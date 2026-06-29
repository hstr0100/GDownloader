/*
 * Copyright (C) 2026 @hstr0100
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates a valid current Firefox User-Agent to appease Codeberg's creative definition of a "public" API.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class FirefoxUserAgentProvider {

    private static final String MOZILLA_API_URL
        = "https://product-details.mozilla.org/1.0/firefox_versions.json";
    private static final String USER_AGENT_TEMPLATE
        = "Mozilla/5.0 (X11; Linux x86_64; rv:%1$s) Gecko/20100101 Firefox/%1$s";
    private static final String FALLBACK_USER_AGENT
        = "Mozilla/5.0 (X11; Linux x86_64; rv:152.0) Gecko/20100101 Firefox/152.0";
    private static final Pattern VERSION_PATTERN
        = Pattern.compile("\"LATEST_FIREFOX_VERSION\"\\s*:\\s*\"([^\"]+)\"");

    public static String getLatestFirefoxUserAgent() {
        return Holder.USER_AGENT;
    }

    private static class Holder {

        static final String USER_AGENT = fetchUserAgent();
    }

    private static String fetchUserAgent() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOZILLA_API_URL))
                .timeout(Duration.ofSeconds(1))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Matcher matcher = VERSION_PATTERN.matcher(response.body());

            if (matcher.find()) {
                String majorVersion = matcher.group(1).split("\\.")[0];
                return String.format(USER_AGENT_TEMPLATE, majorVersion + ".0");
            }

            log.debug("Firefox version not found in response, using fallback");
        } catch (Exception e) {
            log.debug("Error fetching latest Firefox User-Agent: {}", e.getMessage());
        }

        return FALLBACK_USER_AGENT;
    }
}
