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
package net.brlns.gdownloader.downloader.webscanner;

import jakarta.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.URLUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static net.brlns.gdownloader.util.URLUtils.getExtension;
import static net.brlns.gdownloader.util.URLUtils.getHost;
import static net.brlns.gdownloader.util.URLUtils.isHttpUrl;
import static net.brlns.gdownloader.util.URLUtils.resolve;
import static net.brlns.gdownloader.util.URLUtils.stripFragment;

/**
 * Technically a web crawler/spider, but named "scanner" for i10n purposes.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class WebScanner {

    private static final String[][] ATTRIBUTE_SOURCES = {
        {"img", "src"},
        {"img", "data-src"},
        {"img", "data-lazy-src"},
        {"img", "data-original"},
        {"img", "data-full-src"},
        {"source", "src"},
        {"video", "src"},
        {"video", "poster"},
        {"audio", "src"},
        {"a", "href"},
        {"area", "href"},
        {"link", "href"},
        {"embed", "src"},
        {"object", "data"},
        {"track", "src"},
        {"iframe", "src"}
    };

    private static final String[][] SRCSET_SOURCES = {
        {"img", "srcset"},
        {"img", "data-srcset"},
        {"source", "srcset"}
    };

    private static final String[] OG_META_PROPERTIES = {
        "og:image", "og:image:url", "og:image:secure_url",
        "og:video", "og:video:url", "og:video:secure_url",
        "og:audio", "og:audio:url", "og:audio:secure_url",
        "twitter:image", "twitter:image:src", "twitter:player:stream"
    };

    private static final Pattern CSS_URL_PATTERN
        = Pattern.compile("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");

    private final HttpClient httpClient;
    private final WebScannerExtensions extensions;

    private final long maxPageSizeBytes;
    private final Duration pageFetchTimeout;

    public WebScanner(HttpClient httpClientIn, WebScannerExtensions extensionsIn,
        long maxPageSizeBytesIn, Duration pageFetchTimeoutIn) {

        httpClient = httpClientIn;
        extensions = extensionsIn;
        maxPageSizeBytes = maxPageSizeBytesIn;
        pageFetchTimeout = pageFetchTimeoutIn;
    }

    public Set<String> scanForMediaLinks(String startUrl, @Nullable String initialHtml,
        int maxDepth, boolean strictHost, @Nullable Supplier<Boolean> cancelHook) {

        return scanForMediaLinks(startUrl, initialHtml, maxDepth, strictHost, cancelHook, null);
    }

    public Set<String> scanForMediaLinks(String startUrl, @Nullable String initialHtml,
        int maxDepth, boolean strictHost, @Nullable Supplier<Boolean> cancelHook,
        @Nullable ScanStatusListener statusListener) {
        log.debug("Starting media scan for {} (Max Depth: {}, Strict Host: {})", startUrl, maxDepth, strictHost);

        notify(statusListener, "gui.web_scanner.status.starting", startUrl);

        Set<String> mediaLinks = new LinkedHashSet<>();
        Set<String> visitedPages = new HashSet<>();
        String baseHost = getHost(startUrl);

        if (baseHost != null) {
            scanRecursive(startUrl, initialHtml, 0, maxDepth,
                strictHost, baseHost, visitedPages, mediaLinks, cancelHook, statusListener);
        }

        notify(statusListener, "gui.web_scanner.status.finished", mediaLinks.size(), visitedPages.size());
        log.debug("Scan finished. Found {} downloadable media links.", mediaLinks.size());

        return mediaLinks;
    }

    private void scanRecursive(String url, @Nullable String html,
        int depth, int maxDepth, boolean strictHost, String baseHost,
        Set<String> visited, Set<String> media, @Nullable Supplier<Boolean> cancelHook,
        @Nullable ScanStatusListener statusListener) {

        if (cancelHook != null && cancelHook.get()) {
            log.debug("Scan aborted via cancel hook at depth {} for url: {}", depth, url);
            return;
        }

        String cleanUrl = stripFragment(url);

        if (depth > maxDepth || !visited.add(cleanUrl)) {
            return;
        }

        if (strictHost && !baseHost.equalsIgnoreCase(getHost(cleanUrl))) {
            return;
        }

        log.debug("Scanning page [Depth {}/{}]: {}", depth, maxDepth, cleanUrl);
        notify(statusListener, "gui.web_scanner.status.fetching_page", cleanUrl, depth, maxDepth);

        Document doc = null;
        try {
            if (html != null && !html.isBlank()) {
                doc = Jsoup.parse(html, cleanUrl);
            } else {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl))
                    .timeout(pageFetchTimeout)
                    .header("User-Agent", URLUtils.getGlobalUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 == 2) {
                    doc = Jsoup.parse(readBounded(response.body(), maxPageSizeBytes), cleanUrl);
                } else {
                    response.body().close();

                    if (log.isDebugEnabled()) {
                        log.warn("Server returned HTTP {} for {}", response.statusCode(), cleanUrl);
                    }

                    notify(statusListener, "gui.web_scanner.status.page_http_error", cleanUrl, response.statusCode());
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Failed to retrieve or parse document {}: {}", cleanUrl, e.getMessage());
            }

            notify(statusListener, "gui.web_scanner.status.page_fetch_failed", cleanUrl);

            return;
        }

        if (doc == null) {
            return;
        }

        int beforeCount = media.size();

        collectAttributeLinks(doc, media);
        collectSrcSetLinks(doc, cleanUrl, media);
        collectMetaLinks(doc, cleanUrl, media);
        collectInlineStyleLinks(doc, cleanUrl, media);
        collectStyleBlockLinks(doc, cleanUrl, media);

        int foundHere = media.size() - beforeCount;
        if (foundHere > 0) {
            notify(statusListener, "gui.web_scanner.status.found_on_page", foundHere, cleanUrl);
        }

        if (depth < maxDepth) {
            Elements pageLinks = doc.select("a[href]");
            log.debug("Found {} sub-links to potentially scan at depth {}", pageLinks.size(), depth);

            for (Element link : pageLinks) {
                if (cancelHook != null && cancelHook.get()) {
                    log.debug("Scan aborted during sub-link iteration at depth {}", depth);
                    break;
                }

                String nextUrl = link.absUrl("href");
                if (isScannablePage(nextUrl)) {
                    scanRecursive(nextUrl, null, depth + 1, maxDepth,
                        strictHost, baseHost, visited, media, cancelHook, statusListener);
                }
            }
        }
    }

    private void collectAttributeLinks(Document doc, Set<String> found) {
        for (String[] source : ATTRIBUTE_SOURCES) {
            String tag = source[0];
            String attr = source[1];

            Elements elements = doc.select(tag + "[" + attr + "]");
            for (Element element : elements) {
                considerUrl(element.absUrl(attr), found);
            }
        }
    }

    private void collectSrcSetLinks(Document doc, String pageUrl, Set<String> found) {
        for (String[] source : SRCSET_SOURCES) {
            String tag = source[0];
            String attr = source[1];

            Elements elements = doc.select(tag + "[" + attr + "]");
            for (Element element : elements) {
                for (String candidate : parseSrcSet(element.attr(attr), pageUrl)) {
                    considerUrl(candidate, found);
                }
            }
        }
    }

    private void collectMetaLinks(Document doc, String pageUrl, Set<String> found) {
        for (String property : OG_META_PROPERTIES) {
            Elements metas = doc.select(
                "meta[property=" + property + "], meta[name=" + property + "]");

            for (Element meta : metas) {
                considerUrl(resolve(pageUrl, meta.attr("content")), found);
            }
        }
    }

    private void collectInlineStyleLinks(Document doc, String pageUrl, Set<String> found) {
        for (Element element : doc.select("[style]")) {
            extractCssUrls(element.attr("style"), pageUrl, found);
        }
    }

    private void collectStyleBlockLinks(Document doc, String pageUrl, Set<String> found) {
        for (Element styleTag : doc.select("style")) {
            extractCssUrls(styleTag.data(), pageUrl, found);
        }
    }

    private void extractCssUrls(@Nullable String css, String pageUrl, Set<String> found) {
        if (css == null || css.isEmpty()) {
            return;
        }

        Matcher matcher = CSS_URL_PATTERN.matcher(css);
        while (matcher.find()) {
            considerUrl(resolve(pageUrl, matcher.group(1)), found);
        }
    }

    private void considerUrl(@Nullable String candidate, Set<String> found) {
        if (candidate == null || candidate.isEmpty() || !isHttpUrl(candidate)) {
            return;
        }

        // MediaWiki description pages - ignore
        if (candidate.contains("/File:") || candidate.contains("/Image:")) {
            return;
        }

        String extension = getExtension(candidate);
        if (extensions.isDownloadableExtension(extension) || extensions.isMultiPartArchiveUrl(candidate)) {
            found.add(candidate);
        }
    }

    private boolean isScannablePage(String url) {
        if (!isHttpUrl(url)) {
            return false;
        }

        String ext = getExtension(url);
        if (ext == null) {
            return true;
        }

        return !extensions.isDownloadableExtension(ext);
    }

    private List<String> parseSrcSet(@Nullable String srcset, String baseUrl) {
        List<String> results = new ArrayList<>();
        if (srcset == null || srcset.isBlank()) {
            return results;
        }

        for (String part : srcset.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] pieces = trimmed.split("\\s+");
            if (pieces.length > 0) {
                String resolved = resolve(baseUrl, pieces[0]);
                if (resolved != null) {
                    results.add(resolved);
                }
            }
        }

        return results;
    }

    private static String readBounded(InputStream inputStream, long maxBytes) throws IOException {
        try (inputStream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    log.warn("Page exceeded {} byte limit, truncating scan input", maxBytes);
                    int overflow = (int)(total - maxBytes);
                    out.write(buffer, 0, read - overflow);
                    break;
                }

                out.write(buffer, 0, read);
            }

            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static void notify(@Nullable ScanStatusListener listener, String translationKey, Object... args) {
        if (listener != null) {
            listener.onStatus(translationKey, args);
        }
    }

    @FunctionalInterface
    public interface ScanStatusListener {

        void onStatus(String translationKey, Object... args);
    }
}
