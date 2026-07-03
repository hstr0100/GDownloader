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

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.util.URLThumbnailLoader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class MetadataManager {

    @Getter
    private final List<IMetadataExtractor> extractors = new ArrayList<>();

    @Getter
    @Setter
    private IMetadataExtractor defaultExtractor;

    private final IMetadataExtractor faviconExtractor = new FaviconMetadataExtractor();

    public MetadataManager() {
        registerExtractor(new SpotifyMetadataExtractor());
        registerExtractor(defaultExtractor = new OEmbedMetadataExtractor());
    }

    @PostConstruct
    public void init() {
        for (IMetadataExtractor extractor : extractors) {
            extractor.init();
        }

        faviconExtractor.init();
    }

    public void registerExtractor(@NonNull IMetadataExtractor extractor) {
        extractors.add(extractor);
    }

    public Optional<MediaInfo> fetchMetadata(@NonNull String url) throws Exception {
        if (url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        // Find the first extractor that can consume this URL directly
        Optional<IMetadataExtractor> matchedExtractor = extractors.stream()
            .filter(extractor -> extractor.canConsume(url))
            .findFirst();

        // Fallback to default extractor
        IMetadataExtractor extractorToUse = matchedExtractor.orElse(defaultExtractor);

        Optional<MediaInfo> result = Optional.empty();

        if (extractorToUse != null) {
            try {
                result = extractorToUse.fetchMetadata(url);
            } catch (Exception e) {
                log.warn("{} failed for {}, falling back to favicon: {}",
                    extractorToUse.getClass().getSimpleName(), url, e.getMessage());
            }
        }

        if (result.isEmpty()) {
            result = faviconExtractor.fetchMetadata(url);
        } else {
            augmentThumbnailIfMissing(result.get(), url);
        }

        return result;
    }

    public void augmentThumbnailIfMissing(MediaInfo mediaInfo, String url) {
        if (mediaInfo.getFallbackThumbnailImage() != null) {
            return;
        }

        URLThumbnailLoader.tryLoadFavicon(url).ifPresentOrElse(
            favicon -> {
                mediaInfo.setFallbackThumbnailImage(favicon);

                log.debug("Attached a fallback favicon image to {}; extractor produced no thumbnail", url);
            },
            () -> log.debug("No favicon available to augment {} either", url)
        );
    }
}
