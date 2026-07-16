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

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.extractors.GalleryDLMetadataExtractor;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.downloader.GalleryDLSettings;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryDeduplicator;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.FileUtils.relativize;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class GalleryDlDownloader extends AbstractDownloader {

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    private final GalleryDLMetadataExtractor metadataExtractor;

    @SuppressWarnings("this-escape")
    public GalleryDlDownloader(DownloadManager managerIn) {
        super(managerIn);

        metadataExtractor = new GalleryDLMetadataExtractor(this);
    }

    @Override
    public GalleryDLSettings settings() {
        return main.getConfig().getGalleryDLSettings();
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent() && settings().isEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.GALLERY_DL;
    }

    @Override
    public int getPreferenceScore(String inputUrl) {
        if (inputUrl.contains("pinterest.com/")) {
            return 100;
        }

        return super.getPreferenceScore(inputUrl);
    }

    @Override
    public boolean isMainDownloader() {
        return false;
    }

    @Override
    public String getDefaultOutputSubdirectory() {
        return "GalleryDL";
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.singletonList(GALLERY);
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        // Disapointingly, gallery-dl uses sqlite for its archive.
        // Adding a huge sqlite dependency just for this task would be quite wasteful.
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        boolean isSpotifyUrl = URLUtils.isSpotify(inputUrl);

        // Check if it's not a garbage URL
        boolean isNotGarbageUrl = !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/"));

        return isEnabled()
            && isNotGarbageUrl
            && !isSpotifyUrl;
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        try {
            String url = queueEntry.getUrl();

            Optional<MediaInfo> mediaInfoOptional = metadataExtractor.fetchMetadata(url);

            if (mediaInfoOptional.isPresent()) {
                MediaInfo mediaInfo = mediaInfoOptional.get();
                queueEntry.setMediaInfo(mediaInfo);

                return true;
            }
        } catch (Exception e) {
            log.error("{} failed to query for metadata {}: {}", getDownloaderId(), queueEntry.getUrl(), e.getMessage());

            if (log.isDebugEnabled()) {
                log.error("Exception:", e);
            }
        }

        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        if (!settings().isEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        AbstractUrlFilter filter = entry.getFilter();

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        ProcessArguments genericArguments = new ProcessArguments(
            executablePath.get().getAbsolutePath(),
            "--no-colors");

        if (!settings().isRespectConfigFile()) {
            genericArguments.add("--config-ignore");
        }

        Map<String, Object> rawOptions = new LinkedHashMap<>();

        main.getFfmpegTranscoder().getFfmpegPath().ifPresent(ffmpeg
            -> rawOptions.put("ffmpeg-location", ffmpeg.getAbsolutePath()));

        YtDlpDownloader ytdlp = (YtDlpDownloader)main.getDownloadManager()
            .getDownloader(DownloaderIdEnum.YT_DLP);// Always available
        ytdlp.getDenoPath().ifPresent(deno
            -> rawOptions.put("js_runtimes", Map.of("deno", Map.of("path", deno.getAbsolutePath()))));

        if (!rawOptions.isEmpty()) {
            try {
                genericArguments.add("-o", "downloader.ytdl.raw-options="
                    + GDownloader.OBJECT_MAPPER.writeValueAsString(rawOptions));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize ytdl raw-options", e);
            }
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != GALLERY || !settings().isEnabled()) {
                continue;
            }

            entry.setCurrentDownloadType(type);

            ProcessArguments arguments = new ProcessArguments(
                genericArguments,
                filter.getArguments(this, type, manager, tmpPath, entry.getUrl()));

            Pair<Integer, String> result = processDownload(entry, arguments);

            if (result == null || entry.getCancelHook().get()) {
                return new DownloadResult(FLAG_STOPPED);
            }

            lastOutput = result.getValue();

            if (result.getKey() != 0) {
                if (lastOutput.contains("Unsupported URL")) {
                    return new DownloadResult(FLAG_UNSUPPORTED, lastOutput);
                }

                return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
            } else {
                // TODO: consider moving after the deduplicator
                if (settings().isMediaTranscoding()) {
                    DownloadResult transcodeResult = transcodeMediaFiles(entry);

                    if (main.getConfig().isFailDownloadsOnTranscodingFailures()
                        && !FLAG_SUCCESS.isSet(transcodeResult.getFlags())) {
                        return transcodeResult;
                    }
                }

                success = true;
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
                .filter(path -> !path.equals(tmpPath.toPath())
                && !path.toString().contains(FileUtils.TMP_FILE_IDENTIFIER)
                && !path.toString().endsWith(".lock"))
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

                        Optional<LocalDateTime> uploadTime = FileUtils.readLastModifiedTime(path);

                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        // If upload-time is null, this is no-op
                        // if the 'use upload time as created time' setting is off, this
                        // sets the file to the current local time
                        updateFileTimes(entry, targetPath, uploadTime.orElseGet(entry::getUploadTime));

                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                File deepestFile = deepestDir.toFile();
                entry.getFinalMediaFiles().add(deepestFile);

                if (settings().isFileDeduplication()) {
                    entry.updateStatus(DownloadStatusEnum.DEDUPLICATING, l10n("gui.deduplication.deduplicating"));

                    DirectoryDeduplicator.deduplicateDirectory(deepestFile);
                    entry.getFinalMediaFiles().removeIf(file -> !file.exists());
                }
            }
        } catch (IOException e) {
            log.error("Failed to process media files", e);
        }
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, ProcessArguments arguments) throws Exception {
        long start = System.currentTimeMillis();

        ProcessArguments finalArgs = new ProcessArguments(arguments, entry.getUrl());

        entry.setLastCommandLine(finalArgs, true);

        CancelHook cancelHook = entry.getCancelHook().derive(manager::isRunning, true);
        Process process = main.getProcessMonitor().startProcess(finalArgs, cancelHook);
        entry.setProcess(process);

        String lastOutput = "";

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while (!cancelHook.get() && process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedException("Download interrupted");
                }

                if (reader.ready()) {
                    if ((line = reader.readLine()) != null) {
                        lastOutput = truncateLine(line);

                        processProgress(entry, lastOutput);
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.debug("Sleep interrupted, closing process");
                        process.destroyForcibly();
                    }
                }
            }

            long stopped = System.currentTimeMillis() - start;

            if (cancelHook.get()) {
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process halted after {}ms.", stopped);
                }

                return null;
            } else {
                int exitCode = process.waitFor();
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process took {}ms, exit code: {}", stopped, exitCode);
                }

                return new Pair<>(exitCode, lastOutput);
            }
        } catch (IOException e) {
            log.info("IO error: {}", e.getMessage());

            return null;
        } finally {
            entry.getDownloadStarted().set(false);

            // Our ProcessMonitor will take care of closing the underlying process.
        }
    }

    private void processProgress(QueueEntry entry, String lastOutput) {
        if (main.getConfig().isDebugMode()) {
            log.debug("[{}] - {}", entry.getDownloadId(), lastOutput);
        }

        if (lastOutput.startsWith("#") || lastOutput.startsWith(entry.getTmpDirectory().getAbsolutePath())) {
            entry.getMediaCard().setPercentage(-1);
            entry.updateStatus(DownloadStatusEnum.DOWNLOADING,
                StringUtils.getStringAfterLastSeparator(lastOutput
                    .replace(entry.getTmpDirectory().getAbsolutePath() + File.separator, "")), false);
        } else {
            if (lastOutput.contains("Waiting") && lastOutput.contains("rate limit")) {
                entry.markRateLimited();

                entry.updateStatus(DownloadStatusEnum.WAITING, lastOutput);
                return;
            }

            if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {

    }
}
