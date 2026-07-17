package net.brlns.gdownloader.downloader.hosts.impl;

import jakarta.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.hosts.HostResolverContext;
import net.brlns.gdownloader.downloader.hosts.HostResolverException;
import net.brlns.gdownloader.downloader.hosts.ResolvedFile;
import net.brlns.gdownloader.downloader.hosts.RetryLaterException;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: i10n
@Slf4j
public class SunoResolver extends AbstractHostResolver {

    private static final Pattern SONG_ID_PATTERN = Pattern.compile(
        "suno\\.(?:com|ai)/song/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private static final Pattern SHORT_URL_PATTERN = Pattern.compile(
        "suno\\.(?:com|ai)/s/[a-zA-Z0-9]+");

    private static final String CDN_URL_TEMPLATE = "https://cdn1.suno.ai/%s.mp3";
    private static final String REFERER = "https://suno.com/";

    @Override
    public String getId() {
        return "suno";
    }

    @Override
    public String getDisplayName() {
        return "Suno";
    }

    @Override
    public boolean isEnabled(HostResolverContext context) {
        return true;
    }

    @Override
    public boolean canHandle(String url) {
        return SONG_ID_PATTERN.matcher(url).find() || SHORT_URL_PATTERN.matcher(url).find();
    }

    @Override
    public List<ResolvedFile> resolve(String url, HostResolverContext context) throws HostResolverException {
        ensureNotCancelled(context);

        context.notifyStatus("gui.host_resolver.status.resolving", getDisplayName());

        Duration timeout = context.getRequestTimeout() != null
            ? context.getRequestTimeout() : Duration.ofSeconds(15);

        if (SHORT_URL_PATTERN.matcher(url).find()) {
            url = resolveRedirectTarget(context, URI.create(url), timeout).toString();
        }

        String songId = extractSongId(url);
        if (songId == null) {
            throw new HostResolverException(
                "Invalid Suno URL", false);
        }

        String cdnUrl = String.format(CDN_URL_TEMPLATE, songId);

        int status = headStatus(context, URI.create(cdnUrl), timeout,
            Map.of("Referer", REFERER));

        if (status == 404) {
            throw new RetryLaterException(Duration.ofSeconds(30),
                "File not yet available");
        }

        if (status == 403) {
            throw new HostResolverException(
                "File not found", false);
        }

        if (status / 100 != 2) {
            throw new HostResolverException(
                "Suno CDN returned HTTP " + status, status == 429 || status >= 500);
        }

        return List.of(ResolvedFile.builder()
            .url(cdnUrl)
            .fileName(songId + ".mp3")
            .referer(REFERER)
            .build());
    }

    @Nullable
    private static String extractSongId(String url) {
        Matcher matcher = SONG_ID_PATTERN.matcher(url);

        return matcher.find() ? matcher.group(1) : null;
    }
}
