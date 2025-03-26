package net.brlns.gdownloader.downloader.extractors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.extractors.OEmbedProviders.Endpoint;
import net.brlns.gdownloader.downloader.extractors.OEmbedProviders.Provider;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.util.URLUtils;

/**
 * Reference Spec https://oembed.com/
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class OEmbedMetadataExtractor implements IMetadataExtractor {

    private static final OEmbedProviders oembedProviders = new OEmbedProviders();

    protected Optional<OEmbedDTO> getOEmbedDTO(String url) {
        for (Provider provider : oembedProviders.getProviders()) {
            for (Endpoint endpoint : provider.getEndpoints()) {
                for (String scheme : endpoint.getSchemes()) {
                    String regex = schemeToRegex(scheme);
                    if (Pattern.compile(regex).matcher(url).matches()) {
                        return fetchOEmbedDTO(url, endpoint, provider.getProviderName());
                    }
                }
            }
        }

        return Optional.empty();
    }

    private String schemeToRegex(String scheme) {
        return "^" + Pattern.quote(scheme)
            .replace("*", "\\E.*\\Q")
            .replace("\\Q\\E", "") + "$";
    }

    private Optional<OEmbedDTO> fetchOEmbedDTO(String url, Endpoint endpoint, String providerName) {
        try {
            String apiEndpoint = endpoint.getUrl();

            // Handle URL format (XML vs JSON)
            if (!apiEndpoint.contains("{format}")) {
                apiEndpoint += apiEndpoint.contains("?") ? "&" : "?";
                apiEndpoint += "url=" + URLEncoder.encode(url, "UTF-8");

                // Add format if the endpoint supports it
                if (endpoint.getFormats().contains("json")) {
                    apiEndpoint += "&format=json";
                }
            } else {
                // Replace format placeholder with json
                apiEndpoint = apiEndpoint.replace("{format}", "json");
                apiEndpoint += apiEndpoint.contains("?") ? "&" : "?";
                apiEndpoint += "url=" + URLEncoder.encode(url, "UTF-8");
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", URLUtils.GLOBAL_USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = oembedProviders.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                if (body.isEmpty() || !body.startsWith("{")) {
                    return Optional.empty();
                }

                if (log.isDebugEnabled()) {
                    log.info("oEmbed endpoint: {}", apiEndpoint);
                    log.info("oEmbed data: {}", body);
                }

                OEmbedDTO data = GDownloader.OBJECT_MAPPER.readValue(body, OEmbedDTO.class);
                data.setProviderName(providerName);

                return Optional.of(data);
            } else {
                log.warn("Failed to fetch oEmbed data for URL: {}. Status code: {}", url, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching oEmbed data for URL: {}", url, e);
        }

        return Optional.empty();
    }

    @Override
    public boolean canConsume(String urlIn) {
        return true;
    }

    @Override
    public Optional<MediaInfo> fetchMetadata(String urlIn) throws Exception {
        if (log.isDebugEnabled()) {
            log.info("{} fetching oEmbed metadata for: {}", getClass().getSimpleName(), urlIn);
        }

        Optional<OEmbedDTO> optionalDTO = getOEmbedDTO(urlIn);
        if (optionalDTO.isPresent()) {
            MediaInfo mediaInfo = convertToMediaInfo(optionalDTO.get(), urlIn);
            if (mediaInfo.isValid()) {
                return Optional.of(mediaInfo);
            }
        }

        return Optional.empty();
    }

    protected MediaInfo convertToMediaInfo(OEmbedDTO response, String urlIn) {
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setDescription(urlIn);

        if (response.getTitle() != null) {
            String title = response.getTitle();
            int dashIndex = title.indexOf(" - ");
            if (dashIndex != -1) {
                mediaInfo.setChannelId(title.substring(0, dashIndex).trim());
            }

            mediaInfo.setTitle(title);
        }

        if (response.getProviderName() != null) {
            mediaInfo.setHostDisplayName(response.getProviderName());
        }

        if (response.getThumbnailUrl() != null) {
            mediaInfo.setThumbnail(response.getThumbnailUrl());
        }

        if (response.getWidth() != null) {
            mediaInfo.setWidth(response.getWidth());
        }

        if (response.getHeight() != null) {
            mediaInfo.setHeight(response.getHeight());
        }

        return mediaInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OEmbedDTO {

        private String type;
        private String version;
        private String title;
        @JsonProperty("author_name")
        private String authorName;
        @JsonProperty("author_url")
        private String authorUrl;
        @JsonProperty("provider_name")
        private String providerName;
        @JsonProperty("provider_url")
        private String providerUrl;
        @JsonProperty("cache_age")
        private Long cacheAge;
        @JsonProperty("thumbnail_url")
        private String thumbnailUrl;
        @JsonProperty("thumbnail_width")
        private Integer thumbnailWidth;
        @JsonProperty("thumbnail_height")
        private Integer thumbnailHeight;
        private String url;
        //private String html;
        private Integer width;
        private Integer height;
    }
}
