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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.menu.IMenuEntry;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;

// TODO: ETA
// TODO: Speed
// TODO: Settings
// TODO: Resume
/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DirectHttpDownloader extends AbstractDownloader {

    private static final int BUFFER_SIZE = 8192;
    private static final int THREAD_COUNT = 4;
    private static final int MAX_RETRIES = 5;

    private final DecimalFormat percentFormatter = new DecimalFormat("#0.0");

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    @Getter
    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

    public DirectHttpDownloader(DownloadManager managerIn) {
        super(managerIn);
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
    protected boolean canConsumeUrl(String inputUrl) {
        return !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/"));
    }

    @Override
    protected boolean tryQueryVideo(QueueEntry queueEntry) {
        // TODO
        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
//        if (!main.getConfig().isDownloadDirect() || !main.getConfig().isDirectHttpEnabled()) {
//            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
//        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported) {
                // || type == DIRECT && !main.getConfig().isDownloadDirect() 
                // ||!main.getConfig().isDirectHttpEnabled()) {
                continue;
            }

            try {
                success = downloadFile(entry, (percentage, chunkCount) -> {
                    processProgress(entry, percentage, chunkCount);
                });

                lastOutput = "Download complete";

                entry.getDownloadStarted().set(false);
            } catch (Exception e) {
                lastOutput = e.getMessage();
            }

            if (!isAlive(entry)) {
                return new DownloadResult(FLAG_STOPPED);
            }

            //lastOutput = result.getValue();
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
    protected Map<String, IMenuEntry> processMediaFiles(QueueEntry entry) {
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "Http");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

        File tmpPath = entry.getTmpDirectory();

        Map<String, IMenuEntry> rightClickOptions = new TreeMap<>();

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .sorted(Comparator.reverseOrder()) // Process files before directories
                .toList();

            for (Path path : paths) {
                if (path.equals(tmpPath.toPath())) {
                    continue;
                }

                Path relativePath = tmpPath.toPath().relativize(path);
                Path targetPath = finalPath.toPath().resolve(relativePath);

                try {
                    if (Files.isDirectory(targetPath)) {
                        Files.createDirectories(targetPath);
                        log.info("Created directory: {}", targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());
                        log.info("Moved file: {}", targetPath);
                    }
                } catch (FileAlreadyExistsException e) {
                    log.warn("File or directory already exists: {}", targetPath, e);
                } catch (IOException e) {
                    log.error("Failed to move file: {}", path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files", e);
        }

        return rightClickOptions;
    }

    private boolean isAlive(QueueEntry entry) {
        return manager.isRunning() && !entry.getCancelHook().get();
    }

    private void processProgress(QueueEntry entry, double percent, int chunkCount) {
        String formattedPercent = percentFormatter.format(percent);
        percent = Double.parseDouble(formattedPercent);

        double lastPercentage = entry.getMediaCard().getPercentage();

        if (percent > lastPercentage || percent < 5 || Math.abs(percent - lastPercentage) > 10) {
            entry.getMediaCard().setPercentage(percent);
        }

        entry.updateStatus(DownloadStatusEnum.DOWNLOADING,
            "Downloading: " + formattedPercent + "% active chunks: " + chunkCount);
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
        if (mimeType.contains("text/html")) {
            throw new IOException("Unsupported URL: " + fileUrl);
        }

        long totalBytes = connection.getContentLengthLong();
        log.info("Total file size: " + totalBytes + " bytes");
        if (totalBytes <= 0) {
            throw new IOException("Cannot determine content length: " + fileUrl);
        }

        String detectedFileName = getFileNameFromHeaders(connection);
        log.info("Detected filename: " + detectedFileName);
        if (detectedFileName == null || detectedFileName.isEmpty()) {
            throw new IOException("Cannot determine filename: " + fileUrl);
        }

        File targetFile = new File(queueEntry.getTmpDirectory(), detectedFileName);
        try (
            RandomAccessFile outputFile = new RandomAccessFile(targetFile, "rw")) {
            outputFile.setLength(totalBytes); // Preallocate file size
        }

        AtomicLong downloadedBytes = new AtomicLong(0);
        AtomicInteger activeChunkCount = new AtomicInteger(0);

        if (!"bytes".equalsIgnoreCase(connection.getHeaderField("Accept-Ranges"))) {
            log.info("Server does not support multi-threading, downloading single-threaded.");

            activeChunkCount.incrementAndGet();
            try {
                ChunkData chunkData = ChunkData.builder()
                    .chunked(false)
                    .queueEntry(queueEntry)
                    .fileUrl(fileUrl)
                    .filePath(targetFile)
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

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        long chunkSize = totalBytes / THREAD_COUNT;

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long startByte = i * chunkSize;
            long endByte = (i == THREAD_COUNT - 1) ? totalBytes - 1 : (startByte + chunkSize - 1);

            log.error("Chunk {} start/end {}/{}", i, startByte, endByte);
            activeChunkCount.incrementAndGet();

            futures.add(executor.submit(() -> {
                try {
                    ChunkData chunkData = ChunkData.builder()
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
                    executor.shutdownNow();

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
            throw new IOException("Failed to download a chunk: " + fileUrl, e);
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
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
        while (attempt < MAX_RETRIES && !success && isAlive(chunkData.getQueueEntry())) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)chunkData.getFileUrl().openConnection();
                connection.setRequestMethod("GET");

                if (chunkData.isChunked()) {
                    connection.setRequestProperty("Range", "bytes=" + chunkData.getStartByte() + "-" + chunkData.getEndByte());
                }

                int responseCode = connection.getResponseCode();
                int expectedCode = chunkData.isChunked() ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK;

                if (responseCode == expectedCode) {
                    try (InputStream inputStream = connection.getInputStream();
                         RandomAccessFile outputFile = new RandomAccessFile(chunkData.getFilePath(), "rw")) {

                        if (chunkData.isChunked()) {
                            outputFile.seek(chunkData.getStartByte()); // Move to the start of this chunk
                        }

                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;

                        while ((bytesRead = inputStream.read(buffer)) != -1 && isAlive(chunkData.getQueueEntry())) {
                            outputFile.write(buffer, 0, bytesRead);

                            long sum = chunkData.getDownloadedBytes().addAndGet(bytesRead);
                            double progress = ((double)sum * 100) / chunkData.getTotalBytes();

                            if (chunkData.getProgressCallback() != null) {
                                chunkData.getProgressCallback().accept(progress, chunkData.getActiveChunkCount().get());
                            }
                        }
                    }

                    success = true;
                } else {
                    throw new IOException("Failed to connect with HTTP code: " + responseCode);
                }
            } catch (Exception e) {
                attempt++;
                log.error("Error on attempt {}: {}", attempt, e.getMessage());

                if (attempt == MAX_RETRIES) {
                    throw new IOException("Failed to download file after " + MAX_RETRIES + " attempts.");
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return success;
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

    @FunctionalInterface
    private interface ProgressUpdater {

        void accept(double progress, int chunkCount);
    }

    @Data
    @Builder
    private static class ChunkData {

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
