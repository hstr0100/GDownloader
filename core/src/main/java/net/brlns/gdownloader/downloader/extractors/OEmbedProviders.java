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
package net.brlns.gdownloader.downloader.extractors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.URLUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class OEmbedProviders {

    private static final String PROVIDERS_URL = "https://oembed.com/providers.json";

    @Getter
    private final HttpClient httpClient;

    @Getter
    private List<Provider> providers = new ArrayList<>();

    public OEmbedProviders() {
        httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();

        loadProviders();
    }

    private void loadProviders() {
        GDownloader.GLOBAL_THREAD_POOL.submitWithPriority(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROVIDERS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", URLUtils.GLOBAL_USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    providers = GDownloader.OBJECT_MAPPER.readValue(response.body(),
                        GDownloader.OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                            List.class, Provider.class));

                    saveProvidersToCache(response.body());

                    log.info("Loaded {} oEmbed providers", providers.size());
                    return;
                } else {
                    log.error("Failed to load oEmbed providers. Status code: {}", response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error loading oEmbed providers", e);
            }

            loadProvidersFromCache();
        }, 100);
    }

    private void saveProvidersToCache(String providersJson) {
        Path filePath = getProvidersCachePath();
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, providersJson,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("Saved oEmbed providers to {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save oEmbed providers to cache file", e);
        }
    }

    private boolean loadProvidersFromCache() {
        Path filePath = getProvidersCachePath();
        if (!Files.exists(filePath)) {
            log.warn("Local providers file does not exist: {}", filePath);

            return false;
        }

        try {
            String fileContent = Files.readString(filePath);
            providers = GDownloader.OBJECT_MAPPER.readValue(fileContent,
                GDownloader.OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                    List.class, Provider.class));
            log.info("Loaded {} oEmbed providers from cache", providers.size());

            return true;
        } catch (IOException e) {
            log.error("Error loading oEmbed providers from cache", e);

            return false;
        }
    }

    private Path getProvidersCachePath() {
        return Path.of(GDownloader.getWorkDirectory().getAbsolutePath(), "oembed_providers.json");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Provider {

        @JsonProperty("provider_name")
        private String providerName = "";
        @JsonProperty("provider_url")
        private String providerUrl = "";

        private List<Endpoint> endpoints = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Endpoint {

        private List<String> schemes = new ArrayList<>();
        private String url = "";
        private List<String> formats = new ArrayList<>();

        @JsonProperty("discovery")
        private boolean supportsDiscovery;
    }
}
