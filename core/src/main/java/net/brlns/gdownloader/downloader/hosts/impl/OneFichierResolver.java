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
package net.brlns.gdownloader.downloader.hosts.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.hosts.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Resolves 1fichier.com landing pages into direct CDN download links.
 *
 * This implementation is not intended to be maintained and is expected to eventually break.
 * It was built to test our custom host resolver implementation. (direct-link extractors)
 *
 * Referenced from various online sources.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class OneFichierResolver extends AbstractHostResolver {

    private static final Pattern URL_PATTERN = Pattern.compile("^(?:https?://)?[^/]*1fichier\\.com/\\?.+");
    private static final Pattern ID_PATTERN = Pattern.compile("1fichier\\.com/\\?([a-zA-Z0-9]+)");
    private static final Pattern AF_PATTERN = Pattern.compile("af=([0-9]+)");
    private static final Pattern COUNTDOWN_PATTERN = Pattern.compile("var\\s+ct\\s*=\\s*(\\d+)");
    private static final Pattern CDN_LINK_PATTERN = Pattern.compile(
        "^https?://(?!img\\.|www\\.)[a-z0-9-]+\\.1fichier\\.com/[^?#]+$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_INLINE_WAIT_SECONDS = 180;

    @Override
    public String getId() {
        return "1fichier";
    }

    @Override
    public String getDisplayName() {
        return "1fichier";
    }

    @Override
    public boolean isEnabled(HostResolverContext context) {
        return true;
    }

    @Override
    public boolean canHandle(String url) {
        return URL_PATTERN.matcher(url).find();
    }

    @Override
    public List<ResolvedFile> resolve(String url, HostResolverContext context) throws HostResolverException {
        ensureNotCancelled(context);

        context.notifyStatus("gui.host_resolver.status.resolving", getDisplayName());

        String targetUrl = url;
        String password = null;

        // inline password if present (link::password)
        if (url.contains("::")) {
            String[] parts = url.split("::");
            if (parts.length > 1) {
                password = parts[parts.length - 1];
                targetUrl = parts[parts.length - 2];
            }
        }

        if (password == null) {
            password = context.getPassword();
        }

        String cleanUrl = getCleanUrl(targetUrl);
        URI uri = URI.create(cleanUrl);

        String afCookieValue = "1";
        Matcher afMatcher = AF_PATTERN.matcher(targetUrl);
        if (afMatcher.find()) {
            afCookieValue = afMatcher.group(1);
        }

        Duration timeout = context.getRequestTimeout() != null
            ? context.getRequestTimeout() : Duration.ofSeconds(15);

        HttpResponse<String> response = postPage(context, uri, cleanUrl, afCookieValue, password, timeout);
        Document doc = parseResponse(response);

        String dlUrl = findDownloadUrl(doc, cleanUrl);

        if (dlUrl == null) {
            throwIfLimited(doc, response);

            Element form = doc.selectFirst("form#f1");
            Element startButton = doc.selectFirst("#dlw");

            if (form != null && startButton != null) {
                int waitSeconds = extractCountdown(response.body());

                if (waitSeconds > MAX_INLINE_WAIT_SECONDS) {
                    throw new RetryLaterException(Duration.ofSeconds(waitSeconds),
                        "Wait limit reached");
                }

                waitCancellable(context, waitSeconds + 1,
                    "gui.direct_http.download_status.retrying_in");

                ensureNotCancelled(context);

                context.notifyStatus("gui.host_resolver.status.resolving", getDisplayName());

                response = postPage(context, uri, cleanUrl, afCookieValue, password, timeout);
                doc = parseResponse(response);

                dlUrl = findDownloadUrl(doc, cleanUrl);

                if (dlUrl == null) {
                    throwIfLimited(doc, response);
                }
            }
        }

        if (dlUrl != null) {
            context.notifyStatus("gui.host_resolver.status.resolved", dlUrl);

            return List.of(ResolvedFile.builder()
                .url(dlUrl)
                .referer(cleanUrl)
                .forceSingleChunk(true)
                .build());
        }

        log.warn("1Fichier parser failed. No warnings, countdown form or CDN link found. HTTP Status: {}, HTML Length: {}",
            response.statusCode(), response.body().length());

        throw new HostResolverException(
            "Link generation failed", false);
    }

    private HttpResponse<String> postPage(HostResolverContext context, URI uri, String cleanUrl,
        String afCookieValue, String password, Duration timeout) throws HostResolverException {

        HttpRequest.Builder builder = newRequestBuilder(uri, timeout)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Cookie", "AF=" + afCookieValue + "; domain_lang=en")
            .header("Referer", cleanUrl)
            .header("Content-Type", "application/x-www-form-urlencoded");

        String body = "";
        if (password != null && !password.isEmpty()) {
            body = "pass=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        }
        builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        HttpResponse<String> response = sendSniffingRequest(context, builder.build());

        throwIfRateLimited(response, getDisplayName(), Duration.ofMinutes(1));

        if (response.statusCode() == 403 || response.statusCode() == 503) {
            log.warn("1Fichier returned HTTP {}", response.statusCode());

            throw new HostResolverException(
                "Link generation failed", true);
        }

        if (response.statusCode() == 404) {
            throw new HostResolverException(
                "File not found", false);
        }

        return response;
    }

    private Document parseResponse(HttpResponse<String> response) throws HostResolverException {
        Document doc = Jsoup.parse(response.body());

        if (doc.title().toLowerCase(Locale.ROOT).contains("just a moment")) {
            log.warn("1Fichier is protected by Cloudflare.");

            throw new HostResolverException(
                "Link generation failed", true);
        }

        return doc;
    }

    private String findDownloadUrl(Document doc, String cleanUrl) {
        for (Element anchor : doc.select("a[href]")) {
            String href = anchor.attr("href").trim();

            if (!href.equals(cleanUrl) && CDN_LINK_PATTERN.matcher(href).matches()) {
                return href;
            }
        }

        return null;
    }

    private int extractCountdown(String html) {
        Matcher matcher = COUNTDOWN_PATTERN.matcher(html);
        if (matcher.find()) {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        }

        return 60;
    }

    private void throwIfLimited(Document doc, HttpResponse<String> response) throws HostResolverException {
        Elements notices = doc.select("div.ct_warn, div.notice, div.alert, div.warn");

        for (Element notice : notices) {
            String text = notice.text().toLowerCase(Locale.ROOT);

            if (text.contains("you must wait")) {
                int minutes = extractNumber(text);

                log.info("1Fichier limit detected: {} minutes", minutes);

                if (minutes <= 0) {
                    minutes = 1;
                }

                throw new RetryLaterException(Duration.ofMinutes(minutes),
                    "Wait limit reached");
            }

            if (text.contains("temporarily limited")
                || text.contains("high demand")
                || text.contains("guest slots")
                || text.contains("slots are currently in use")
                || text.contains("no free slot")) {
                log.info("1Fichier limit detected: high demand / no free guest slots");

                throw new RetryLaterException(Duration.ofMinutes(1),
                    "Temporarily limited due to high demand");
            }

            if (text.contains("one file at a time")) {
                log.info("1Fichier limit detected: cannot download more than one file at a time");

                throw new RetryLaterException(Duration.ofMinutes(5),
                    "Cannot download more than one file at a time");
            }

            if (text.contains("more than 10 files")) {
                log.info("1Fichier limit detected: cannot download more than 10 files per day");

                throw new RetryLaterException(Duration.ofHours(1),
                    "Cannot download more than 10 files per day");
            }

            if (text.contains("protect access")
                || text.contains("bad password")) {
                throw new HostResolverException(
                    "Invalid or missing password", false);
            }
        }

        if (!notices.isEmpty()) {
            log.warn("1Fichier returned unknown warning state: {}", notices.text());

            throw new RetryLaterException(Duration.ofMinutes(5),
                "Unrecognized site response");
        }
    }

    private String getCleanUrl(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return "https://1fichier.com/?" + matcher.group(1);
        }

        return url;
    }

    private int extractNumber(String text) {
        Matcher anchored = Pattern.compile("(\\d+)\\s*minute").matcher(text);
        if (anchored.find()) {
            return Integer.parseInt(anchored.group(1));
        }

        Matcher fallback = Pattern.compile("\\d+").matcher(text);

        return fallback.find() ? Integer.parseInt(fallback.group()) : -1;
    }
}
