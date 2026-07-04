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
package net.brlns.gdownloader.downloader.extractors;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.util.URLThumbnailLoader;
import net.brlns.gdownloader.util.URLThumbnailLoader.FaviconResult;

/**
 * Last-resort metadata source.
 *
 * When every real extractor (yt-dlp, oEmbed, etc.) has failed to produce
 * anything, this pulls what it can directly out of the page: a favicon or
 * apple-touch-icon for the thumbnail, and the page's og:title or html title as a
 * stand-in title. Neither is guaranteed to be present - either one alone is still
 * treated as a successful result, both missing is treated as failure.
 *
 * This extractor is never matched by URL directly; canConsume always returns false,
 * and it is invoked explicitly by MetadataManager as an unconditional fallback.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class FaviconMetadataExtractor implements IMetadataExtractor {

    @Override
    public void init() {

    }

    @Override
    public boolean canConsume(String urlIn) {
        return false;
    }

    @Override
    public Optional<MediaInfo> fetchMetadata(String urlIn) throws Exception {
        Optional<FaviconResult> resultOptional = URLThumbnailLoader.tryLoadFaviconWithMetadata(urlIn);
        if (resultOptional.isEmpty() || resultOptional.get().isEmpty()) {
            return Optional.empty();
        }

        FaviconResult favicon = resultOptional.get();

        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setOriginalUrl(urlIn);
        mediaInfo.setDescription(urlIn);

        favicon.titleOptional().ifPresent(mediaInfo::setTitle);
        favicon.imageOptional().ifPresent(mediaInfo::setFallbackThumbnailImage);

        boolean hasSomething = favicon.titleOptional().isPresent() || favicon.imageOptional().isPresent();
        if (!hasSomething) {
            return Optional.empty();
        }

        if (log.isDebugEnabled()) {
            log.debug("Favicon fallback for {} -> title: {} hasImage: {}",
                urlIn, favicon.titleOptional().isPresent(), favicon.imageOptional().isPresent());
        }

        return Optional.of(mediaInfo);
    }
}
