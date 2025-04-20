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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.ffmpeg.enums.AudioCodecEnum;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.FileUtils;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractDownloader {

    protected final GDownloader main;
    protected final DownloadManager manager;

    public AbstractDownloader(DownloadManager managerIn) {
        main = managerIn.getMain();
        manager = managerIn;
    }

    public abstract boolean isEnabled();

    protected abstract boolean canConsumeUrl(String inputUrl);

    protected abstract boolean tryQueryMetadata(QueueEntry queueEntry);

    protected abstract DownloadResult tryDownload(QueueEntry entry) throws Exception;

    protected abstract void processMediaFiles(QueueEntry entry);

    public abstract Optional<File> getExecutablePath();

    public abstract void setExecutablePath(Optional<File> file);

    public abstract boolean isMainDownloader();

    public abstract List<DownloadTypeEnum> getArchivableTypes();

    public abstract void removeArchiveEntry(QueueEntry queueEntry);

    public abstract DownloaderIdEnum getDownloaderId();

    public List<DownloadTypeEnum> getDownloadTypes() {
        return DownloadTypeEnum.getForDownloaderId(getDownloaderId());
    }

    @PreDestroy
    public abstract void close();

    @Nullable
    public File getArchiveFile(DownloadTypeEnum downloadType) {
        List<DownloadTypeEnum> supported = getArchivableTypes();

        if (supported.contains(downloadType)) {
            File oldArchive = new File(GDownloader.getWorkDirectory(),
                getDownloaderId().getDisplayName()
                + "_archive.txt");

            File newArchive = new File(GDownloader.getWorkDirectory(),
                getDownloaderId().getDisplayName()
                + "_archive_"
                + downloadType.name().toLowerCase()
                + ".txt");

            if (oldArchive.exists()) {
                oldArchive.renameTo(newArchive);
            }

            return FileUtils.getOrCreate(newArchive);
        }

        return null;
    }

    protected File getCookieJarFileLocation() {
        return new File(GDownloader.getWorkDirectory(),
            getDownloaderId().getDisplayName() + "_cookies.txt");
    }

    @Nullable
    public File getCookieJarFile() {
        if (!main.getConfig().isReadCookiesFromCookiesTxt()) {
            return null;
        }

        File cookieJar = getCookieJarFileLocation();

        try {
            if (cookieJar.exists() && cookieJar.isFile()) {
                if (cookieJar.length() > 0) {
                    return cookieJar;
                }
            } else {
                if (cookieJar.createNewFile()) {
                    log.info("Created empty {} cookies.txt file at: {}",
                        getDownloaderId().getDisplayName(), cookieJar);
                }
            }
        } catch (IOException e) {
            GDownloader.handleException(e);
        }

        return null;
    }

    protected DownloadResult transcodeMediaFiles(QueueEntry entry) {
        if (!main.getFfmpegTranscoder().hasFFmpeg()) {
            // FFmpeg not found, nothing we can do.
            log.error("FFmpeg not found, unable to transcode media files");
            return new DownloadResult(FLAG_SUCCESS);
        }

        QualitySettings quality = entry.getFilter().getActiveQualitySettings(main.getConfig());
        if (quality.getVideoContainer() == VideoContainerEnum.GIF) {
            // Not implemented, not supported. Just give up and smile.
            return new DownloadResult(FLAG_SUCCESS);
        }

        try {
            File tmpPath = entry.getTmpDirectory();
            List<Path> paths = Files.walk(tmpPath.toPath())
                .filter(path -> !path.equals(tmpPath.toPath())
                && !Files.isDirectory(path)
                && VideoContainerEnum.isFileType(path.toFile()))
                .collect(Collectors.toList());

            AtomicReference<String> lastOutput = new AtomicReference<>();

            FFmpegConfig config;
            if (!quality.isEnableTranscoding() && main.getConfig().isTranscodeAudioToAAC()) {
                config = FFmpegConfig.builder()
                    .audioCodec(AudioCodecEnum.AAC)
                    .audioBitrate(AudioBitrateEnum.BITRATE_256).build();
            } else if (quality.isEnableTranscoding()) {
                // Copy the config, so we can make changes to the selected container
                config = GDownloader.OBJECT_MAPPER.convertValue(
                    quality.getTranscodingSettings(), FFmpegConfig.class);
            } else {
                return new DownloadResult(FLAG_SUCCESS);
            }

            if (config.getVideoContainer().isDefault()) {
                config.setVideoContainer(quality.getVideoContainer());
            }

            Set<File> toDelete = new HashSet<>();
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();
            for (Path path : paths) {
                File inputFile = path.toFile();
                String expectedExtension = config.getVideoContainer().getValue();
                File tmpFile = FileUtils.deriveTempFile(inputFile, expectedExtension);
                File lockFile = FileUtils.deriveFile(inputFile, "", "lock");

                try {
                    if (lockFile.exists()) {
                        log.info("Skipping {} as it's already been transcoded", path);
                        if (!inputFile.getName().endsWith(expectedExtension)) {
                            toDelete.add(inputFile);
                        }
                        continue;
                    }

                    entry.updateStatus(DownloadStatusEnum.TRANSCODING, l10n("gui.transcode.starting"));

                    CancelHook cancelHook = entry.getCancelHook().derive(manager::isRunning, true);
                    int exitCode = manager.getMain().getFfmpegTranscoder().startTranscode(
                        config, inputFile, tmpFile, cancelHook,
                        (output, hasTaskStarted, progress) -> {
                            lastOutput.set(output);

                            if (hasTaskStarted) {
                                entry.getMediaCard().setPercentage(progress);
                                entry.updateStatus(DownloadStatusEnum.TRANSCODING, output, false);
                            }
                        }
                    );

                    if (cancelHook.get()) {
                        return new DownloadResult(FLAG_STOPPED);
                    }

                    if (exitCode == 0) {
                        if (!tmpFile.exists()) {
                            log.error("Transcoding error, output file is missing - exit code: {}", exitCode);

                            return new DownloadResult(FLAG_TRANSCODING_FAILED, lastOutput.get());
                        }

                        log.info("Transcoding successful - exit code: {}", exitCode);

                        String prefix = "";
                        if (!main.getConfig().isKeepRawMediaFilesAfterTranscode()) {
                            Files.deleteIfExists(inputFile.toPath());
                        } else {
                            prefix = config.getFileSuffix();
                        }

                        File finalFile = FileUtils.deriveFile(inputFile, prefix, expectedExtension);
                        tmpFile.renameTo(finalFile);

                        lockFile.createNewFile();
                        toDelete.add(lockFile);

                        if (!finalFile.equals(inputFile) && !inputFile.exists()) {
                            // Create a placeholder to prevent downloaders from downloading the file again
                            int exCode = main.getFfmpegTranscoder().generateEmptyContainer(inputFile);
                            if (exCode == 0) {
                                toDelete.add(inputFile);
                            }
                        }

                        successCount.incrementAndGet();
                    } else if (exitCode > 0) {
                        log.error("FFmpeg transcoding error - exit code: {}", exitCode);
                        failureCount.incrementAndGet();
                    } else if (exitCode < 0) {
                        log.info("Transcoding not required - exit code: {}", exitCode);
                    }
                } catch (Exception e) {
                    log.error("Failed to transcode media file: {}", path, e);
                    failureCount.incrementAndGet();
                    lastOutput.set(e.getMessage());
                }
            }

            // We're now past all cancel hooks, we're safe to discard temporary files
            for (File file : toDelete) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException e) {
                    log.error("Failed to delete temporary file {}: {}", file, e.getMessage());
                }
            }

            log.info("Transcoding results: Successes: {} Failures: {}",
                successCount.get(), failureCount.get());

            if (successCount.get() > 0) {
                // Consider it a success if at least one transcode suceeded
                // lets not discard an entire playlist because of one broken file
                // TODO: still, we need to notify the user about such failures
                return new DownloadResult(FLAG_SUCCESS);
            } else if (failureCount.get() > 0) {
                return new DownloadResult(FLAG_TRANSCODING_FAILED, lastOutput.get());
            }

            return new DownloadResult(FLAG_SUCCESS);
        } catch (IOException e) {
            log.error("Failed to scan media files", e);
            return new DownloadResult(FLAG_TRANSCODING_FAILED, e.getMessage());
        }
    }
}
