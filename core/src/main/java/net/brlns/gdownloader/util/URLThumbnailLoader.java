/*
 * Copyright (C) 2026 hstr0100
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.collection.LRUCache;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class URLThumbnailLoader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final LRUCache<String, Optional<FaviconResult>> FAVICON_CACHE = new LRUCache<>(512);

    static {
        // Resorting to sheer ignorance here to make really sure these classes are loaded once and for all.
        for (String spi : new String[] {
            "com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi",
            "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi",
            "com.twelvemonkeys.imageio.plugins.bmp.BMPImageReaderSpi",
            "com.twelvemonkeys.imageio.plugins.bmp.CURImageReaderSpi",
            "com.twelvemonkeys.imageio.plugins.bmp.ICOImageReaderSpi"
        }) {
            forceRegisterReaderSpi(spi);
        }

        if (log.isDebugEnabled()) {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            Iterator<ImageReaderSpi> it = registry.getServiceProviders(ImageReaderSpi.class, false);
            while (it.hasNext()) {
                ImageReaderSpi spi = it.next();

                log.debug("Registered ImageReaderSpi: {} ({})",
                    spi.getClass().getName(), String.join(",", spi.getFormatNames()));
            }
        }
    }

    public static void init() {
        // no-op
    }

    private static void forceRegisterReaderSpi(String className) {
        try {
            Class<?> spiClass = Class.forName(className);
            IIORegistry registry = IIORegistry.getDefaultInstance();

            boolean alreadyRegistered = false;
            Iterator<ImageReaderSpi> it = registry.getServiceProviders(ImageReaderSpi.class, false);
            while (it.hasNext()) {
                if (it.next().getClass().equals(spiClass)) {
                    alreadyRegistered = true;
                    break;
                }
            }

            if (!alreadyRegistered) {
                ImageReaderSpi spi = (ImageReaderSpi)spiClass.getDeclaredConstructor().newInstance();
                registry.registerServiceProvider(spi);

                log.debug("Force-registered ImageIO SPI: {}", className);
            }
        } catch (ClassNotFoundException e) {
            log.warn("Could not resolve {} for force-registration: {}", className, e.toString());
        } catch (Exception e) {
            log.warn("Failed to force-register ImageIO SPI {}: {}", className, e.toString());
        }
    }

    public static Optional<BufferedImage> tryLoadThumbnailCropped(String url) {
        return tryLoadThumbnail(url, true);
    }

    public static Optional<BufferedImage> tryLoadThumbnailFull(String url) {
        return tryLoadThumbnail(url, false);
    }

    private static Optional<BufferedImage> tryLoadThumbnail(String url, boolean cropToSixteenByNine) {
        if (log.isDebugEnabled()) {
            log.debug("Trying to load thumbnail {}", url);
        }

        byte[] bytes = fetchBytes(url).orElse(null);

        if (bytes == null) {
            String stripped = URLUtils.removeQueryParameters(url);
            if (!stripped.equals(url)) {
                bytes = fetchBytes(stripped).orElse(null);
            }
        }

        if (bytes == null || bytes.length == 0) {
            log.debug("No usable bytes retrieved for thumbnail {}", url);

            return Optional.empty();
        }

        return decodeImageBytes(bytes, url).map(img -> {
            if (log.isDebugEnabled()) {
                log.debug("Thumbnail resolution: {}x{}", img.getWidth(), img.getHeight());
            }

            if (cropToSixteenByNine) {
                return ImageUtils.cropToSixteenByNineIfHorizontal(img);
            }

            return img;
        });
    }

    public static CompletableFuture<Optional<BufferedImage>> tryLoadThumbnailAsync(
        String url, boolean cropToSixteenByNine) {

        return CompletableFuture.supplyAsync(
            () -> tryLoadThumbnail(url, cropToSixteenByNine), GDownloader.GLOBAL_THREAD_POOL);
    }

    public record FaviconResult(BufferedImage image, String pageTitle) {

        public Optional<BufferedImage> imageOptional() {
            return Optional.ofNullable(image);
        }

        public Optional<String> titleOptional() {
            return Optional.ofNullable(pageTitle);
        }

        public boolean isEmpty() {
            return image == null && pageTitle == null;
        }
    }

    public static Optional<BufferedImage> tryLoadFavicon(String originalUrl) {
        return resolveFaviconResult(originalUrl).flatMap(FaviconResult::imageOptional);
    }

    public static Optional<FaviconResult> tryLoadFaviconWithMetadata(String originalUrl) {
        return resolveFaviconResult(originalUrl);
    }

    public static CompletableFuture<Optional<BufferedImage>> tryLoadFaviconAsync(String originalUrl) {
        return CompletableFuture.supplyAsync(
            () -> tryLoadFavicon(originalUrl), GDownloader.GLOBAL_THREAD_POOL);
    }

    public static CompletableFuture<Optional<FaviconResult>> tryLoadFaviconWithMetadataAsync(String originalUrl) {
        return CompletableFuture.supplyAsync(
            () -> tryLoadFaviconWithMetadata(originalUrl), GDownloader.GLOBAL_THREAD_POOL);
    }

    public static void clearFaviconCache() {
        FAVICON_CACHE.clear();
    }

    private static Optional<FaviconResult> resolveFaviconResult(String originalUrl) {
        Optional<String> originOpt = extractOrigin(originalUrl);
        if (originOpt.isEmpty()) {
            log.debug("Could not extract origin for favicon fallback from {}", originalUrl);

            return Optional.empty();
        }

        String origin = originOpt.get();

        return FAVICON_CACHE.computeIfAbsent(origin,
            key -> resolveFavicon(originalUrl, origin));
    }

    private static Optional<FaviconResult> resolveFavicon(String originalUrl, String origin) {
        if (log.isDebugEnabled()) {
            log.debug("Resolving favicon for {}", origin);
        }

        HtmlProbeResult probe = probePageHtml(originalUrl);

        for (String candidate : probe.iconCandidates()) {
            Optional<BufferedImage> img = fetchAndDecode(candidate, origin);
            if (img.isPresent()) {
                log.debug("Loaded favicon from discovered <link> for {}: {}", origin, candidate);

                return Optional.of(new FaviconResult(img.get(), probe.title()));
            }
        }

        for (String candidate : wellKnownFaviconCandidates(origin)) {
            Optional<BufferedImage> img = fetchAndDecode(candidate, origin);
            if (img.isPresent()) {
                log.debug("Loaded favicon from well-known path for {}: {}", origin, candidate);

                return Optional.of(new FaviconResult(img.get(), probe.title()));
            }
        }

        if (probe.title() != null) {
            log.debug("No favicon found for {}, but recovered page title: {}", origin, probe.title());

            return Optional.of(new FaviconResult(null, probe.title()));
        }

        if (log.isDebugEnabled()) {
            log.debug("Exhausted all favicon strategies for {}", origin);
        }

        return Optional.empty();
    }

    private record HtmlProbeResult(List<String> iconCandidates, String title) {

        private static final HtmlProbeResult EMPTY = new HtmlProbeResult(List.of(), null);
    }

    private static HtmlProbeResult probePageHtml(String pageUrl) {
        Optional<byte[]> htmlBytes = fetchBytes(pageUrl);
        if (htmlBytes.isEmpty()) {
            return HtmlProbeResult.EMPTY;
        }

        try {
            Document doc = Jsoup.parse(new String(htmlBytes.get(), StandardCharsets.UTF_8), pageUrl);

            Elements linkTags = doc.select("head link[href]");

            List<String> iconCandidates = linkTags.stream()
                .filter(el -> el.attr("rel").toLowerCase().contains("icon"))
                .map(IconCandidate::new)
                .filter(c -> !c.href.isBlank())
                .sorted(Comparator.comparingInt((IconCandidate c) -> c.score).reversed())
                .map(c -> c.href)
                .distinct()
                .toList();

            return new HtmlProbeResult(iconCandidates, extractTitle(doc));
        } catch (Exception e) {
            log.debug("Failed to parse HTML for {}: {}", pageUrl, e.toString());

            return HtmlProbeResult.EMPTY;
        }
    }

    private static String extractTitle(Document doc) {
        // og:title is usually cleaner than <title> - no " | Site Name" suffixes, no tracking junk
        String ogTitle = doc.select("meta[property=og:title]").attr("content").trim();
        if (!ogTitle.isBlank()) {
            return ogTitle;
        }

        String title = doc.title().trim();

        return title.isBlank() ? null : title;
    }

    private static List<String> wellKnownFaviconCandidates(String origin) {
        return List.of(
            origin + "/favicon.ico",
            origin + "/favicon.png",
            "https://www.google.com/s2/favicons?sz=128&domain_url=" + origin
        );
    }

    private static Optional<BufferedImage> fetchAndDecode(String url, String referer) {
        return fetchBytes(url, referer).flatMap(bytes -> decodeImageBytes(bytes, url));
    }

    private static Optional<String> extractOrigin(String url) {
        try {
            URI uri = URI.create(url);

            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }

            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";

            return Optional.of(scheme + "://" + host);
        } catch (Exception e) {
            log.debug("Failed to parse origin from {}: {}", url, e.toString());

            return Optional.empty();
        }
    }

    private static final class IconCandidate {

        private final String href;
        private final int score;

        private IconCandidate(Element linkEl) {
            // absUrl resolves relative/protocol-relative/root-relative hrefs against the doc's base URI
            href = linkEl.absUrl("href");

            String rel = linkEl.attr("rel").toLowerCase();
            int s = 0;

            String sizes = linkEl.attr("sizes");
            if (!sizes.isBlank() && !sizes.equalsIgnoreCase("any")) {
                try {
                    s = Integer.parseInt(sizes.toLowerCase().split("x")[0].trim());
                } catch (Exception ignored) {
                    // malformed sizes attribute - leave at 0
                }
            }

            if (rel.contains("apple-touch-icon")) {
                s += 1000;
            }

            score = s;
        }
    }

    private static Optional<byte[]> fetchBytes(String url) {
        return fetchBytes(url, null);
    }

    private static Optional<byte[]> fetchBytes(String url, String referer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", URLUtils.getGlobalUserAgent())
                // TODO: AVIF support
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip")
                .GET();

            if (referer != null) {
                builder.header("Referer", referer);
            }

            HttpResponse<byte[]> response = HTTP_CLIENT.send(
                builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() / 100 != 2) {
                log.debug("Fetch got HTTP {} for {}", response.statusCode(), url);

                return Optional.empty();
            }

            byte[] body = response.body();
            if ("gzip".equalsIgnoreCase(response.headers().firstValue("Content-Encoding").orElse(""))) {
                body = new GZIPInputStream(new ByteArrayInputStream(body)).readAllBytes();
            }

            return Optional.of(body);
        } catch (IOException e) {
            log.debug("Failed to fetch bytes for {}: {}", url, e.toString());

            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> decodeImageBytes(byte[] bytes, String url) {
        try (
            ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return Optional.empty();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true);
                    BufferedImage img = reader.read(0);
                    if (img != null) {
                        return Optional.of(img);
                    }
                } catch (Exception e) {
                    log.debug("Reader {} failed on {}: {}", reader.getClass().getName(), url, e.toString());
                } finally {
                    reader.dispose();
                }
            }

            if (log.isDebugEnabled()) {
                int n = Math.min(bytes.length, 16);
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    hex.append(String.format("%02X ", bytes[i]));
                }

                log.error("No suitable ImageIO reader for {} ({} bytes, magic={})",
                    url, bytes.length, hex.toString().trim());
            }
        } catch (IOException e) {
            log.debug("ImageIO stream error for {}: {}", url, e.toString());
        }

        return Optional.empty();
    }
}
