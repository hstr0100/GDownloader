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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class GalleryDlDownloader extends AbstractDownloader {

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    @Getter
    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

    public GalleryDlDownloader(DownloadManager managerIn) {
        super(managerIn);
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
    public DownloadTypeEnum[] getDownloadTypes() {
        return new DownloadTypeEnum[] {GALLERY};
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return getExecutablePath().isPresent()
            && !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/")
            || inputUrl.endsWith(".jpg")
            || inputUrl.endsWith(".png")
            || inputUrl.endsWith(".webp"));
    }

    @Override
    protected boolean tryQueryVideo(QueueEntry queueEntry) {
        // TODO
        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        AbstractUrlFilter filter = entry.getFilter();

        if (!main.getConfig().isDownloadGallery() || !main.getConfig().isGalleryDlEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        List<String> genericArguments = new ArrayList<>();

        genericArguments.addAll(List.of(
            executablePath.get().getAbsolutePath(),
            "--no-colors"
        ));

        if (!main.getConfig().isRespectGalleryDlConfigFile()) {
            genericArguments.add("--config-ignore");
        }

        // Workaround for gallery-dl: the process always returns 1 even when downloads succeed.
        genericArguments.addAll(List.of(
            "--exec-after",
            "echo \"GD-Internal-Finished\""
        ));

        genericArguments.addAll(filter.getArguments(getDownloaderId(), ALL, main, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = Arrays.stream(getDownloadTypes())
                .anyMatch(typeIn -> typeIn == type);

            if (!supported
                || type == GALLERY && !main.getConfig().isDownloadGallery()
                || !main.getConfig().isGalleryDlEnabled()) {
                continue;
            }

            List<String> arguments = new ArrayList<>(genericArguments);

            List<String> downloadArguments = filter.getArguments(getDownloaderId(), type, main, tmpPath, entry.getUrl());
            arguments.addAll(downloadArguments);

            if (main.getConfig().isDebugMode()) {
                log.debug("ALL {}: Type {} ({}): {}",
                    genericArguments,
                    type,
                    filter.getDisplayName(),
                    downloadArguments);
            }

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
                success = true;
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected Map<String, Runnable> processMediaFiles(QueueEntry entry) {
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "GalleryDL");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

        File tmpPath = entry.getTmpDirectory();

        Map<String, Runnable> rightClickOptions = new TreeMap<>();

        try (Stream<Path> dirStream = Files.walk(tmpPath.toPath())) {
            dirStream.forEach(path -> {
                Path relativePath = tmpPath.toPath().relativize(path);
                Path targetPath = finalPath.toPath().resolve(relativePath);

                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                        log.info("Created directory: {}", path.getFileName());
                    } else {
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Copied file: {}", path.getFileName());
                    }
                } catch (FileAlreadyExistsException e) {
                    log.warn("File or directory already exists: {}", targetPath, e);
                } catch (IOException e) {
                    log.error("Failed to copy file: {}", path.getFileName(), e);
                }
            });

            entry.getFinalMediaFiles().add(finalPath);
        } catch (IOException e) {
            log.error("Failed to list files", e);
        }

        return rightClickOptions;
    }

    @Override
    protected void processProgress(QueueEntry entry, String lastOutput) {
        if (lastOutput.contains("GD-Internal-Finished")) {
            return;
        }

        if (main.getConfig().isDebugMode()) {
            log.debug("[{}] - {}", entry.getDownloadId(), lastOutput);
        }

        if (lastOutput.startsWith(entry.getTmpDirectory().getAbsolutePath())) {
            // TODO: estimate percentage
            entry.getMediaCard().setPercentage(50);

            entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput.replace(entry.getTmpDirectory().getAbsolutePath() + "/", ""));
        } else {
            if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
            }
        }
    }
}
