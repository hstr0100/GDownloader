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
import net.brlns.gdownloader.downloader.structs.MediaInfo;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class MetadataManager {

    @Getter
    private final List<IMetadataExtractor> extractors = new ArrayList<>();

    @Getter
    @Setter
    private IMetadataExtractor defaultExtractor;

    public MetadataManager() {
        registerExtractor(new SpotifyMetadataExtractor());
        registerExtractor(defaultExtractor = new OEmbedMetadataExtractor());
    }

    @PostConstruct
    public void init() {
        for (IMetadataExtractor extractor : extractors) {
            extractor.init();
        }
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

        if (extractorToUse == null) {
            throw new IllegalStateException("No extractor found for URL and no default extractor set");
        }

        return extractorToUse.fetchMetadata(url);
    }
}
