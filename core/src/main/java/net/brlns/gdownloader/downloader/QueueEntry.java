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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.settings.enums.IContainerEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.StringUtils;

import static net.brlns.gdownloader.downloader.enums.DownloadStatusEnum.*;
import static net.brlns.gdownloader.lang.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class QueueEntry {

    private final GDownloader main;

    private final MediaCard mediaCard;
    private final AbstractUrlFilter filter;
    private final String originalUrl;
    private final String url;
    private final int downloadId;
    private final List<AbstractDownloader> downloaders;

    private final List<DownloaderIdEnum> downloaderBlacklist = new CopyOnWriteArrayList<>();

    private final AtomicReference<DownloaderIdEnum> forcedDownloader
        = new AtomicReference<>(null);

    @Setter
    private DownloaderIdEnum currentDownloader;

    private DownloadStatusEnum downloadStatus;

    private final AtomicBoolean downloadStarted = new AtomicBoolean(false);
    private final AtomicBoolean cancelHook = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger retryCounter = new AtomicInteger();

    private MediaInfo mediaInfo;

    @Setter
    private File tmpDirectory;
    private final List<File> finalMediaFiles = new ArrayList<>();

    @Setter
    private Process process;

    public void openUrl() {
        main.openUrlInBrowser(originalUrl);
    }

    public <T extends Enum<T> & IContainerEnum> void play(Class<T> container) {
        if (!finalMediaFiles.isEmpty()) {
            for (File file : finalMediaFiles) {
                if (!file.exists()) {
                    continue;
                }

                String fileName = file.getAbsolutePath().toLowerCase();

                String[] values = IContainerEnum.getContainerValues(container);

                for (String value : values) {
                    if (fileName.endsWith("." + value)) {
                        main.open(file);
                        return;
                    }
                }
            }
        }

        main.openDownloadsDirectory();
    }

    @Nullable
    public DownloaderIdEnum getForcedDownloader() {
        return forcedDownloader.get();
    }

    public void setForcedDownloader(DownloaderIdEnum downloaderId) {
        forcedDownloader.set(downloaderId);
    }

    public void resetDownloaderBlacklist() {
        downloaderBlacklist.clear();
    }

    public boolean isDownloaderBlacklisted(DownloaderIdEnum downloaderId) {
        return downloaderBlacklist.contains(downloaderId);
    }

    public void blackListDownloader(DownloaderIdEnum downloaderId) {
        downloaderBlacklist.add(downloaderId);
    }

    public void copyUrlToClipboard() {
        main.getClipboardManager().copyTextToClipboard(originalUrl);
    }

    public void deleteMediaFiles() {
        boolean success = false;

        for (File file : finalMediaFiles) {
            try {
                if (Files.deleteIfExists(file.toPath())) {
                    success = true;
                }
            } catch (IOException e) {
                GDownloader.handleException(e);
            }
        }

        finalMediaFiles.clear();

        main.getGuiManager().showMessage(
            l10n("gui.delete_files.notification_title"),
            success ? l10n("gui.delete_files.deleted") : l10n("gui.delete_files.no_files"),
            3000,
            GUIManager.MessageType.INFO,
            false
        );
    }

    public boolean isRunning() {
        return running.get();
    }

    public void cleanDirectories() {
        if (tmpDirectory != null && tmpDirectory.exists()) {
            DirectoryUtils.deleteRecursively(tmpDirectory.toPath());
        }
    }

    public void close() {
        cancelHook.set(true);

        if (process != null) {
            process.destroy();
        }

        cleanDirectories();
    }

    public void resetForRestart() {
        downloadStarted.set(false);
        cancelHook.set(false);
        process = null;
    }

    public void resetRetryCounter() {
        retryCounter.set(0);
    }

    public void setMediaInfo(MediaInfo mediaInfoIn) {
        mediaInfo = mediaInfoIn;

        Optional<BufferedImage> optional = mediaInfo.supportedThumbnails()
            .limit(5)
            .map(urlIn -> tryLoadThumbnail(urlIn))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

        optional.ifPresentOrElse(
            img -> mediaCard.setThumbnailAndDuration(img, mediaInfo.getDuration()),
            () -> {
                if (main.getConfig().isDebugMode()) {
                    log.error("Failed to load a valid thumbnail");
                }
            }
        );
    }

    private Optional<BufferedImage> tryLoadThumbnail(String url) {
        try {
            if (main.getConfig().isDebugMode()) {
                log.debug("Trying to load thumbnail {}", url);
            }

            BufferedImage img = ImageIO.read(new URI(url).toURL());

            if (img != null) {
                return Optional.of(img);
            } else if (main.getConfig().isDebugMode()) {
                log.error("ImageIO.read returned null for {}", url);
            }
        } catch (IOException | URISyntaxException e) {
            if (main.getConfig().isDebugMode()) {
                log.error("ImageIO.read exception {}", url, e);
            }
        }

        return Optional.empty();
    }

    private String getTitle() {
        if (mediaInfo != null && !mediaInfo.getTitle().isEmpty()) {
            return StringUtils.truncate(mediaInfo.getTitle(), 40);
        }

        return StringUtils.truncate(url
            .replace("https://", "")
            .replace("www.", ""), 30);
    }

    private Optional<String> getDisplaySize() {
        if (mediaInfo != null) {
            long size = mediaInfo.getFilesizeApprox();

            // For this, we consider 0 as null.
            if (size > 0 && size < Long.MAX_VALUE) {
                return Optional.of(StringUtils.getHumanReadableFileSize(size));
            }
        }

        return Optional.empty();
    }

    public void updateStatus(DownloadStatusEnum status, String text) {
        String topText = filter.getDisplayName();

        if (status == DownloadStatusEnum.DOWNLOADING) {
            downloadStarted.set(true);
        }

        if (currentDownloader != null) {
            topText += " (" + currentDownloader.getDisplayName() + ")";
        }

        Optional<String> size = getDisplaySize();
        if (size.isPresent()) {
            topText += " (~" + size.get() + ")";
        }

        mediaCard.setLabel(topText, getTitle(),
            status != DownloadStatusEnum.DOWNLOADING ? StringUtils.truncate(text, 40) : StringUtils.truncate(text, 51));

        mediaCard.setTooltip(text);

        updateStatus(status);
    }

    public void updateStatus(DownloadStatusEnum status) {
        if (status != downloadStatus) {
            downloadStatus = status;

            switch (status) {
                case QUERYING -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.MAGENTA);
                }
                case PROCESSING, POST_PROCESSING, DEDUPLICATING -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.ORANGE);
                }
                case PREPARING, QUEUED, STOPPED -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.GRAY);
                }
                case DOWNLOADING -> {
                    mediaCard.setPercentage(0);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%",
                        new Color(255, 214, 0));
                }
                case RETRYING, STARTING -> {
                    mediaCard.setPercentage(0);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), new Color(255, 214, 0));
                }
                case COMPLETE -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), new Color(0, 200, 83));
                }
                case NO_METHOD, FAILED -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.RED);
                }
                default ->
                    throw new RuntimeException("Unhandled status: " + status);
            }
        } else if (status == DownloadStatusEnum.DOWNLOADING) {
            mediaCard.setProgressBarText(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%");
        }
    }
}
