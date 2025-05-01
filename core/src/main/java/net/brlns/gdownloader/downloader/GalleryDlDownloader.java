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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryDeduplicator;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.StringUtils;

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

    public GalleryDlDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent()
            && main.getConfig().isGalleryDlEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.GALLERY_DL;
    }

    @Override
    public boolean isMainDownloader() {
        return false;
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
        return isEnabled()
            && !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/"));
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        try {
            String url = queueEntry.getUrl();

            Optional<MediaInfo> mediaInfoOptional = manager.getMetadataManager().fetchMetadata(url);

            if (mediaInfoOptional.isPresent()) {
                MediaInfo mediaInfo = mediaInfoOptional.get();
                queueEntry.setMediaInfo(mediaInfo);

                PersistenceManager persistence = main.getPersistenceManager();
                if (!queueEntry.getCancelHook().get() && persistence.isInitialized()) {
                    persistence.getMediaInfos().addMediaInfo(mediaInfo.toEntity(queueEntry.getDownloadId()));
                }

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
        if (!main.getConfig().isGalleryDlEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        AbstractUrlFilter filter = entry.getFilter();

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        ProcessArguments genericArguments = new ProcessArguments(
            executablePath.get().getAbsolutePath(),
            "--no-colors");

        if (!main.getConfig().isRespectGalleryDlConfigFile()) {
            genericArguments.add("--config-ignore");
        }

        Optional<File> ffmpegPath = main.getFfmpegTranscoder().getFfmpegPath();
        if (ffmpegPath.isPresent()) {
            genericArguments.add(
                "-o",
                String.format(
                    "downloader.ytdl.raw-options={\"ffmpeg-location\": \"%s\"}",
                    ffmpegPath.get().getAbsolutePath()
                )
            );
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != GALLERY
                || !main.getConfig().isGalleryDlEnabled()) {
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
                if (main.getConfig().isGalleryDlTranscoding()) {
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
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "GalleryDL");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

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
                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                File deepestFile = deepestDir.toFile();
                entry.getFinalMediaFiles().add(deepestFile);

                if (main.getConfig().isGalleryDlDeduplication()) {
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
                        lastOutput = line;

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
