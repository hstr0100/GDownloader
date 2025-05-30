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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.*;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.persistence.entity.QueueEntryEntity;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.ui.menu.*;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.ImageUtils;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;
import net.brlns.gdownloader.util.collection.ConcurrentLinkedHashSet;

import static net.brlns.gdownloader.downloader.enums.DownloadStatusEnum.*;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.COMPLETED;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.FAILED;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.QUEUED;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.FileUtils.isFileType;
import static net.brlns.gdownloader.util.FileUtils.isMimeType;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class QueueEntry {

    private final GDownloader main;

    private final MediaCard mediaCard;
    private final String filterId;
    private final AbstractUrlFilter originalFilter;
    private final String originalUrl;
    private final String url;
    private final long downloadId;
    private final List<AbstractDownloader> downloaders;

    private final List<DownloaderIdEnum> downloaderBlacklist = new CopyOnWriteArrayList<>();

    private final AtomicReference<DownloaderIdEnum> forcedDownloader
        = new AtomicReference<>(null);

    @Setter
    private QueueSortOrderEnum temporarySortOrder = QueueSortOrderEnum.SEQUENCE;

    @Setter
    private Long currentSequence;

    @Setter
    private DownloaderIdEnum currentDownloader;

    private DownloadTypeEnum currentDownloadType;

    @Setter
    private QueueCategoryEnum currentQueueCategory;

    private DownloadPriorityEnum downloadPriority
        = DownloadPriorityEnum.NORMAL;

    private DownloadStatusEnum downloadStatus;
    private String lastStatusMessage;

    private final AtomicBoolean downloadStarted = new AtomicBoolean(false);
    private final AtomicBoolean downloadSkipped = new AtomicBoolean(false);

    private final CancelHook cancelHook = new CancelHook();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean queried = new AtomicBoolean(false);
    private final AtomicInteger retryCounter = new AtomicInteger();

    private MediaInfo mediaInfo;

    @Setter
    private File tmpDirectory;
    private final Set<File> finalMediaFiles = new HashSet<>();

    private final List<String> thumbnailUrls = new CopyOnWriteArrayList<>();
    private final List<String> lastCommandLine = new CopyOnWriteArrayList<>();

    private final ConcurrentLinkedHashSet<String> errorLog = new ConcurrentLinkedHashSet<>();
    private final ConcurrentLinkedHashSet<String> downloadLog = new ConcurrentLinkedHashSet<>();

    @Setter
    private Process process;

    public AbstractUrlFilter getFilter() {
        Optional<AbstractUrlFilter> filter = main.getConfig().getUrlFilterById(filterId);
        if (filter.isPresent()) {
            return filter.get();
        }

        return originalFilter;
    }

    public void openUrl() {
        main.openUrlInBrowser(originalUrl);
    }

    public void dispose(CloseReasonEnum closeReason) {
        main.getGuiManager().getMediaCardManager()
            .removeMediaCard(mediaCard.getId(), closeReason);
    }

    public void tryOpenMediaFiles() {
        if (play(VideoContainerEnum.class)
            || play(AudioContainerEnum.class)
            || play(ImageContainerEnum.class)) {
            return;
        }

        for (File file : finalMediaFiles) {
            if (file.isDirectory()) {
                main.open(file);
                return;
            }
        }

        main.openDownloadsDirectory();
    }

    private <T extends Enum<T> & IContainerEnum> boolean play(Class<T> container) {
        return play(container, true);
    }

    private <T extends Enum<T> & IContainerEnum> boolean play(
        Class<T> container, boolean canOpenPlaylist) {
        List<File> matchingFiles = new ArrayList<>();

        for (File file : finalMediaFiles) {
            if (file.isFile() && isMediaType(file, container)) {
                matchingFiles.add(file);
            }
        }

        if (!matchingFiles.isEmpty()) {
            if (matchingFiles.size() > 1 && canOpenPlaylist) {
                main.openPlaylist(matchingFiles);
            } else {
                main.open(matchingFiles.get(0));
            }

            return true;
        }

        return false;
    }

    private <T extends Enum<T> & IContainerEnum> boolean isMediaType(File file, Class<T> enumClass) {
        for (T container : enumClass.getEnumConstants()) {
            if (isFileType(file, ((IContainerEnum)container).getValue())) {
                return true;
            }

            if (isMimeType(file, ((IContainerEnum)container).getMimeTypePrefix())) {
                return true;
            }
        }

        return false;
    }

    public void openThumbnail(BufferedImage image) {
        File file = ImageUtils.writeImageToTempFile(image);
        if (file != null) {
            main.open(file);
        }
    }

    public void recreateQueueEntry() {
        dispose(CloseReasonEnum.MANUAL);

        main.getDownloadManager().captureUrl(url, true);
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

    public void deleteMediaFiles() {
        boolean success = false;

        for (File file : finalMediaFiles) {
            try {
                if (file.isFile() && Files.deleteIfExists(file.toPath())) {
                    success = true;
                }
            } catch (IOException e) {
                GDownloader.handleException(e);
            }
        }

        for (File file : finalMediaFiles) {
            try {
                if (file.isDirectory() && Files.deleteIfExists(file.toPath())) {
                    success = true;
                }
            } catch (DirectoryNotEmptyException e) {
                log.warn("Directory {} is not empty, ignoring...", file);
            } catch (IOException e) {
                GDownloader.handleException(e);
            }
        }

        finalMediaFiles.clear();

        ToastMessenger.show(Message.builder()
            .message(success
                ? "gui.delete_files.deleted"
                : "gui.delete_files.no_files")
            .durationMillis(3000)
            .messageType(MessageTypeEnum.INFO)
            .discardDuplicates(true)
            .build());
    }

    public boolean isRunning() {
        return running.get();
    }

    public void cleanDirectories() {
        if (tmpDirectory != null && tmpDirectory.exists()) {
            DirectoryUtils.deleteRecursively(tmpDirectory.toPath());
        }
    }

    public void close(CloseReasonEnum reason) {
        cancelHook.set(true);

        if (process != null) {
            process.destroy();
        }

        if (reason != CloseReasonEnum.SHUTDOWN) {
            cleanDirectories();
        }
    }

    public void resetForRestart() {
        downloadStarted.set(false);
        cancelHook.set(false);
        process = null;
    }

    public void resetRetryCounter() {
        retryCounter.set(0);
    }

    public void markQueried() {
        queried.set(true);
    }

    public void setCurrentDownloadType(DownloadTypeEnum typeIn) {
        currentDownloadType = typeIn;

        mediaCard.setPlaceholderIcon(typeIn);
    }

    public void setDownloadPriority(DownloadPriorityEnum priorityIn) {
        downloadPriority = priorityIn;

        mediaCard.setPriorityIcon(priorityIn);
    }

    public boolean isSkipped() {
        return downloadSkipped.get();
    }

    public void setSkipped(boolean skip) {
        downloadSkipped.set(skip);

        updateExtraRightClickOptions();
    }

    @Nullable
    public BufferedImage getValidFullResThumbnail() {
        return thumbnailUrls.stream()
            .map(this::tryLoadThumbnail)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public LocalDateTime getUploadTime() {
        if (mediaInfo == null) {
            return null;
        }

        LocalDateTime localDateTime = mediaInfo.getUploadDateAsLocalDateTime();
        if (localDateTime != null) {
            return localDateTime;
        }

        LocalDate localDate = mediaInfo.getUploadDateAsLocalDate();
        if (localDate != null) {
            return localDate.atStartOfDay();
        }

        return null;
    }

    public boolean isPlaylist() {
        if (mediaInfo == null) {
            return false;
        }

        return notNullOrEmpty(mediaInfo.getPlaylistTitle());
    }

    public void setMediaInfo(MediaInfo mediaInfoIn) {
        mediaInfo = mediaInfoIn;

        markQueried();

        if (notNullOrEmpty(mediaInfo.getTitle())) {
            logOutput("Title: " + mediaInfo.getTitle());
        }

        if (notNullOrEmpty(mediaInfo.getPlaylistTitle())) {
            logOutput("Playlist Title: " + mediaInfo.getPlaylistTitle());
        }

        if (nullOrEmpty(mediaInfo.getHostDisplayName())) {
            String displayName = Optional.ofNullable(
                originalFilter.getClass() == GenericFilter.class
                ? URLUtils.getHostName(url)
                : originalFilter.getDisplayName()
            ).map(host -> notNullOrEmpty(mediaInfo.getExtractorKey())
                ? host + " [" + mediaInfo.getExtractorKey() + "]"
                : host
            ).orElse(notNullOrEmpty(mediaInfo.getExtractorKey())
                ? mediaInfo.getExtractorKey()
                : null
            );

            Optional.ofNullable(displayName).ifPresent(mediaInfo::setHostDisplayName);
        }

        String base64encoded = mediaInfo.getBase64EncodedThumbnail();

        Optional<BufferedImage> optional = Optional.ofNullable(
            ImageUtils.base64ToBufferedImage(base64encoded)
        ).or(() -> {
            return mediaInfo.supportedThumbnails()
                .limit(5)
                .map(this::tryLoadThumbnail)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        });

        optional.ifPresentOrElse(
            img -> {
                // Downscale thumbnails to save space and resources, we don't need the full resolution here.
                BufferedImage downscaledImage = ImageUtils.downscaleImage(img, 240);

                if (base64encoded.isEmpty()) {
                    mediaInfo.setBase64EncodedThumbnail(
                        ImageUtils.bufferedImageToBase64(downscaledImage, "jpg"));
                }

                mediaCard.setThumbnailAndDuration(downscaledImage, mediaInfo.getDuration());
            },
            () -> {
                if (log.isDebugEnabled()) {
                    log.error("Failed to load a valid thumbnail");
                }
            }
        );

        mediaInfo.bestThumbnails()
            .forEach(thumbnailUrls::add);
    }

    private Optional<BufferedImage> tryLoadThumbnail(String url) {
        try {
            String urlWithoutQuery = URLUtils.removeQueryParameters(url);
            if (log.isDebugEnabled()) {
                log.debug("Trying to load thumbnail {}", urlWithoutQuery);
            }

            BufferedImage img = ImageIO.read(new URI(urlWithoutQuery).toURL());
            if (img != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Thumbnail Resolution: {}x{}", img.getWidth(), img.getHeight());
                }

                img = ImageUtils.cropToSixteenByNineIfHorizontal(img);

                return Optional.of(img);
            } else if (log.isDebugEnabled()) {
                log.error("ImageIO.read returned null for {}", url);
            }
        } catch (IOException | URISyntaxException e) {
            if (log.isDebugEnabled()) {
                log.error("ImageIO.read exception {}", url, e);
            }
        }

        return Optional.empty();
    }

    private String getTitle() {
        // Emojis won't render, but will successfully distort the layout.
        return StringUtils.removeEmojis(getRawTitle());
    }

    private String getRawTitle() {
        if (mediaInfo != null) {
            // Give priority to playlist titles
            if (notNullOrEmpty(mediaInfo.getPlaylistTitle())) {
                return l10n("gui.playlist") + " " + mediaInfo.getPlaylistTitle();
            }

            if (notNullOrEmpty(mediaInfo.getTitle())) {
                return mediaInfo.getTitle();
            }
        }

        return url.replace("https://", "").replace("www.", "");
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

    private String getHostDisplayName() {
        if (mediaInfo != null && notNullOrEmpty(mediaInfo.getHostDisplayName())) {
            return mediaInfo.getHostDisplayName();
        } else {
            return originalFilter.getDisplayName();
        }
    }

    public double getPerceivedPercentage() {
        QueueCategoryEnum category = getCurrentQueueCategory();
        if (category == null) {
            return -1d;
        }

        return switch (category) {
            case FAILED, COMPLETED ->
                100d;
            case QUEUED ->
                0d;
            default -> {
                if (getDownloadStatus() == DOWNLOADING || getDownloadStatus() == TRANSCODING) {
                    yield getMediaCard().getPercentage();
                } else {
                    yield 0d;// Consider all other statuses as zero
                }
            }
        };
    }

    public void updateStatus(DownloadStatusEnum status, String text) {
        updateStatus(status, text, true);
    }

    public void updateStatus(DownloadStatusEnum status, String text, boolean log) {
        if (!text.isEmpty()) {
            if (log) {
                logOutput(text);
            }

            lastStatusMessage = text;

            String topText = getHostDisplayName();

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

            mediaCard.setLabel(topText, getTitle(), text);
            mediaCard.setTooltip(text);
        }

        updateStatus(status);
    }

    public void updateStatus(DownloadStatusEnum status) {
        if (status != downloadStatus) {
            downloadStatus = status;

            switch (status) {
                case QUERYING -> {
                    logOutput("Url: " + originalUrl);
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.MAGENTA);
                }
                case PROCESSING, POST_PROCESSING, DEDUPLICATING -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.ORANGE);
                }
                case WAITING -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), new Color(30, 136, 229));
                }
                case PREPARING, QUEUED, STOPPED -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), Color.GRAY);
                }
                case SKIPPED -> {
                    mediaCard.setPercentage(100);
                    mediaCard.setProgressBarTextAndColors(status.getDisplayName(), new Color(61, 61, 61));
                }
                case DOWNLOADING, TRANSCODING -> {
                    if (mediaCard.getPercentage() >= 0) {
                        mediaCard.setPercentage(0);
                    }

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
        } else if (status == DownloadStatusEnum.DOWNLOADING || status == DownloadStatusEnum.TRANSCODING) {
            mediaCard.setProgressBarText(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%");
        }
    }

    public void setLastCommandLine(List<String> commandLineArguments) {
        setLastCommandLine(commandLineArguments, false);
    }

    public void setLastCommandLine(List<String> commandLineArguments, boolean shouldLog) {
        if (commandLineArguments.isEmpty()) {
            return;
        }

        lastCommandLine.clear();
        lastCommandLine.addAll(commandLineArguments);

        if (shouldLog) {
            String builtCommandLine = StringUtils.escapeAndBuildCommandLine(lastCommandLine);
            logOutput("Command line: " + builtCommandLine);

            if (log.isDebugEnabled()) {
                log.debug("[Dispatch {}]: Type: {} Filter: {} CLI: {}",
                    downloadId,
                    currentDownloadType,
                    originalFilter.getDisplayName(),
                    builtCommandLine);
            }
        }

        updateExtraRightClickOptions();
    }

    public void logError(String output) {
        if (output.isEmpty()) {
            return;
        }

        if (!errorLog.isEmpty()) {
            updateExtraRightClickOptions();
        }

        errorLog.remove(output);
        errorLog.add(output);// Re-add at the bottom
    }

    public void logOutput(String output) {
        if (output.isEmpty()) {
            return;
        }

        if (!downloadLog.isEmpty()) {
            updateExtraRightClickOptions();
        }

        downloadLog.remove(output);
        downloadLog.add(output);
    }

    public void addRightClick(String key, Runnable runnable) {
        addRightClick(key, new RunnableMenuEntry(runnable));
    }

    public void addRightClick(String key, IMenuEntry entry) {
        mediaCard.getRightClickMenu().put(key, entry);
    }

    public void addRightClick(Map<String, IMenuEntry> input) {
        mediaCard.getRightClickMenu().putAll(input);
    }

    public void removeRightClick(String key) {
        mediaCard.getRightClickMenu().remove(key);
    }

    public void clearRightClick() {
        mediaCard.getRightClickMenu().clear();
    }

    protected void createDefaultRightClick(DownloadManager manager) {
        Map<String, IMenuEntry> menu = new LinkedHashMap<>();

        menu.put(l10n("gui.open_downloads_directory"),
            new SingleActionMenuEntry(() -> main.openDownloadsDirectory()));
        menu.put(l10n("gui.open_in_browser"),
            new RunnableMenuEntry(() -> openUrl()));
        menu.put(l10n("gui.copy_url"),
            new MultiActionMenuEntry<>(() -> originalUrl, (entries) -> {
                List<String> finalText = entries.stream()
                    .collect(Collectors.toList());

                main.getClipboardManager().copyTextToClipboard(finalText);
            }));

        NestedMenuEntry downloadersSubmenu = new NestedMenuEntry();

        for (AbstractDownloader downloader : getDownloaders()) {
            DownloaderIdEnum downloaderId = downloader.getDownloaderId();
            downloadersSubmenu.put(
                downloaderId.getDisplayName(),
                new RunnableMenuEntry(() -> {
                    setForcedDownloader(downloaderId);
                    setCurrentDownloader(downloaderId);
                    manager.stopDownload(this, () -> {
                        manager.requeueEntry(this);
                    });
                })
            );
        }

        if (!downloadersSubmenu.isEmpty()) {
            menu.put(l10n("gui.download_with"),
                downloadersSubmenu);
        }

        clearRightClick();

        addRightClick(menu);

        updateMediaRightClickOptions();
        updateExtraRightClickOptions();
    }

    public void updateExtraRightClickOptions() {
        DownloadManager manager = main.getDownloadManager();

        NestedMenuEntry extrasSubmenu = new NestedMenuEntry();

        if (mediaCard.getThumbnailImage() != null) {
            extrasSubmenu.put(l10n("gui.view_thumbnail"),
                new RunnableMenuEntry(() -> {
                    BufferedImage thumbnailImage = getValidFullResThumbnail();
                    if (thumbnailImage != null) {
                        openThumbnail(thumbnailImage);
                    } else {
                        log.warn("Could not load a valid full resolution thumbnail.");
                        openThumbnail(mediaCard.getThumbnailImage());
                    }
                }));
        }

        if (!lastCommandLine.isEmpty()) {
            extrasSubmenu.put(l10n("gui.copy_command_line"),
                constructCommandLineMenu(lastCommandLine));
        }

        if (!errorLog.isEmpty()) {
            extrasSubmenu.put(l10n("gui.copy_error_log"),
                constructLogMenu(errorLog));
        }

        if (!downloadLog.isEmpty()) {
            extrasSubmenu.put(l10n("gui.copy_download_log"),
                constructLogMenu(downloadLog));
        }

        extrasSubmenu.put(l10n("gui.recreate_entry"),
            new RunnableMenuEntry(() -> recreateQueueEntry()));

        extrasSubmenu.put(l10n("gui.remove_entry"),
            new RunnableMenuEntry(()
                -> dispose(CloseReasonEnum.MANUAL)));

        if (currentQueueCategory == QueueCategoryEnum.QUEUED
            || currentQueueCategory == QueueCategoryEnum.RUNNING) {
            boolean skipped = isSkipped();
            extrasSubmenu.put(skipped
                ? l10n("gui.skip.unskip_download")
                : l10n("gui.skip.skip_download"),
                new RunnableMenuEntry(()
                    -> manager.setSkipDownload(this, !skipped)));
        }

        if (!finalMediaFiles.isEmpty()) {
            extrasSubmenu.put(l10n("gui.delete_files_and_remove"),
                new RunnableMenuEntry(() -> {
                    deleteMediaFiles();
                    dispose(CloseReasonEnum.MANUAL);
                }));
        }

        if (!extrasSubmenu.isEmpty()) {
            addRightClick(l10n("gui.more_options"),
                extrasSubmenu);
        }
    }

    public NestedMenuEntry getDownloadPriorityMenu(DownloadManager manager) {
        NestedMenuEntry submenu = new NestedMenuEntry();
        for (DownloadPriorityEnum priority : DownloadPriorityEnum.values()) {
            submenu.put(priority.getDisplayName(),
                new RunnableMenuEntry(() -> {
                    manager.updatePriority(this, priority);
                }, () -> priority.getIconAsset()));
        }

        return submenu;
    }

    public void updateMediaRightClickOptions() {
        Runnable removeAction = () -> Stream.of(
            "gui.delete_files",
            "gui.play_video",
            "gui.play_audio",
            "gui.view_image",
            "gui.open_downloaded_directory",
            "gui.open_as_audio_playlist",
            "gui.open_as_video_playlist"
        ).forEach(action -> removeRightClick(l10n(action)));

        if (!finalMediaFiles.isEmpty()) {
            addRightClick(l10n("gui.delete_files"), () -> {
                deleteMediaFiles();

                removeAction.run();
            });

            int audioMediaCount = 0;
            int videoMediaCount = 0;

            for (File file : finalMediaFiles) {
                if (file.isFile()) {
                    if (addMediaAction(file, VideoContainerEnum.class, "gui.play_video")) {
                        videoMediaCount++;
                    }

                    if (addMediaAction(file, AudioContainerEnum.class, "gui.play_audio")) {
                        audioMediaCount++;
                    }

                    addMediaAction(file, ImageContainerEnum.class, "gui.view_image");
                } else if (file.isDirectory()) {
                    addRightClick(l10n("gui.open_downloaded_directory"),
                        () -> main.open(file));
                }
            }

            if (audioMediaCount >= 2) {
                removeRightClick(l10n("gui.play_audio"));
                addRightClick(l10n("gui.open_as_audio_playlist"), () -> {
                    List<File> playableFiles = finalMediaFiles.stream()
                        .filter(file -> file.isFile() && isMediaType(file, AudioContainerEnum.class))
                        .collect(Collectors.toList());

                    main.openPlaylist(playableFiles);
                });
            }

            if (videoMediaCount >= 2) {
                removeRightClick(l10n("gui.play_video"));
                addRightClick(l10n("gui.open_as_video_playlist"), () -> {
                    List<File> playableFiles = finalMediaFiles.stream()
                        .filter(file -> file.isFile() && isMediaType(file, VideoContainerEnum.class))
                        .collect(Collectors.toList());

                    main.openPlaylist(playableFiles);
                });
            }
        } else {
            removeAction.run();
        }
    }

    private <T extends Enum<T> & IContainerEnum> boolean addMediaAction(File file, Class<T> enumClass, String actionKey) {
        if (isMediaType(file, enumClass)) {
            addRightClick(l10n(actionKey), () -> {
                if (!play(enumClass, false)) {
                    main.openDownloadsDirectory();
                }
            });

            return true;
        }

        return false;
    }

    private IMenuEntry constructLogMenu(ConcurrentLinkedHashSet<String> logEntries) {
        return new MultiActionMenuEntry<>(() -> logEntries.snapshotAsList(), (entries) -> {
            List<String> finalText = new ArrayList<>();

            for (List<String> entry : entries) {
                StringBuilder builder = new StringBuilder();
                for (String line : entry) {
                    builder.append(line).append(System.lineSeparator());
                }

                if (entries.size() > 1) {
                    builder.append("---");
                }

                finalText.add(builder.toString());
            }

            main.getClipboardManager().copyTextToClipboard(finalText);
        });
    }

    private IMenuEntry constructCommandLineMenu(List<String> commandLineArguments) {
        return new MultiActionMenuEntry<>(() -> commandLineArguments, (entries) -> {
            List<String> finalText = new ArrayList<>();

            for (List<String> entry : entries) {
                if (entry.isEmpty()) {
                    continue;
                }

                finalText.add(StringUtils.escapeAndBuildCommandLine(entry));
            }

            main.getClipboardManager().copyTextToClipboard(finalText);
        });
    }

    public QueueEntryEntity toEntity() {
        QueueEntryEntity entity = new QueueEntryEntity();

        entity.setOriginalUrl(getOriginalUrl());
        entity.setUrl(getUrl());
        entity.setDownloadId(getDownloadId());
        entity.setFilterId(getFilterId());
        entity.setFilter(getOriginalFilter());

        entity.getDownloaderBlacklist().addAll(getDownloaderBlacklist());

        if (getMediaInfo() != null) {
            entity.setMediaInfo(getMediaInfo().toEntity(getDownloadId()));
        }

        entity.setForcedDownloader(getForcedDownloader());

        entity.setCurrentDownloader(getCurrentDownloader());
        entity.setCurrentDownloadType(getCurrentDownloadType());
        entity.setCurrentQueueCategory(getCurrentQueueCategory());
        entity.setCurrentDownloadPriority(getDownloadPriority());
        entity.setCurrentDownloadSequence(getCurrentSequence());
        entity.setDownloadStatus(getDownloadStatus());
        entity.setLastStatusMessage(getLastStatusMessage());

        entity.setDownloadStarted(getDownloadStarted().get());
        entity.setDownloadSkipped(getDownloadSkipped().get());
        // Runtime properties, do not save.
        //entity.setCancelHook(getCancelHook().get());
        //entity.setRunning(isRunning());

        entity.setRetryCounter(getRetryCounter().get());
        entity.setQueried(getQueried().get());

        if (getTmpDirectory() != null) {
            entity.setTmpDirectoryPath(getTmpDirectory().getAbsolutePath());
        }

        entity.setMediaFilePaths(getFinalMediaFiles().stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.toCollection(ArrayList::new)));

        entity.setThumbnailUrls(new ArrayList<>(getThumbnailUrls()));
        entity.setLastCommandLine(new ArrayList<>(getLastCommandLine()));

        entity.setErrorLog(getErrorLog().snapshotAsList());
        entity.setDownloadLog(getDownloadLog().snapshotAsList());

        return entity;
    }

    @SuppressWarnings("deprecation")
    public static QueueEntry fromEntity(QueueEntryEntity entity, MediaCard mediaCard, List<AbstractDownloader> downloaders) {
        QueueEntry queueEntry = new QueueEntry(
            GDownloader.getInstance(),
            mediaCard,
            entity.getFilterId(),
            entity.getFilter(),
            entity.getOriginalUrl(),
            entity.getUrl(),
            entity.getDownloadId(),
            downloaders
        );

        queueEntry.resetDownloaderBlacklist();
        for (DownloaderIdEnum downloaderId : entity.getDownloaderBlacklist()) {
            queueEntry.blackListDownloader(downloaderId);
        }

        if (entity.getMediaInfo() != null) {
            queueEntry.setMediaInfo(MediaInfo.fromEntity(entity.getMediaInfo()));
        }

        queueEntry.setForcedDownloader(entity.getForcedDownloader());
        queueEntry.setCurrentDownloader(entity.getCurrentDownloader());
        queueEntry.setCurrentDownloadType(entity.getCurrentDownloadType());
        queueEntry.setCurrentQueueCategory(entity.getCurrentQueueCategory());
        // Null checks are needed here because these fields are new
        queueEntry.setDownloadPriority(entity.getCurrentDownloadPriority() != null
            ? entity.getCurrentDownloadPriority() : DownloadPriorityEnum.NORMAL);
        queueEntry.setCurrentSequence(entity.getCurrentDownloadSequence());

        if (entity.getDownloadStatus() != null && entity.getLastStatusMessage() != null) {
            queueEntry.updateStatus(entity.getDownloadStatus(), entity.getLastStatusMessage(), false);
        }

        queueEntry.getDownloadStarted().set(entity.isDownloadStarted());
        queueEntry.getDownloadSkipped().set(entity.isDownloadSkipped());
        //queueEntry.getCancelHook().set(entity.isCancelHook());
        //queueEntry.getRunning().set(entity.isRunning());
        queueEntry.getRetryCounter().set(entity.getRetryCounter());
        queueEntry.getQueried().set(entity.isQueried());

        if (entity.getTmpDirectoryPath() != null && !entity.getTmpDirectoryPath().isEmpty()) {
            queueEntry.setTmpDirectory(new File(entity.getTmpDirectoryPath()));
        }

        // Deprecated field, extract contents for migration.
        for (String path : entity.getFinalMediaFilePaths()) {
            queueEntry.getFinalMediaFiles().add(new File(path));
        }

        for (String path : entity.getMediaFilePaths()) {
            queueEntry.getFinalMediaFiles().add(new File(path));
        }

        queueEntry.getThumbnailUrls().clear();
        queueEntry.getThumbnailUrls().addAll(entity.getThumbnailUrls());

        queueEntry.setLastCommandLine(entity.getLastCommandLine());

        queueEntry.getErrorLog().clear();
        queueEntry.getErrorLog().addAll(entity.getErrorLog());

        queueEntry.getDownloadLog().clear();
        queueEntry.getDownloadLog().addAll(entity.getDownloadLog());

        return queueEntry;
    }
}
