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

        if (response.statusCode() == 403 || response.statusCode() == 503) {
            log.warn("1Fichier returned HTTP {}", response.statusCode());

            throw new HostResolverException(
                "Link generation failed", true);
        }

        if (response.statusCode() == 404) {
            throw new HostResolverException(
                "File not found", false);
        }

        String htmlBody = response.body();
        Document doc = Jsoup.parse(htmlBody);

        if (doc.title().toLowerCase(Locale.ROOT).contains("just a moment")) {
            log.warn("1Fichier is protected by Cloudflare.");

            throw new HostResolverException(
                "Link generation failed", true);
        }

        Element dlLinkElement = doc.selectFirst("a.ok.btn-general.btn-orange");
        if (dlLinkElement == null) {
            dlLinkElement = doc.selectFirst("a.btn-orange");
        }

        if (dlLinkElement == null) {
            dlLinkElement = doc.selectFirst("a[href*=.1fichier.com/]");
        }

        if (dlLinkElement != null && dlLinkElement.hasAttr("href")) {
            String dlUrl = dlLinkElement.attr("href");

            if (!dlUrl.equals(cleanUrl) && dlUrl.startsWith("http")) {
                context.notifyStatus("gui.host_resolver.status.resolved", dlUrl);

                return List.of(ResolvedFile.builder()
                    .url(dlUrl)
                    .referer(cleanUrl)
                    .forceSingleChunk(true)
                    .build());
            }
        }

        Elements ctWarns = doc.select("div.ct_warn");

        if (ctWarns.isEmpty()) {
            log.warn("1Fichier parser failed. No warning divs and no CDN link found. HTTP Status: {}, HTML Length: {}",
                response.statusCode(), htmlBody.length());

            throw new HostResolverException(
                "Link generation failed", false);
        }

        for (Element warn : ctWarns) {
            String text = warn.text().toLowerCase(Locale.ROOT);

            if (text.contains("you must wait")) {
                int minutes = extractNumber(text);

                log.info("1Fichier limit detected: {} minutes", minutes);

                if (minutes <= 0) {
                    minutes = 1;
                }

                // TODO: retry-later scheduler, we can wait for 10 minutes, but not for 10 hours.
                throw new RetryLaterException(Duration.ofMinutes(minutes),
                    "Wait limit reached");
            }

            if (text.contains("temporarily limited")
                || text.contains("high demand")
                || text.contains("guest slots")) {
                log.info("1Fichier limit detected: high demand / no free guest slots");

                // We can pester them for this one, 1 minute should be fine.
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

        log.warn("1Fichier returned unknown warning state: {}", ctWarns.text());

        throw new RetryLaterException(Duration.ofMinutes(5),
            "Unrecognized site response");
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
