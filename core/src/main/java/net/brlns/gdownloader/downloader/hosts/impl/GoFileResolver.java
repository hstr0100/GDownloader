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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.hosts.HostResolverContext;
import net.brlns.gdownloader.downloader.hosts.HostResolverException;
import net.brlns.gdownloader.downloader.hosts.ResolvedFile;
import net.brlns.gdownloader.downloader.hosts.RetryLaterException;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;

/**
 * Resolves gofile.io share links (single files or whole folders, recursively)
 * into direct CDN download links.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class GoFileResolver extends AbstractHostResolver {

    private static final Pattern URL_PATTERN
        = Pattern.compile("gofile\\.io/d/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ILLEGAL_FILENAME_CHARS
        = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private static final String ACCOUNTS_ENDPOINT = "https://api.gofile.io/accounts";
    private static final String CONTENTS_ENDPOINT_TEMPLATE = "https://api.gofile.io/contents/%s"
        + "?contentFilter=&page=1&pageSize=1000&sortField=createTime&sortDirection=-1";
    private static final String REFERER = "https://gofile.io/";
    private static final String LANGUAGE = "en-US";

    // Salt extracted from gofile.io/dist/js/wt.obf.js - this will eventually change.
    private static final String WT_SALT = "9844d94d963d30";
    private static final long WT_WINDOW_SECONDS = 14400; // 4-hour

    private final AtomicReference<String> accountToken = new AtomicReference<>();

    @Override
    public String getId() {
        return "gofile";
    }

    @Override
    public String getDisplayName() {
        return "GoFile";
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

        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new HostResolverException(
                "Invalid GoFile URL", false);
        }

        String rootContentId = matcher.group(1);
        String token = ensureAccountToken(context);

        List<ResolvedFile> files = new ArrayList<>();
        collectFiles(rootContentId, context, token, context.getPassword(), "", files);

        if (files.isEmpty()) {
            throw new HostResolverException(
                "No downloadable content found in this GoFile link", false);
        }

        return files;
    }

    private void collectFiles(String contentId, HostResolverContext context, String token,
        @Nullable String password, String parentPath, List<ResolvedFile> out) throws HostResolverException {

        ensureNotCancelled(context);

        JsonNode data = fetchContentData(context, contentId, token, password);

        String passwordStatus = text(data.get("passwordStatus"));
        if (passwordStatus != null && !"passwordOk".equals(passwordStatus)) {
            throw new HostResolverException(
                "Invalid or missing password", false);
        }

        if ("folder".equals(text(data.get("type")))) {
            String folderPath = joinPath(parentPath, text(data.get("name")));

            JsonNode children = data.get("children");
            if (children == null) {
                return;
            }

            for (Map.Entry<String, JsonNode> child : children.properties()) {
                ensureNotCancelled(context);

                JsonNode childNode = child.getValue();

                if ("folder".equals(text(childNode.get("type")))) {
                    collectFiles(child.getKey(), context, token, password, folderPath, out);
                } else {
                    addFile(childNode, folderPath, token, out);
                }
            }
        } else {
            addFile(data, parentPath, token, out);
        }
    }

    private void addFile(JsonNode fileNode, String parentPath, String token, List<ResolvedFile> out) {
        String name = text(fileNode.get("name"));
        String link = text(fileNode.get("link"));

        if (name == null || link == null) {
            log.warn("Skipping malformed GoFile link");

            return;
        }

        out.add(ResolvedFile.builder()
            .url(link)
            .fileName(joinPath(parentPath, name))
            .referer(REFERER)
            .extraHeader("Cookie", "accountToken=" + token)
            .build());
    }

    private JsonNode fetchContentData(HostResolverContext context, String contentId, String token,
        @Nullable String password) throws HostResolverException {

        Duration timeout = context.getRequestTimeout() != null
            ? context.getRequestTimeout() : Duration.ofSeconds(15);
        String hashedPassword = password != null && !password.isEmpty()
            ? StringUtils.calculateSHA256(password) : null;

        for (int windowOffset = 0; windowOffset >= -1; windowOffset--) {
            HttpResponse<String> response = sendContentsRequest(
                context, contentId, token, hashedPassword, timeout, windowOffset);

            requireSuccess(response, "GoFile content resolve");

            JsonNode json = parseJson(response);
            String status = text(json.get("status"));

            if ("ok".equals(status)) {
                return json.get("data");
            }

            if ("error-notPremium".equals(status) && windowOffset == 0) {
                continue;
            }

            throw toResolverException(status);
        }

        throw new HostResolverException(
            "Failed to retrieve GoFile content", true);
    }

    private HttpResponse<String> sendContentsRequest(HostResolverContext context, String contentId,
        String token, @Nullable String hashedPassword, Duration timeout, int windowOffset)
        throws HostResolverException {

        String query = CONTENTS_ENDPOINT_TEMPLATE.formatted(contentId)
            + (hashedPassword != null ? "&password=" + hashedPassword : "");

        HttpRequest request = gofileRequestBuilder(URI.create(query), timeout)
            .header("Authorization", "Bearer " + token)
            .header("X-Website-Token", generateWebsiteToken(token, windowOffset))
            .header("X-BL", LANGUAGE)
            .header("Accept", "*/*")
            .header("Origin", "https://gofile.io")
            .header("Referer", REFERER)
            .GET()
            .build();

        return send(context, request);
    }

    private String ensureAccountToken(HostResolverContext context) throws HostResolverException {
        String cached = accountToken.get();
        if (cached != null) {
            return cached;
        }

        Duration timeout = context.getRequestTimeout() != null
            ? context.getRequestTimeout() : Duration.ofSeconds(15);

        HttpRequest request = gofileRequestBuilder(URI.create(ACCOUNTS_ENDPOINT), timeout)
            .header("Origin", "https://gofile.io")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> response = send(context, request);
        requireSuccess(response, "GoFile account creation");

        JsonNode json = parseJson(response);
        if (!"ok".equals(text(json.get("status")))) {
            throw new HostResolverException(
                "Failed to create a GoFile guest account", true);
        }

        String token = text(json.get("data").get("token"));
        if (token == null || token.isBlank()) {
            throw new HostResolverException(
                "GoFile did not return a valid account token", true);
        }

        accountToken.compareAndSet(null, token);

        return accountToken.get();
    }

    private HttpRequest.Builder gofileRequestBuilder(URI uri, Duration timeout) {
        return newRequestBuilder(uri, timeout)
            .setHeader("User-Agent", URLUtils.getGlobalUserAgent());
    }

    private static String generateWebsiteToken(String accountTokenValue, int windowOffset) {
        long window = Math.floorDiv(Instant.now().getEpochSecond(), WT_WINDOW_SECONDS) + windowOffset;

        String raw = URLUtils.getGlobalUserAgent()
            + "::" + LANGUAGE
            + "::" + accountTokenValue
            + "::" + window
            + "::" + WT_SALT;

        return StringUtils.calculateSHA256(raw);
    }

    private static HostResolverException toResolverException(@Nullable String status) {
        if (status == null) {
            return new HostResolverException(
                "GoFile returned an unrecognized response", true);
        }

        return switch (status) {
            case "error-notFound" ->
                new HostResolverException(
                "Content not found.", false);
            case "error-rateLimit" ->
                new RetryLaterException(Duration.ofSeconds(30),
                "GoFile rate limit reached");
            case "error-notPremium" ->
                new HostResolverException(
                "GoFile rejected the website token", false);
            case "error-passwordRequired", "error-passwordWrong" ->
                new HostResolverException(
                "Invalid or missing password", false);
            default -> {
                log.warn("Unrecognized GoFile status: {}", status);

                yield new HostResolverException("GoFile API error: " + status, true);
            }
        };
    }

    private static String joinPath(String parentPath, @Nullable String name) {
        String sanitized = sanitizeSegment(name);

        return parentPath.isEmpty() ? sanitized : parentPath + "/" + sanitized;
    }

    private static String sanitizeSegment(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "_";
        }

        String sanitized = ILLEGAL_FILENAME_CHARS.matcher(name).replaceAll("_").trim();
        sanitized = sanitized.replaceAll("[.\\s]+$", "");

        return sanitized.isBlank() ? "_" : sanitized;
    }
}
