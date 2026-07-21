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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.hosts.HostResolverContext;
import net.brlns.gdownloader.downloader.hosts.HostResolverException;
import net.brlns.gdownloader.downloader.hosts.IHostResolver;
import net.brlns.gdownloader.util.URLUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractHostResolver implements IHostResolver {

    private static final HttpResponse.BodyHandler<String> HTML_SNIFFING_HANDLER = responseInfo -> {
        String contentType = responseInfo.headers().firstValue("Content-Type").orElse("");
        String lower = contentType.toLowerCase(Locale.ROOT);

        if (lower.contains("text/html") || lower.contains("text/plain") || lower.isEmpty()) {
            return HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        }

        return HttpResponse.BodySubscribers.replacing("");
    };

    @Override
    public String getDisplayName() {
        return getId();
    }

    protected HttpResponse<String> get(HostResolverContext context, URI uri, Duration timeout,
        Map<String, String> headers) throws HostResolverException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("User-Agent", URLUtils.getGlobalUserAgent())
            .GET();

        headers.forEach(builder::header);

        return send(context, builder.build());
    }

    protected HttpResponse<String> postJson(HostResolverContext context, URI uri, Duration timeout,
        String jsonBody, Map<String, String> headers) throws HostResolverException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("User-Agent", URLUtils.getGlobalUserAgent())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        headers.forEach(builder::header);

        return send(context, builder.build());
    }

    protected HttpResponse<String> postEmpty(HostResolverContext context, URI uri, Duration timeout,
        Map<String, String> headers) throws HostResolverException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("User-Agent", URLUtils.getGlobalUserAgent())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.noBody());

        headers.forEach(builder::header);

        return send(context, builder.build());
    }

    protected HttpResponse<String> send(HostResolverContext context, HttpRequest request) throws HostResolverException {
        try {
            return context.getHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new HostResolverException(
                "Network error contacting " + request.uri().getHost() + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new HostResolverException(
                "Interrupted while contacting " + request.uri().getHost(), true, e);
        }
    }

    protected HttpRequest.Builder newRequestBuilder(URI uri, Duration timeout) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("User-Agent", URLUtils.getGlobalUserAgent());
    }

    protected HttpResponse<String> sendSniffingRequest(HostResolverContext context, HttpRequest request) throws HostResolverException {
        try {
            return context.getHttpClient().send(request, HTML_SNIFFING_HANDLER);
        } catch (IOException e) {
            throw new HostResolverException(
                "Network error contacting " + request.uri().getHost() + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new HostResolverException(
                "Interrupted while contacting " + request.uri().getHost(), true, e);
        }
    }

    protected int headStatus(HostResolverContext context, URI uri, Duration timeout,
        Map<String, String> headers) throws HostResolverException {

        HttpRequest.Builder builder = newRequestBuilder(uri, timeout)
            .method("HEAD", HttpRequest.BodyPublishers.noBody());

        headers.forEach(builder::header);

        HttpRequest request = builder.build();

        try {
            return context.getHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new HostResolverException(
                "Network error contacting " + uri.getHost() + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new HostResolverException(
                "Interrupted while contacting " + uri.getHost(), true, e);
        }
    }

    protected URI resolveRedirectTarget(HostResolverContext context, URI uri, Duration timeout) throws HostResolverException {
        HttpRequest request = newRequestBuilder(uri, timeout)
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();

        try {
            return context.getHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).uri();
        } catch (IOException e) {
            throw new HostResolverException(
                "Network error resolving redirect for " + uri.getHost() + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new HostResolverException(
                "Interrupted while resolving redirect for " + uri.getHost(), true, e);
        }
    }

    protected void waitCancellable(HostResolverContext context, long waitSeconds, String statusKey)
        throws HostResolverException {

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(waitSeconds).toMillis();
        long lastStatusUpdate = 0;

        while (System.currentTimeMillis() < deadline) {
            ensureNotCancelled(context);

            long now = System.currentTimeMillis();
            if (now - lastStatusUpdate > 2000) {
                long remainingSecs = Math.max(0, (deadline - now) / 1000);
                context.notifyStatus(statusKey, remainingSecs);

                lastStatusUpdate = now;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new HostResolverException(
                    "Interrupted while waiting", true, e);
            }
        }
    }

    protected JsonNode parseJson(HttpResponse<String> response) throws HostResolverException {
        try {
            return GDownloader.OBJECT_MAPPER.readTree(response.body());
        } catch (Exception e) {
            throw new HostResolverException(
                "Failed to parse response from " + response.uri().getHost() + " as JSON: " + e.getMessage(), false, e);
        }
    }

    protected void requireSuccess(HttpResponse<?> response, String context) throws HostResolverException {
        if (response.statusCode() / 100 != 2) {
            throw new HostResolverException(
                context + " failed with HTTP " + response.statusCode(),
                response.statusCode() == 429 || response.statusCode() >= 500);
        }
    }

    protected void ensureNotCancelled(HostResolverContext context) throws HostResolverException {
        if (context.isCancelled()) {
            throw new HostResolverException(
                "Cancelled", false);
        }
    }

    @Nullable
    protected static String text(@Nullable JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
