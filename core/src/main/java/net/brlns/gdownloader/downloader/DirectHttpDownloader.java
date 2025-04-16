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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.DIRECT;
import static net.brlns.gdownloader.util.FileUtils.relativize;

// TODO: Resume chunked
// TODO: Add proxy settings to UI as a floating window that validates fields.
// TODO: Do not consume unsupported urls
// TODO: Clipboard: deep scan current webpage for valid download urls. Add settings to configure scan depth and external links
// TODO: ftp
// TODO: Throttling, chunked downloads are easily saturating all available downlink bandwidth
/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DirectHttpDownloader extends AbstractDownloader {

    private static final String PREFIX = "[direct-http] ";

    private static final int BUFFER_SIZE = 8192;

    private final ExecutorService chunkThreadPool = Executors.newVirtualThreadPerTaskExecutor();

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    public DirectHttpDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public boolean isEnabled() {
        return main.getConfig().isDirectHttpEnabled();
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
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.emptyList();
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        // Not implemented
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return isEnabled()
            && !(inputUrl.contains("ytimg")
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
        if (!main.getConfig().isDirectHttpEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != DIRECT
                || !main.getConfig().isDirectHttpEnabled()) {
                continue;
            }

            entry.setCurrentDownloadType(type);

            try {
                success = downloadFile(entry, (percent, total, speed, remainingTime, chunkCount) -> {
                    String fmPercent = StringUtils.formatPercent(percent);
                    percent = Math.round(percent * 10) / 10.0;// Strip out unecessary precision

                    double lastPercentage = entry.getMediaCard().getPercentage();

                    if (percent > lastPercentage || percent < 5 || Math.abs(percent - lastPercentage) > 10) {
                        entry.getMediaCard().setPercentage(percent);
                    }

                    String fmTotal = StringUtils.getHumanReadableFileSize(total);
                    String fmSpeed = StringUtils.getHumanReadableFileSize(speed);
                    String fmRemaingTime = StringUtils.convertTime(remainingTime);

                    entry.updateStatus(DownloadStatusEnum.DOWNLOADING,
                        String.format("%s%% of %s at %s/s ETA: %s chks %d",
                            fmPercent, fmTotal, fmSpeed, fmRemaingTime, chunkCount), false);
                });

                lastOutput = PREFIX + "Download complete";
                entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput);
            } catch (Exception e) {
                lastOutput = PREFIX + e.getMessage();

                success = false;
            } finally {
                entry.getDownloadStarted().set(false);
            }

            log.info(lastOutput);

            if (!isAlive(entry)) {
                return new DownloadResult(FLAG_STOPPED);
            }

            if (!success) {
                if (lastOutput.contains("Unsupported URL")) {
                    return new DownloadResult(FLAG_UNSUPPORTED, lastOutput);
                }

                return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected DownloadResult transcodeMediaFiles(QueueEntry entry) {
        // TODO
        return null;
    }

    @Override
    protected void processMediaFiles(QueueEntry entry) {
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "HTTP");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

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

    @Nullable
    private Pair<HttpURLConnection, Integer> openConnection(URL fileUrl, String requestType) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)fileUrl.openConnection();
            connection.setRequestMethod(requestType);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP error code: " + responseCode);
            }

            return new Pair<>(connection, responseCode);
        } catch (Exception e) {
            log.error("Request {} failed for {}", requestType, fileUrl);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean downloadFile(QueueEntry queueEntry, ProgressUpdater progressCallback) throws Exception {
        URL fileUrl = new URI(queueEntry.getUrl()).toURL();

        Pair<HttpURLConnection, Integer> connectionPair = openConnection(fileUrl, "HEAD");
        if (connectionPair == null) {
            connectionPair = openConnection(fileUrl, "GET");
        }

        if (connectionPair == null) {
            throw new IOException("Connection failed: " + fileUrl);
        }

        if (connectionPair.getValue() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Server returned HTTP error code: " + connectionPair.getValue());
        }

        HttpURLConnection connection = connectionPair.getKey();

        String mimeType = connection.getContentType();
        log.info("MIME Type: " + mimeType);
        if (mimeType == null || (mimeType.contains("text/html") || mimeType.contains("text/plain"))) {
            throw new IOException("Unsupported URL: " + fileUrl);
        }

        long totalBytes = connection.getContentLengthLong();
        log.info("Total file size: {}", StringUtils.getHumanReadableFileSize(totalBytes));
        if (totalBytes <= 0) {
            throw new IOException("Cannot determine content length: " + fileUrl);
        }

        String detectedFileName = getFileNameFromHeaders(connection);
        log.info("Detected filename: " + detectedFileName);
        if (detectedFileName == null || detectedFileName.isEmpty()) {
            throw new IOException("Cannot determine filename: " + fileUrl);
        }

        String suggestedUrlPath = URLUtils.getDirectoryPath(fileUrl.toString());

        Path basePath = queueEntry.getTmpDirectory().toPath();
        Path urlPath = Paths.get(suggestedUrlPath != null ? suggestedUrlPath : "");
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
            log.debug("Download already complete.");
            return true;
        }

        AtomicLong downloadedBytes = new AtomicLong(downloadedBytesSoFar);
        AtomicInteger activeChunkCount = new AtomicInteger(0);
        AtomicBoolean abortHook = new AtomicBoolean();

        if (!"bytes".equalsIgnoreCase(connection.getHeaderField("Accept-Ranges"))) {
            log.info("Server does not support multi-threading, downloading single-threaded.");
            log.debug("Start offset: {} remaining: {}", downloadedBytesSoFar, remainingBytes);

            activeChunkCount.incrementAndGet();
            try {
                ChunkData chunkData = ChunkData.builder()
                    .chunkId(0)
                    .abortHook(abortHook)
                    .chunked(downloadedBytesSoFar > 0)
                    .queueEntry(queueEntry)
                    .fileUrl(fileUrl)
                    .filePath(targetFile)
                    .startByte(downloadedBytesSoFar)
                    .endByte(totalBytes - 1)
                    .totalBytes(totalBytes)
                    .downloadedBytes(downloadedBytes)
                    .activeChunkCount(activeChunkCount)
                    .progressCallback(progressCallback)
                    .build();

                return downloadChunk(chunkData);
            } finally {
                activeChunkCount.decrementAndGet();
            }
        }

        // No support for cold-start resume of chunked downloads yet
        downloadedBytes.set(0);

        int maxDownloadChunks = Math.clamp(manager.getMain()
            .getConfig().getDirectHttpMaxDownloadChunks(), 1, 20);

        long chunkSize = totalBytes / maxDownloadChunks;

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < maxDownloadChunks; i++) {
            int chunkId = i;
            long startByte = i * chunkSize;
            long endByte = (i == maxDownloadChunks - 1) ? totalBytes - 1 : (startByte + chunkSize - 1);

            log.error("Chunk {} start/end {}/{}", i, startByte, endByte);
            activeChunkCount.incrementAndGet();

            futures.add(chunkThreadPool.submit(() -> {
                try {
                    ChunkData chunkData = ChunkData.builder()
                        .chunkId(chunkId)
                        .abortHook(abortHook)
                        .chunked(true)
                        .queueEntry(queueEntry)
                        .fileUrl(fileUrl)
                        .filePath(targetFile)
                        .startByte(startByte)
                        .endByte(endByte)
                        .totalBytes(totalBytes)
                        .downloadedBytes(downloadedBytes)
                        .activeChunkCount(activeChunkCount)
                        .progressCallback(progressCallback)
                        .build();

                    downloadChunk(chunkData);
                } catch (Exception e) {
                    log.error("Error downloading chunk: " + e.getMessage());
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
        } catch (Exception e) {
            throw new IOException("Failed to download a chunk: " + fileUrl + ": " + e.getMessage(), e);
        }

        if (downloadedBytes.get() != totalBytes) {
            throw new IOException("Download incomplete: " + fileUrl);
        }

        log.info("Download complete: " + targetFile.getAbsolutePath());
        return true;
    }

    private boolean downloadChunk(ChunkData chunkData) throws IOException {
        int attempt = 0;
        boolean success = false;
        int currentByteOffset = 0;

        Supplier<Boolean> alive = () -> isAlive(chunkData.getQueueEntry()) && !chunkData.getAbortHook().get();

        int chunkRetries = Math.clamp(main.getConfig().getMaxFragmentRetries(), 1, 50);

        while (attempt < chunkRetries && !success && alive.get()) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)chunkData.getFileUrl().openConnection(getProxySettings());
                connection.setRequestMethod("GET");

                long startOffset = chunkData.getStartByte() + currentByteOffset;

                if (chunkData.isChunked()) {
                    connection.setRequestProperty("Range", "bytes=" + startOffset + "-" + chunkData.getEndByte());
                }

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_PARTIAL
                    || responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream();
                         RandomAccessFile outputFile = new RandomAccessFile(chunkData.getFilePath(), "rw")) {
                        if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                            log.debug("Partial download accepted, resuming from {} <- offset {}", chunkData.getStartByte(), startOffset);
                            outputFile.seek(startOffset); // Move to the start of this chunk
                        } else if (chunkData.isChunked()) {
                            if (chunkData.getStartByte() != 0 && chunkData.getEndByte() != chunkData.getTotalBytes() - 1) {
                                throw new IOException("Partial download refused by server");
                            } else {
                                log.debug("Partial download refused, resetting progress");
                                chunkData.getDownloadedBytes().set(0);
                            }
                        }

                        long startTime = System.nanoTime();
                        long totalDownloadedAtStart = chunkData.getDownloadedBytes().get();
                        long lastCallbackTime = System.nanoTime();

                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;

                        while ((bytesRead = inputStream.read(buffer)) != -1 && alive.get()) {
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
                } else {
                    throw new IOException("Failed to connect with HTTP code: " + responseCode);
                }
            } catch (Exception e) {
                attempt++;
                log.error("Error on attempt {}: {}", attempt, e.getMessage());

                if (attempt == chunkRetries) {
                    chunkData.getAbortHook().set(true);
                    throw new IOException("Failed to download file after " + chunkRetries + " attempts: " + e.getMessage(), e);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return success;
    }

    private Proxy getProxySettings() {
        return main.getConfig().getProxySettings().createProxy();
    }

    @Nullable
    private String getFileNameFromHeaders(HttpURLConnection connection) {
        if (log.isDebugEnabled()) {
            log.info(connection.getContentType());
            log.info("{}", connection.getHeaderFields());
        }

        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("filename=")) {
                    return part.split("=")[1].trim().replace("\"", "");
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
        private QueueEntry queueEntry;
        private URL fileUrl;
        private File filePath;
        private long startByte;
        private long endByte;
        private long totalBytes;
        private AtomicLong downloadedBytes;
        private AtomicInteger activeChunkCount;
        private ProgressUpdater progressCallback;
    }

}
