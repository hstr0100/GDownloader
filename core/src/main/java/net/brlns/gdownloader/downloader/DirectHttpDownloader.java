/*
 * Copyright (C) 2024 hstr0100
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
package net.brlns.gdownloader.downloader;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.webscanner.WebScanner;
import net.brlns.gdownloader.downloader.webscanner.WebScannerExtensions;
import net.brlns.gdownloader.settings.downloader.DirectHttpSettings;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;

import static java.net.HttpURLConnection.*;
import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.DIRECT;
import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.util.FileUtils.relativize;

// TODO: Resume chunked
// TODO: ftp
/**
 * Example of a supported URL: https://hiddenpalace.org/Resident_Evil_(Prototype)
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DirectHttpDownloader extends AbstractDownloader {

    private static final String ACCEPT_DOCUMENT = "text/html,"
        + "application/xhtml+xml,"
        + "application/xml;q=0.9,"
        + "image/avif,"
        + "image/webp,"
        + "image/apng,"
        + "*/*;q=0.8,"
        + "application/signed-exchange;v=b3;q=0.7";

    private static final String ACCEPT_ANY = "*/*";

    private static final String PREFIX = "[direct-http] ";

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_DRAIN_BYTES = 65536;

    private static final long BASE_BACKOFF_MILLIS = 1000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;
    private static final long MIN_CHUNK_SIZE_BYTES = 2L * 1024 * 1024;// 2MB
    private static final long MAX_RETRY_AFTER_MILLIS = 300000L;
    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 30000;

    private final ConcurrentHashMap<String, Semaphore> hostConnectionLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> hostRateLimitedUntil = new ConcurrentHashMap<>();

    private final ExecutorService chunkThreadPool = Executors.newVirtualThreadPerTaskExecutor();

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    public DirectHttpDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public DirectHttpSettings settings() {
        return main.getConfig().getDirectHttpSettings();
    }

    @Override
    public boolean isEnabled() {
        return settings().isEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.DIRECT_HTTP;
    }

    @Override
    public boolean isMainDownloader() {
        return false;
    }

    @Override
    public String getDefaultOutputSubdirectory() {
        return "HTTP";
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.emptyList();
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        // Not implemented
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        if (!isEnabled()) {
            return false;
        }

        if (!URLUtils.isHttpUrl(inputUrl)) {
            return false;
        }

        return !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/"));
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        // TODO
        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        if (!settings().isEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        boolean success = false;
        boolean unsupportedUrl = false;
        String lastOutput = "";

        AtomicLong lastProgressUpdateNanos = new AtomicLong(0);

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != DIRECT || !settings().isEnabled()) {
                continue;
            }

            entry.setCurrentDownloadType(type);

            try {
                success = downloadFile(entry, (percent, total, speed, remainingTime, chunkCount) -> {
                    double roundedPercent = Math.round(percent * 10) / 10.0;// Strip out unecessary precision

                    double lastPercentage = entry.getMediaCard().getPercentage();
                    boolean reachedCompletion = roundedPercent >= 100.0;

                    long now = System.nanoTime();
                    boolean intervalElapsed = (now - lastProgressUpdateNanos.get()) >= 400_000_000;

                    if (roundedPercent < lastPercentage || (!intervalElapsed && !reachedCompletion)) {
                        return;
                    }

                    lastProgressUpdateNanos.set(now);
                    entry.getMediaCard().setPercentage(roundedPercent);

                    String fmPercent = StringUtils.formatPercent(percent);
                    String fmTotal = StringUtils.getHumanReadableFileSize(total);
                    String fmSpeed = StringUtils.getHumanReadableFileSize(speed);
                    String fmRemaingTime = StringUtils.formatETATime(remainingTime);

                    entry.updateStatus(DownloadStatusEnum.DOWNLOADING,
                        l10n("gui.direct_http.download_status.downloading_progress",
                            fmPercent, fmTotal, fmSpeed, fmRemaingTime, chunkCount), false);
                });

                lastOutput = PREFIX + l10n("gui.direct_http.download_status.download_complete");
                entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput);
            } catch (UnsupportedURLException e) {
                lastOutput = PREFIX + e.getMessage();

                unsupportedUrl = true;
                success = false;
            } catch (Exception e) {
                lastOutput = PREFIX + e.getMessage();
                success = false;
            } finally {
                entry.getDownloadStarted().set(false);
            }

            log.debug(lastOutput);

            if (!isAlive(entry)) {
                return new DownloadResult(FLAG_STOPPED);
            }

            if (!success) {
                if (unsupportedUrl) {
                    return new DownloadResult(FLAG_UNSUPPORTED, lastOutput);
                }

                return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
            } else {
                if (settings().isMediaTranscoding()) {
                    DownloadResult transcodeResult = transcodeMediaFiles(entry);

                    if (main.getConfig().isFailDownloadsOnTranscodingFailures()
                        && !FLAG_SUCCESS.isSet(transcodeResult.getFlags())) {
                        return transcodeResult;
                    }
                }
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected void processMediaFiles(QueueEntry entry) {
        File finalPath = resolveOutputDirectory(entry);

        File tmpPath = entry.getTmpDirectory();
        Path deepestDir = null;
        int maxDepth = -1;

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .filter(path -> !path.equals(tmpPath.toPath()))
                .collect(Collectors.toList());

            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    Path targetPath = relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath);
                        int depth = targetPath.getNameCount();
                        if (depth > maxDepth) {
                            maxDepth = depth;
                            deepestDir = targetPath;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to create directory: {}", targetPath, e);
                    }
                }
            }

            for (Path path : paths) {
                if (!Files.isDirectory(path)) {
                    Path targetPath = relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);

                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        updateFileTimes(entry, targetPath);

                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                entry.getFinalMediaFiles().add(deepestDir.toFile());
            }
        } catch (IOException e) {
            log.error("Failed to process media files", e);
        }
    }

    private boolean isAlive(QueueEntry entry) {
        return manager.isRunning() && !entry.getCancelHook().get();
    }

    private void applyBrowserHeaders(HttpURLConnection connection, String accept, @Nullable String referer) {
        connection.setRequestProperty("User-Agent", URLUtils.getGlobalUserAgent());
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", URLUtils.resolveFetchSite(connection.getURL(), referer));

        if (referer != null) {
            connection.setRequestProperty("Referer", referer);
        }
    }

    @Nullable
    private Pair<HttpURLConnection, Integer> openConnection(URL fileUrl, String requestType) {
        return openConnection(fileUrl, requestType, null);
    }

    @Nullable
    private Pair<HttpURLConnection, Integer> openConnection(URL fileUrl, String requestType, @Nullable String referer) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)fileUrl.openConnection(getProxySettings());
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod(requestType);
            applyBrowserHeaders(connection, ACCEPT_DOCUMENT, referer);

            int responseCode = connection.getResponseCode();
            if (responseCode != HTTP_OK && responseCode != HTTP_PARTIAL) {
                connection.disconnect();

                throw new IOException(
                    l10n("gui.direct_http.download_status.error.server_http_error", responseCode));
            }

            return new Pair<>(connection, responseCode);
        } catch (Exception e) {
            log.error("Request {} failed for {}: {}", requestType, fileUrl, e.getMessage());

            if (connection != null) {
                connection.disconnect();
            }

            return null;
        }
    }

    private boolean downloadFile(QueueEntry queueEntry, ProgressUpdater progressCallback) throws Exception {
        URL fileUrl = new URI(queueEntry.getUrl()).toURL();
        Pair<HttpURLConnection, Integer> connectionPair = null;

        int attempts = 0;
        while (attempts < 3 && isAlive(queueEntry)) {
            connectionPair = openConnection(fileUrl, "HEAD");
            if (connectionPair == null) {
                connectionPair = openConnection(fileUrl, "GET");
            }

            if (connectionPair != null) {
                break;
            }

            attempts++;
            if (attempts < 3) {
                waitCancellable(queueEntry, 1500, () -> isAlive(queueEntry),
                    "gui.direct_http.download_status.waiting_resolve_host");
            }
        }

        if (connectionPair == null) {
            throw new IOException(
                l10n("gui.direct_http.download_status.error.connection_failed_retries", fileUrl));
        }

        if (connectionPair.getValue() != HTTP_OK) {
            throw new IOException(
                l10n("gui.direct_http.download_status.error.server_http_error", connectionPair.getValue()));
        }

        HttpURLConnection connection = connectionPair.getKey();

        String mimeType = connection.getContentType();
        log.debug("MIME Type: {}", mimeType);

        if (isHtmlContent(mimeType)) {
            closeQuietly(connection);

            if (!settings().isWebScannerEnabled()) {
                throw new UnsupportedURLException(l10n("gui.direct_http.download_status.error.unsupported_url", fileUrl));
            }

            return scanAndDownloadMedia(queueEntry, fileUrl, progressCallback);
        }

        return downloadResolvedFile(queueEntry, fileUrl, connection, progressCallback);
    }

    private boolean isHtmlContent(@Nullable String mimeType) {
        if (mimeType == null) {
            return false;
        }

        String lower = mimeType.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") || lower.contains("application/xhtml+xml");
    }

    private boolean scanAndDownloadMedia(QueueEntry queueEntry, URL pageUrl,
        ProgressUpdater progressCallback) throws Exception {
        queueEntry.updateStatus(DownloadStatusEnum.SCANNING,
            l10n("gui.direct_http.download_status.scanning_page"));

        Set<String> extraAllowed = WebScannerExtensions.parseExtensionList(
            settings().getWebScannerAllowedExtensions());
        Set<String> extraBlacklisted = WebScannerExtensions.parseExtensionList(
            settings().getWebScannerBlacklistedExtensions());

        WebScannerExtensions extensions = WebScannerExtensions.createDefault(extraAllowed, extraBlacklisted);

        Duration pageFetchTimeout = Duration.ofMillis(CONNECT_TIMEOUT_MILLIS + READ_TIMEOUT_MILLIS);
        WebScanner scanner = new WebScanner(main.getHttpManager().getClient(), extensions,
            settings().getMaxPageSizeBytes(), pageFetchTimeout);

        int maxDepth = settings().getWebScannerMaxDepth();
        boolean strictHost = settings().isWebScannerStrictHost();

        Set<String> mediaLinks;
        try {
            mediaLinks = scanner.scanForMediaLinks(pageUrl.toString(), null, maxDepth, strictHost,
                () -> !isAlive(queueEntry),
                (key, args) -> queueEntry.updateStatus(DownloadStatusEnum.SCANNING, l10n(key, args), false));
        } catch (Exception e) {
            throw new IOException("Failed to scan page for media: " + pageUrl + ": " + e.getMessage(), e);
        }

        if (!isAlive(queueEntry)) {
            log.info("Download cancelled during media scanning phase.");
            return false;
        }

        if (mediaLinks.isEmpty()) {
            throw new UnsupportedURLException(
                l10n("gui.direct_http.download_status.error.no_media_found", pageUrl));
        }

        log.info("Found {} candidate media link(s) from {}", mediaLinks.size(), pageUrl);

        int totalFiles = mediaLinks.size();
        AtomicInteger fileIndex = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        Semaphore concurrencyLimiter = new Semaphore(
            Math.max(1, Math.min(settings().getMaxConcurrentCrawledDownloads(), totalFiles)));

        List<Future<?>> futures = new ArrayList<>();

        for (String link : mediaLinks) {
            if (!isAlive(queueEntry)) {
                break;
            }

            futures.add(chunkThreadPool.submit(() -> {
                boolean acquired = false;
                long waitStart = System.currentTimeMillis();

                while (!acquired && isAlive(queueEntry)) {
                    try {
                        acquired = concurrencyLimiter.tryAcquire(500, TimeUnit.MILLISECONDS);
                        if (!acquired && (System.currentTimeMillis() - waitStart) > 1000) {
                            queueEntry.updateStatus(DownloadStatusEnum.WAITING,
                                l10n("gui.direct_http.download_status.waiting_queue_slot"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (!acquired) {
                    return;
                }

                try {
                    if (!isAlive(queueEntry)) {
                        return;
                    }

                    URL mediaUrl;
                    try {
                        mediaUrl = new URI(link).toURL();
                    } catch (Exception e) {
                        log.warn("Skipping malformed scanned link {}: {}", link, e.getMessage());
                        return;
                    }

                    Pair<HttpURLConnection, Integer> mediaConnectionPair = openConnection(mediaUrl, "HEAD", pageUrl.toString());
                    if (mediaConnectionPair == null) {
                        mediaConnectionPair = openConnection(mediaUrl, "GET", pageUrl.toString());
                    }

                    if (mediaConnectionPair == null) {
                        log.warn("Skipping unreachable scanned media link: {}", link);
                        return;
                    }

                    int currentIndex = fileIndex.incrementAndGet();

                    boolean ok = downloadResolvedFile(queueEntry, mediaUrl, mediaConnectionPair.getKey(),
                        (percent, total, speed, remainingTime, chunkCount) -> {
                            double overallPercent = ((currentIndex - 1) * 100.0 + percent) / totalFiles;

                            progressCallback.accept(overallPercent, total, speed, remainingTime, chunkCount);

                            queueEntry.updateStatus(DownloadStatusEnum.DOWNLOADING,
                                l10n("gui.direct_http.download_status.discovered_media_progress",
                                    currentIndex, totalFiles,
                                    StringUtils.formatPercent(percent),
                                    StringUtils.getHumanReadableFileSize(total),
                                    StringUtils.getHumanReadableFileSize(speed)), false);
                        }, pageUrl.toString());

                    if (ok) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Failed to download scanned media {}: {}", link, e.getMessage());
                } finally {
                    concurrencyLimiter.release();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("Error while waiting for a scanned media download: {}", e.getMessage());
            }
        }

        return successCount.get() > 0;
    }

    private boolean downloadResolvedFile(QueueEntry queueEntry, URL fileUrl,
        HttpURLConnection connection, ProgressUpdater progressCallback) throws Exception {

        return downloadResolvedFile(queueEntry, fileUrl, connection, progressCallback, null);
    }

    private boolean downloadResolvedFile(QueueEntry queueEntry, URL fileUrl,
        HttpURLConnection connection, ProgressUpdater progressCallback, @Nullable String referer) throws Exception {

        String mimeType = connection.getContentType();
        log.debug("MIME Type: {}", mimeType);
        if (mimeType == null || (mimeType.contains("text/html") || mimeType.contains("text/plain"))) {
            closeQuietly(connection);

            throw new UnsupportedURLException(
                l10n("gui.direct_http.download_status.error.unsupported_url", fileUrl));
        }

        long totalBytes = connection.getContentLengthLong();
        log.debug("Total file size: {}", StringUtils.getHumanReadableFileSize(totalBytes));
        if (totalBytes <= 0) {
            closeQuietly(connection);

            throw new IOException(
                l10n("gui.direct_http.download_status.error.content_length_unknown", fileUrl));
        }

        String detectedFileName = getFileNameFromHeaders(connection);
        log.debug("Detected filename: {}", detectedFileName);
        if (detectedFileName == null || detectedFileName.isEmpty()) {
            closeQuietly(connection);

            throw new IOException(
                l10n("gui.direct_http.download_status.error.filename_unknown", fileUrl));
        }

        boolean supportsRanges = "bytes".equalsIgnoreCase(connection.getHeaderField("Accept-Ranges"));
        closeQuietly(connection);

        String suggestedUrlPath = URLUtils.getDirectoryPath(fileUrl.toString());

        Path basePath = queueEntry.getTmpDirectory().toPath();
        Path urlPath = Paths.get("");
        if (settings().isOrganizeFilesIntoFolders() && suggestedUrlPath != null) {
            urlPath = Paths.get(suggestedUrlPath);
        }

        Path targetPath = basePath.resolve(urlPath);

        int pathLength = targetPath.resolve(detectedFileName).toString().length();
        if (pathLength > 260 && GDownloader.isWindows()) { // Microsoft shenanigans
            log.info("Long path detected, trimming {}", targetPath);
            targetPath = DirectoryUtils.trimPathToFit(basePath, urlPath, detectedFileName, 260);
            log.info("Trimmed to {}", targetPath);
        }

        Files.createDirectories(targetPath);

        File targetFile = new File(targetPath.toFile(), detectedFileName);

        long downloadedBytesSoFar = targetFile.exists() ? targetFile.length() : 0;

        long remainingBytes = totalBytes - downloadedBytesSoFar;
        if (remainingBytes <= 0) {
            log.info("Download already complete.");
            return true;
        }

        BandwidthThrottle throttle = new BandwidthThrottle(
            () -> settings().getMaxDownloadSpeedBytesPerSecond());
        boolean attemptChunking = supportsRanges;

        while (true) {
            AtomicLong downloadedBytes = new AtomicLong(downloadedBytesSoFar);
            AtomicInteger activeChunkCount = new AtomicInteger(0);
            AtomicBoolean abortHook = new AtomicBoolean();

            if (!attemptChunking) {
                if (log.isDebugEnabled()) {
                    log.info("Server does not support multi-threading, downloading single-threaded.");
                    log.debug("Start offset: {} remaining: {}", downloadedBytesSoFar, remainingBytes);
                }

                activeChunkCount.incrementAndGet();
                try {
                    ChunkData chunkData = ChunkData.builder()
                        .chunkId(0)
                        .abortHook(abortHook)
                        .chunked(downloadedBytesSoFar > 0)
                        .soleChunk(true)
                        .queueEntry(queueEntry)
                        .fileUrl(fileUrl)
                        .referer(referer)
                        .filePath(targetFile)
                        .startByte(downloadedBytesSoFar)
                        .endByte(totalBytes - 1)
                        .totalBytes(totalBytes)
                        .downloadedBytes(downloadedBytes)
                        .activeChunkCount(activeChunkCount)
                        .progressCallback(progressCallback)
                        .throttle(throttle)
                        .build();

                    return downloadChunk(chunkData);
                } finally {
                    activeChunkCount.decrementAndGet();
                }
            }

            // No support for cold-start resume of chunked downloads yet
            downloadedBytes.set(0);

            int configuredMaxChunks = Math.clamp(settings().getMaxDownloadChunks(), 1, 20);
            int maxDownloadChunks = (int)Math.max(1,
                Math.min(configuredMaxChunks, totalBytes / MIN_CHUNK_SIZE_BYTES));

            if (maxDownloadChunks == 1) {
                attemptChunking = false;
                continue;
            }

            long chunkSize = totalBytes / maxDownloadChunks;
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < maxDownloadChunks; i++) {
                int chunkId = i;
                long startByte = i * chunkSize;
                long endByte = (i == maxDownloadChunks - 1)
                    ? totalBytes - 1 : (startByte + chunkSize - 1);

                if (log.isDebugEnabled()) {
                    log.debug("Chunk {} start/end {}/{}", i, startByte, endByte);
                }

                activeChunkCount.incrementAndGet();
                futures.add(chunkThreadPool.submit(() -> {
                    try {
                        ChunkData chunkData = ChunkData.builder()
                            .chunkId(chunkId)
                            .abortHook(abortHook)
                            .chunked(true)
                            .soleChunk(maxDownloadChunks == 1)
                            .queueEntry(queueEntry)
                            .fileUrl(fileUrl)
                            .referer(referer)
                            .filePath(targetFile)
                            .startByte(startByte)
                            .endByte(endByte)
                            .totalBytes(totalBytes)
                            .downloadedBytes(downloadedBytes)
                            .activeChunkCount(activeChunkCount)
                            .progressCallback(progressCallback)
                            .throttle(throttle)
                            .build();

                        downloadChunk(chunkData);
                    } catch (Exception e) {
                        log.error("Error downloading chunk: {}", e.getMessage());
                        throw new RuntimeException(e);
                    } finally {
                        activeChunkCount.decrementAndGet();
                    }
                }));
            }

            try {
                for (Future<?> future : futures) {
                    future.get();
                }

                if (downloadedBytes.get() != totalBytes) {
                    throw new IOException(
                        l10n("gui.direct_http.download_status.error.download_incomplete", fileUrl));
                }

                log.info("Download complete: {}", targetFile.getAbsolutePath());
                return true;

            } catch (Exception e) {
                // Server refused Ranges for some reason, catch and fallback.
                Throwable cause = e;
                boolean rangeRefused = false;
                while (cause != null) {
                    if (cause instanceof RangeRefusedException) {
                        rangeRefused = true;
                        break;
                    }

                    cause = cause.getCause();
                }

                if (rangeRefused) {
                    log.warn("Server ignored Range header. Falling back to single-threaded download...");

                    attemptChunking = false;
                    downloadedBytesSoFar = 0;
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }

                    continue;
                }

                throw new IOException(
                    l10n("gui.direct_http.download_status.error.chunk_failed", fileUrl, e.getMessage()), e);
            }
        }
    }

    private boolean downloadChunk(ChunkData chunkData) throws IOException {
        int attempt = 0;
        boolean success = false;
        int currentByteOffset = 0;

        Supplier<Boolean> alive = () -> isAlive(chunkData.getQueueEntry()) && !chunkData.getAbortHook().get();

        int chunkRetries = Math.clamp(main.getConfig().getMaxFragmentRetries(), 1, 50);

        while (attempt < chunkRetries && !success && alive.get()) {
            // A 429 anywhere pauses every subsequent attempt until the cooldown clears
            long hostCooldown = getHostCooldownRemainingMillis(chunkData.getFileUrl());
            if (hostCooldown > 0) {
                waitCancellable(chunkData.getQueueEntry(), hostCooldown, alive,
                    "gui.direct_http.download_status.rate_limit_cooldown");

                continue;
            }

            HttpURLConnection connection = null;
            AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
            Thread cancelWatcher = null;
            Semaphore hostLimiter = getHostLimiter(chunkData.getFileUrl());
            boolean acquiredSlot = false;
            long backoffMillis = -1;

            try {
                long slotWaitStart = System.currentTimeMillis();
                while (!acquiredSlot && alive.get()) {
                    try {
                        acquiredSlot = hostLimiter.tryAcquire(200, TimeUnit.MILLISECONDS);
                        if (!acquiredSlot && (System.currentTimeMillis() - slotWaitStart) > 1000) {
                            chunkData.getQueueEntry().updateStatus(DownloadStatusEnum.WAITING,
                                l10n("gui.direct_http.download_status.waiting_server_connection"));
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();

                        throw new IOException(
                            l10n("gui.direct_http.download_status.error.download_cancelled_waiting_slot"), ie);
                    }
                }

                if (!acquiredSlot) {
                    throw new IOException(
                        l10n("gui.direct_http.download_status.error.download_cancelled_waiting_slot"));
                }

                connection = (HttpURLConnection)chunkData.getFileUrl().openConnection(getProxySettings());
                connectionRef.set(connection);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestMethod("GET");
                applyBrowserHeaders(connection, ACCEPT_ANY, chunkData.getReferer());

                cancelWatcher = Thread.ofVirtual().start(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        if (!alive.get()) {
                            HttpURLConnection toKill = connectionRef.get();
                            if (toKill != null) {
                                toKill.disconnect();
                            }

                            return;
                        }

                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                });

                long startOffset = chunkData.getStartByte() + currentByteOffset;

                if (chunkData.isChunked()) {
                    connection.setRequestProperty("Range", "bytes=" + startOffset + "-" + chunkData.getEndByte());
                }

                int responseCode = connection.getResponseCode();

                switch (responseCode) {
                    case HTTP_PARTIAL, HTTP_OK -> {
                        try (
                            InputStream inputStream = connection.getInputStream();
                            RandomAccessFile outputFile = new RandomAccessFile(chunkData.getFilePath(), "rw")) {
                            if (responseCode == HTTP_PARTIAL) {
                                log.debug("Partial download accepted, resuming from {} <- offset {}", chunkData.getStartByte(), startOffset);
                                outputFile.seek(startOffset); // Move to the start of this chunk
                            } else if (chunkData.isChunked()) {
                                if (!chunkData.isSoleChunk()) {
                                    chunkData.getAbortHook().set(true);

                                    throw new RangeRefusedException("Server returned 200 OK instead of 206 Partial Content");
                                }

                                log.debug("Partial download refused, resetting progress");
                                chunkData.getDownloadedBytes().set(0);
                            }

                            long startTime = System.nanoTime();
                            long totalDownloadedAtStart = chunkData.getDownloadedBytes().get();
                            long lastCallbackTime = System.nanoTime();

                            byte[] buffer = new byte[BUFFER_SIZE];
                            int bytesRead;

                            while ((bytesRead = inputStream.read(buffer)) != -1 && alive.get()) {
                                if (chunkData.getThrottle() != null) {
                                    chunkData.getThrottle().acquire(bytesRead, alive);

                                    if (!alive.get()) {
                                        break;
                                    }
                                }

                                outputFile.write(buffer, 0, bytesRead);
                                currentByteOffset += bytesRead;

                                long totalDownloaded = chunkData.getDownloadedBytes().addAndGet(bytesRead);

                                long currentTime = System.nanoTime();
                                if ((currentTime - lastCallbackTime) >= 1e9) {
                                    if (chunkData.getProgressCallback() == null) {
                                        continue;
                                    }

                                    double progress = ((double)totalDownloaded * 100) / chunkData.getTotalBytes();

                                    // Speed
                                    long elapsedTimeNano = currentTime - startTime;
                                    double elapsedTimeSeconds = elapsedTimeNano / 1e9;
                                    long speed = (elapsedTimeSeconds > 0)
                                        ? (long)((totalDownloaded - totalDownloadedAtStart) / elapsedTimeSeconds) : 0;

                                    // ETA
                                    long remainingBytes = chunkData.getTotalBytes() - totalDownloaded;
                                    double remainingTimeSeconds = (speed > 0) ? (double)remainingBytes / speed : 0;
                                    long remainingTimeMillis = (long)(remainingTimeSeconds * 1000);

                                    chunkData.getProgressCallback().accept(
                                        progress,
                                        chunkData.getTotalBytes(),
                                        speed,
                                        remainingTimeMillis,
                                        chunkData.getActiveChunkCount().get()
                                    );

                                    lastCallbackTime = currentTime;
                                }
                            }
                        }

                        log.debug("Chunk {} has quit", chunkData.getChunkId());
                        success = alive.get();
                    }
                    case 429 ->
                        throw new RateLimitedException(parseRetryAfterMillis(connection));
                    default ->
                        throw new IOException(
                            l10n("gui.direct_http.download_status.error.server_http_error", responseCode));
                }
            } catch (Exception e) {
                attempt++;

                if (log.isDebugEnabled()) {
                    log.error("Error on attempt {}: {}", attempt, e.getMessage());
                }

                if (!alive.get()) {
                    throw new IOException(
                        l10n("gui.direct_http.download_status.error.download_cancelled"), e);
                }

                if (attempt == chunkRetries) {
                    chunkData.getAbortHook().set(true);

                    throw new IOException(
                        l10n("gui.direct_http.download_status.error.chunk_failed_retries",
                            chunkRetries, e.getMessage()), e);
                }

                if (e instanceof RateLimitedException rle) {
                    backoffMillis = rle.retryAfterMillis > 0
                        ? addJitter(rle.retryAfterMillis)
                        : computeBackoffMillis(attempt);

                    markHostRateLimited(chunkData.getFileUrl(), backoffMillis);
                } else {
                    backoffMillis = computeBackoffMillis(attempt);
                }
            } finally {
                if (cancelWatcher != null) {
                    cancelWatcher.interrupt();
                }

                if (acquiredSlot) {
                    hostLimiter.release();
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (backoffMillis >= 0) {
                waitCancellable(chunkData.getQueueEntry(), backoffMillis, alive,
                    "gui.direct_http.download_status.retrying_in");
            }
        }

        return success;
    }

    private Proxy getProxySettings() {
        return main.getConfig().getProxySettings().createProxy();
    }

    private Semaphore getHostLimiter(URL url) {
        String host = url.getHost() != null
            ? url.getHost().toLowerCase(Locale.ROOT)
            : "unknown";

        return hostConnectionLimiters.computeIfAbsent(host,
            h -> new Semaphore(Math.max(1, settings().getMaxConnectionsPerHost())));
    }

    @Nullable
    private String getFileNameFromHeaders(HttpURLConnection connection) {
        if (log.isDebugEnabled()) {
            log.debug(connection.getContentType());
            log.debug("{}", connection.getHeaderFields());
        }

        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null) {
            for (String part : contentDisposition.split(";")) {
                String trimmed = part.trim();

                // RFC 5987 extended form.
                if (trimmed.startsWith("filename*=")) {
                    String value = trimmed.substring("filename*=".length());
                    int quotePos = value.indexOf("''");
                    String encoded = quotePos >= 0 ? value.substring(quotePos + 2) : value;

                    try {
                        return URLDecoder.decode(encoded, StandardCharsets.UTF_8).replace("\"", "");
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to decode filename* header value: {}", value);
                    }
                }

                if (trimmed.startsWith("filename=")) {
                    String[] split = trimmed.split("=", 2);
                    if (split.length == 2) {
                        return split[1].trim().replace("\"", "");
                    }
                }
            }
        }

        return URLUtils.getFileName(connection.getURL());
    }

    @Override
    @PreDestroy
    public void close() {
        chunkThreadPool.shutdownNow();
    }

    private long parseRetryAfterMillis(HttpURLConnection connection) {
        String header = connection.getHeaderField("Retry-After");
        if (header == null || header.isBlank()) {
            return -1;
        }

        try {
            long seconds = Long.parseLong(header.trim());

            return Math.max(0, Math.min(seconds * 1000, MAX_RETRY_AFTER_MILLIS));
        } catch (NumberFormatException e) {
            try {
                ZonedDateTime retryDate = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                long millis = Duration.between(ZonedDateTime.now(retryDate.getZone()), retryDate).toMillis();

                return Math.max(0, Math.min(millis, MAX_RETRY_AFTER_MILLIS));
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    private void markHostRateLimited(URL url, long cooldownMillis) {
        String host = normalizeHost(url);
        long until = System.currentTimeMillis() + cooldownMillis;

        hostRateLimitedUntil.computeIfAbsent(host, h -> new AtomicLong(0))
            .updateAndGet(current -> Math.max(current, until));
    }

    private long getHostCooldownRemainingMillis(URL url) {
        AtomicLong ref = hostRateLimitedUntil.get(normalizeHost(url));

        return ref == null ? 0 : Math.max(0, ref.get() - System.currentTimeMillis());
    }

    private static String normalizeHost(URL url) {
        return url.getHost() != null
            ? url.getHost().toLowerCase(Locale.ROOT)
            : "unknown";
    }

    private static void waitCancellable(QueueEntry entry, long millis, Supplier<Boolean> alive, String reasonKey) {
        long deadline = System.currentTimeMillis() + millis;
        long lastStatusUpdate = 0;

        while (System.currentTimeMillis() < deadline && alive.get()) {
            long now = System.currentTimeMillis();
            if (now - lastStatusUpdate > 500) {
                double remainingSecs = Math.max(0, deadline - now) / 1000.0;
                String formattedTime = String.format("%.1fs", remainingSecs);

                entry.updateStatus(DownloadStatusEnum.WAITING, l10n(reasonKey, formattedTime));
                lastStatusUpdate = now;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();

                return;
            }
        }
    }

    private static long computeBackoffMillis(int attempt) {
        long exp = BASE_BACKOFF_MILLIS * (1L << Math.min(attempt, 16)); // avoids overflow
        long capped = Math.min(exp, MAX_BACKOFF_MILLIS);

        return ThreadLocalRandom.current().nextLong(capped + 1);
    }

    private static long addJitter(long baseMillis) {
        long jitterWindow = Math.max(250, baseMillis / 4);

        return baseMillis + ThreadLocalRandom.current().nextLong(jitterWindow);
    }

    private static void closeQuietly(@Nullable HttpURLConnection connection) {
        if (connection == null) {
            return;
        }

        try {
            int code = connection.getResponseCode();
            if (code / 100 == 2) {
                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int totalRead = 0;
                    int bytesRead;

                    while (totalRead < MAX_DRAIN_BYTES && (bytesRead = inputStream.read(buffer)) != -1) {
                        totalRead += bytesRead;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to drain response body before closing: {}", e.getMessage());
        } finally {
            connection.disconnect();
        }
    }

    @FunctionalInterface
    private interface ProgressUpdater {

        void accept(double progress, long size, long speed, long remainingTime, int chunkCount);

    }

    @Data
    @Builder
    private static class ChunkData {

        private int chunkId;
        private AtomicBoolean abortHook;
        private boolean chunked;
        private boolean soleChunk;
        private QueueEntry queueEntry;
        private URL fileUrl;
        private String referer;
        private File filePath;
        private long startByte;
        private long endByte;
        private long totalBytes;
        private AtomicLong downloadedBytes;
        private AtomicInteger activeChunkCount;
        private ProgressUpdater progressCallback;
        private BandwidthThrottle throttle;
    }

    private static final class BandwidthThrottle {

        private final Object lock = new Object();
        private final Supplier<Long> bytesPerSecond;
        private long availableTokens;
        private long lastRefillNanos;

        private BandwidthThrottle(Supplier<Long> bytesPerSecondIn) {
            bytesPerSecond = bytesPerSecondIn;
            availableTokens = Math.max(0, bytesPerSecond.get());
            lastRefillNanos = System.nanoTime();
        }

        private void acquire(int bytes, Supplier<Boolean> aliveCheck) {
            synchronized (lock) {
                if (bytesPerSecond.get() <= 0) {
                    return;
                }

                refillLocked();

                while (availableTokens < bytes) {
                    if (aliveCheck != null && !aliveCheck.get()) {
                        return;
                    }

                    long currentLimit = bytesPerSecond.get();
                    if (currentLimit <= 0) {
                        return;
                    }

                    long deficit = bytes - availableTokens;
                    long waitMillis = Math.max(1, (long)(deficit / (double)currentLimit * 1000));
                    waitMillis = Math.min(waitMillis, 200L);

                    try {
                        lock.wait(waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    refillLocked();
                }

                availableTokens -= bytes;
            }
        }

        // Caller must hold the lock.
        private void refillLocked() {
            long currentLimit = bytesPerSecond.get();
            if (currentLimit <= 0) {
                return;
            }

            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;

            if (elapsedNanos > 0) {
                long refill = (long)(elapsedNanos / 1e9 * currentLimit);
                if (refill > 0) {
                    if (availableTokens < currentLimit) {
                        availableTokens = Math.min(currentLimit, availableTokens + refill);
                    }

                    lastRefillNanos = now;
                }
            }
        }
    }

    private static final class RateLimitedException extends IOException {

        private final long retryAfterMillis;

        private RateLimitedException(long retryAfterMillisIn) {
            super("Failed to connect with HTTP code: 429");

            retryAfterMillis = retryAfterMillisIn;
        }
    }

    private static final class RangeRefusedException extends IOException {

        private RangeRefusedException(String message) {
            super(message);
        }
    }

    private static final class UnsupportedURLException extends IOException {

        private UnsupportedURLException(String message) {
            super(message);
        }
    }
}
