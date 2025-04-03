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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.CloseReasonEnum;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.enums.QueueCategoryEnum;
import net.brlns.gdownloader.downloader.extractors.MetadataManager;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.IEvent;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.persistence.entity.CounterTypeEnum;
import net.brlns.gdownloader.persistence.entity.QueueEntryEntity;
import net.brlns.gdownloader.settings.enums.PlayListOptionEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;
import net.brlns.gdownloader.settings.filters.YoutubeFilter;
import net.brlns.gdownloader.settings.filters.YoutubePlaylistFilter;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.collection.ConcurrentRearrangeableDeque;
import net.brlns.gdownloader.util.collection.ExpiringSet;
import net.brlns.gdownloader.util.collection.LinkedIterableBlockingQueue;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.ALWAYS_ASK;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.DOWNLOAD_PLAYLIST;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.DOWNLOAD_SINGLE;
import static net.brlns.gdownloader.util.URLUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadManager implements IEvent {

    @Getter
    private final GDownloader main;

    private final PersistenceManager persistence;

    private final ExecutorService processMonitor;

    @Getter
    private final MetadataManager metadataManager;

    private final List<AbstractDownloader> downloaders = new ArrayList<>();

    private final Set<String> capturedLinks = new HashSet<>();
    private final Set<String> capturedPlaylists = new HashSet<>();
    private final ReentrantLock linkCaptureLock = new ReentrantLock(true);// Fair-mode reentrant lock

    private final AtomicInteger currentlyQueryingCount = new AtomicInteger();
    private final Queue<QueueEntry> metadataQueryQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentRearrangeableDeque<QueueEntry> downloadDeque
        = new ConcurrentRearrangeableDeque<>();

    // This queue has a blocking iterator, it's exclusive for the process monitor
    private final Queue<QueueEntry> inProgressDownloads = new LinkedIterableBlockingQueue<>();

    private final Queue<QueueEntry> runningDownloads = new ConcurrentLinkedQueue<>();
    private final Queue<QueueEntry> completedDownloads = new ConcurrentLinkedQueue<>();
    private final Queue<QueueEntry> failedDownloads = new ConcurrentLinkedQueue<>();

    @Getter
    private final AtomicLong downloadCounter = new AtomicLong();

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);
    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);
    private final AtomicBoolean downloadsManuallyStarted = new AtomicBoolean(false);

    private final AtomicReference<DownloaderIdEnum> suggestedDownloaderId = new AtomicReference<>(null);

    private final ExpiringSet<String> urlIgnoreSet = new ExpiringSet<>(TimeUnit.SECONDS, 20);

    private final ExecutorService forcefulExecutor = Executors.newVirtualThreadPerTaskExecutor();// No limits, power to ya
    private final String _forceStartKey = l10n("gui.force_download_start");
    private final String _restartKey = l10n("gui.restart_download");

    @SuppressWarnings("this-escape")
    public DownloadManager(GDownloader mainIn) {
        main = mainIn;

        persistence = main.getPersistenceManager();
        metadataManager = new MetadataManager();

        processMonitor = Executors.newSingleThreadExecutor();
        processMonitor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                for (QueueEntry entry : inProgressDownloads) {
                    if (!downloadsRunning.get() || entry.getCancelHook().get()) {
                        Process process = entry.getProcess();

                        if (process != null && process.isAlive()) {
                            if (main.getConfig().isDebugMode()) {
                                log.debug("Process Monitor is stopping {}", entry.getUrl());
                            }

                            try {
                                tryStopProcess(process);
                            } catch (InterruptedException e) {
                                log.error("Interrupted", e);
                            } catch (Exception e) {
                                log.error("Failed to stop process", e);
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        downloaders.add(new YtDlpDownloader(this));
        downloaders.add(new GalleryDlDownloader(this));
        downloaders.add(new SpotDLDownloader(this));
        downloaders.add(new DirectHttpDownloader(this));
    }

    @PostConstruct
    public void init() {
        try {
            if (persistence.isInitialized()) {
                // Even if persistance is disabled, we still initialize the db so a restart is not
                // needed and no data is lost if the user toggles persistence back on during runtime.
                long nextId = persistence.getCounters().getCurrentValue(CounterTypeEnum.DOWNLOAD_ID);
                main.getDownloadManager().getDownloadCounter().set(nextId);
                log.info("Current download id: {}", nextId);

                if (nextId > 0 && main.getConfig().isRestoreSessionAfterRestart()) {
                    ToastMessenger.show(
                        l10n("gui.restoring-session.toast"),
                        5000,
                        MessageTypeEnum.INFO,
                        false, true);
                }

                GDownloader.GLOBAL_THREAD_POOL.submitWithPriority(() -> {
                    linkCaptureLock.lock();// Intentionally block url capture during the entire restoring proccess
                    try {
                        int count = 0;
                        for (QueueEntryEntity entity : persistence.getQueueEntries().getAll()) {
                            String downloadUrl = entity.getUrl();

                            capturedLinks.add(downloadUrl);
                            capturedLinks.add(entity.getOriginalUrl());

                            List<AbstractDownloader> compatibleDownloaders = getCompatibleDownloaders(downloadUrl);

                            if (compatibleDownloaders.isEmpty()) {
                                log.error("No compatible downloaders found for: {}", downloadUrl);
                                continue;
                            }

                            MediaCard mediaCard = main.getGuiManager().addMediaCard(downloadUrl);

                            QueueEntry queueEntry = QueueEntry.fromEntity(entity, mediaCard, compatibleDownloaders);

                            if (queueEntry.getCurrentQueueCategory() == QueueCategoryEnum.RUNNING) {
                                queueEntry.updateStatus(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
                            }

                            initializeAndEnqueueEntry(queueEntry);

                            count++;
                        }

                        if (count > 0) {
                            log.info("Successfully restored {} downloads", count);
                        } else {
                            log.info("No downloads to restore");
                        }
                    } finally {
                        linkCaptureLock.unlock();
                    }
                }, 30);
            }
        } catch (Exception e) {
            GDownloader.handleException(e);
        }
    }

    public boolean isBlocked() {
        return downloadsBlocked.get();
    }

    public void block() {
        downloadsBlocked.set(true);
        fireListeners();
    }

    public void unblock() {
        downloadsBlocked.set(false);
        fireListeners();
    }

    public boolean isMainDownloaderInitialized() {
        return downloaders.stream().anyMatch((downloader)
            -> downloader.isMainDownloader()
            && downloader.getExecutablePath().isPresent()
            && downloader.getExecutablePath().get().exists());
    }

    public void setFfmpegPath(File path) {
        downloaders.stream()
            .forEach(downloader -> downloader.setFfmpegPath(Optional.of(path)));
    }

    public void setExecutablePath(DownloaderIdEnum downloaderId, File path) {
        downloaders.stream()
            .filter(downloader -> downloader.getDownloaderId() == downloaderId)
            .forEach(downloader -> downloader.setExecutablePath(Optional.of(path)));
    }

    private List<AbstractDownloader> getCompatibleDownloaders(String inputUrl) {
        return downloaders.stream()
            .filter(downloader -> downloader.canConsumeUrl(inputUrl))
            .collect(Collectors.toUnmodifiableList());
    }

    public List<AbstractDownloader> getEnabledDownloaders() {
        return downloaders.stream()
            .filter(downloader -> downloader.isEnabled())
            .collect(Collectors.toUnmodifiableList());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force) {
        return captureUrl(inputUrl, force, main.getConfig().getPlaylistDownloadOption());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force, PlayListOptionEnum playlistOption) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        List<AbstractDownloader> compatibleDownloaders = getCompatibleDownloaders(inputUrl);

        if (downloadsBlocked.get() || inputUrl == null || compatibleDownloaders.isEmpty()) {
            future.complete(false);
            return future;
        }

        linkCaptureLock.lock();
        try {
            if (capturedLinks.contains(inputUrl)) {
                future.complete(false);
                return future;
            }

            Optional<AbstractUrlFilter> filterOptional = getFilterForUrl(inputUrl,
                main.getConfig().isCaptureAnyLinks() || force);

            if (!filterOptional.isPresent()) {
                log.error("No filter found for url: {}. Ignoring.", inputUrl);
                future.complete(false);
                return future;
            }

            AbstractUrlFilter filter = filterOptional.get();
            if (main.getConfig().isDebugMode()) {
                log.debug("URL: {} matched {}", inputUrl, filter);
            }

            if (!filter.canAcceptUrl(inputUrl, main)) {
                log.info("Filter {} has denied to accept url {}; Verify settings.", filter, inputUrl);
                future.complete(false);
                return future;
            }

            String filteredUrl;
            // TODO: move these to the appropriate classes.
            if (filter instanceof YoutubePlaylistFilter) {
                switch (playlistOption) {
                    case DOWNLOAD_PLAYLIST: {
                        filteredUrl = filterPlaylist(inputUrl);

                        if (filteredUrl != null) {
                            if (!capturedPlaylists.add(filteredUrl)) {
                                String video = filterVideo(inputUrl);

                                if (main.getConfig().isDebugMode()) {
                                    log.debug("Individual video url is {}", video);
                                }

                                if (video != null && video.contains("?v=") && !video.contains("list=")) {
                                    return captureUrl(video, force);
                                } else {
                                    future.complete(false);
                                    return future;
                                }
                            }
                        }

                        break;
                    }

                    case DOWNLOAD_SINGLE: {
                        String playlist = filterPlaylist(inputUrl);

                        if (playlist != null) {
                            capturedPlaylists.add(playlist);
                        }

                        String video = filterVideo(inputUrl);

                        if (main.getConfig().isDebugMode()) {
                            log.debug("Video url is {}", video);
                        }

                        if (video != null && video.contains("?v=") && !video.contains("list=")) {
                            return captureUrl(video, force);
                        } else {
                            future.complete(false);
                            return future;
                        }
                    }

                    case ALWAYS_ASK:
                    default: {
                        String playlist = filterPlaylist(inputUrl);

                        if (playlist == null) {
                            future.complete(false);
                            return future;
                        }

                        if (!capturedPlaylists.contains(playlist)) {
                            GUIManager.DialogButton playlistDialogOption = new GUIManager.DialogButton(PlayListOptionEnum.DOWNLOAD_PLAYLIST.getDisplayName(),
                                (boolean setDefault) -> {
                                    if (setDefault) {
                                        main.getConfig().setPlaylistDownloadOption(PlayListOptionEnum.DOWNLOAD_PLAYLIST);
                                        main.updateConfig();
                                    }

                                    captureUrl(playlist, force, PlayListOptionEnum.DOWNLOAD_PLAYLIST)
                                        .whenComplete((Boolean result, Throwable e) -> {
                                            if (e != null) {
                                                GDownloader.handleException(e);
                                            }

                                            future.complete(result);
                                        });
                                });

                            GUIManager.DialogButton singleDialogOption = new GUIManager.DialogButton(PlayListOptionEnum.DOWNLOAD_SINGLE.getDisplayName(),
                                (boolean setDefault) -> {
                                    if (setDefault) {
                                        main.getConfig().setPlaylistDownloadOption(PlayListOptionEnum.DOWNLOAD_SINGLE);
                                        main.updateConfig();
                                    }

                                    captureUrl(inputUrl, force, PlayListOptionEnum.DOWNLOAD_SINGLE)
                                        .whenComplete((Boolean result, Throwable e) -> {
                                            if (e != null) {
                                                GDownloader.handleException(e);
                                            }

                                            future.complete(result);
                                        });
                                });

                            GUIManager.DialogButton defaultOption = new GUIManager.DialogButton("", (boolean setDefault) -> {
                                future.complete(false);
                            });

                            // TODO: This whole section needs to be refactored
                            if (urlIgnoreSet.contains(playlist) && !force) {// Temporary fix for double popups
                                future.complete(false);
                                return future;
                            } else {
                                urlIgnoreSet.add(playlist);

                                String sep = System.lineSeparator();
                                main.getGuiManager().showConfirmDialog(
                                    l10n("dialog.confirm"),
                                    l10n("dialog.download_playlist") + sep + sep + playlist,
                                    30000,
                                    defaultOption,
                                    playlistDialogOption,
                                    singleDialogOption);

                                return future;
                            }
                        } else {
                            // TODO I'm assuming this is a wanted behavior - having subsequent links being treated as individual videos
                            // It's odd that you'd download a whole playlist and then an individual video from it though, maybe investigate use cases
                            String video = filterVideo(inputUrl);

                            if (main.getConfig().isDebugMode()) {
                                log.debug("Individual video url is {}", video);
                            }

                            if (video != null && video.contains("?v=") && !video.contains("list=")) {
                                return captureUrl(video, force);
                            } else {
                                future.complete(false);
                                return future;
                            }
                        }
                    }
                }
            } else if (filter instanceof YoutubeFilter) {
                filteredUrl = filterVideo(inputUrl);
            } else {
                filteredUrl = inputUrl;
            }

            if (filteredUrl == null) {
                log.error("Filtered url was null.");
                future.complete(false);
                return future;
            }

            if (capturedLinks.add(filteredUrl)) {
                capturedLinks.add(inputUrl);

                log.info("Captured {}", inputUrl);

                MediaCard mediaCard = main.getGuiManager().addMediaCard(filteredUrl);

                long downloadId = downloadCounter.incrementAndGet();

                if (persistence.isInitialized()) {
                    persistence.getCounters().setCurrentValue(CounterTypeEnum.DOWNLOAD_ID, downloadId);
                }

                QueueEntry queueEntry = new QueueEntry(
                    main,
                    mediaCard,
                    filter,
                    inputUrl,
                    filteredUrl,
                    downloadId,
                    compatibleDownloaders);

                queueEntry.updateStatus(DownloadStatusEnum.QUERYING, l10n("gui.download_status.querying"));

                initializeAndEnqueueEntry(queueEntry);

                saveCheckpoint(queueEntry);

                future.complete(true);
                return future;
            }
        } finally {
            linkCaptureLock.unlock();
        }

        future.complete(false);
        return future;
    }

    private void initializeAndEnqueueEntry(QueueEntry queueEntry) {
        queueEntry.getMediaCard().setOnClose((reason) -> {
            queueEntry.close(reason);

            linkCaptureLock.lock();
            try {
                capturedPlaylists.remove(queueEntry.getUrl());
                capturedLinks.remove(queueEntry.getUrl());
                capturedLinks.remove(queueEntry.getOriginalUrl());
            } finally {
                linkCaptureLock.unlock();
            }

            dequeueFromAll(queueEntry);

            if (reason != CloseReasonEnum.SHUTDOWN) {
                deleteCheckpoint(queueEntry);
            }
        });

        queueEntry.getMediaCard().setOnLeftClick(() -> {
            main.openDownloadsDirectory();
        });

        queueEntry.createDefaultRightClick(this);

        queueEntry.getMediaCard().setOnDrag((targetIndex) -> {
            if (downloadDeque.contains(queueEntry)) {
                try {
                    downloadDeque.moveToPosition(queueEntry,
                        Math.clamp(targetIndex, 0, downloadDeque.size() - 1));
                } catch (Exception e) {
                    GDownloader.handleException(e, false);
                }
            }
        });

        queueEntry.getMediaCard().setValidateDropTarget(() -> {
            return downloadDeque.contains(queueEntry);
        });

        queryMetadata(queueEntry);

        QueueCategoryEnum category = queueEntry.getCurrentQueueCategory();
        if (category != null) {
            switch (category) {
                case FAILED ->
                    offerTo(FAILED, queueEntry, false);
                case COMPLETED ->
                    offerTo(COMPLETED, queueEntry, false);
                case RUNNING ->
                    enqueueFirst(queueEntry);
                default ->
                    enqueueLast(queueEntry);
            }
        } else {
            enqueueLast(queueEntry);
        }

        if (main.getConfig().isAutoDownloadStart() && !downloadsRunning.get()) {
            startDownloads(suggestedDownloaderId.get());
        }
    }

    private void saveCheckpoint(QueueEntry queueEntry) {
        if (persistence.isInitialized()) {
            persistence.getQueueEntries().upsert(queueEntry.toEntity());
        }
    }

    private void deleteCheckpoint(QueueEntry queueEntry) {
        if (persistence.isInitialized()) {
            persistence.getQueueEntries().remove(queueEntry.getDownloadId());
        }
    }

    private Optional<AbstractUrlFilter> getFilterForUrl(String url, boolean allowAnyLink) {
        AbstractUrlFilter filter = null;

        for (AbstractUrlFilter filterNeedle : main.getConfig().getUrlFilters()) {
            if (filterNeedle.matches(url)) {
                filter = filterNeedle;
                break;
            }
        }

        if (filter == null && allowAnyLink) {
            for (AbstractUrlFilter filterNeedle : main.getConfig().getUrlFilters()) {
                if (filterNeedle.getId().equals(GenericFilter.ID)) {
                    filter = filterNeedle;
                    break;
                }
            }
        }

        return Optional.ofNullable(filter);
    }

    public boolean isRunning() {
        return downloadsRunning.get();
    }

    public void toggleDownloads() {
        if (!isRunning()) {
            startDownloads();
        } else {
            stopDownloads();
        }
    }

    public void startDownloads() {
        startDownloads(null);
    }

    public void startDownloads(@Nullable DownloaderIdEnum downloaderId) {
        if (!downloadsBlocked.get()) {
            downloadsRunning.set(true);
            downloadsManuallyStarted.set(true);
            suggestedDownloaderId.set(downloaderId);

            fireListeners();
        }
    }

    public void stopDownloads() {
        downloadsRunning.set(false);
        downloadsManuallyStarted.set(false);
        suggestedDownloaderId.set(null);

        fireListeners();
    }

    private void fireListeners() {
        EventDispatcher.dispatch(this);
    }

    public int getQueuedDownloads() {
        return downloadDeque.size();
    }

    public int getRunningDownloads() {
        return runningDownloads.size();
    }

    public int getFailedDownloads() {
        return failedDownloads.size();
    }

    public int getCompletedDownloads() {
        return completedDownloads.size();
    }

    public void retryFailedDownloads() {
        QueueEntry entry;
        while ((entry = failedDownloads.poll()) != null) {
            resetDownload(entry, false);
        }

        startDownloads(suggestedDownloaderId.get());
        fireListeners();
    }

    public void processQueue() {
        while (downloadsRunning.get() && downloadsManuallyStarted.get() && !downloadDeque.isEmpty()) {
            if (runningDownloads.size() >= main.getConfig().getMaxSimultaneousDownloads()) {
                break;
            }

            QueueEntry entry = downloadDeque.peek();

            submitDownloadTask(entry, false);
        }

        if (!metadataQueryQueue.isEmpty()) {
            if (currentlyQueryingCount.get() < 2) {
                QueueEntry entry = metadataQueryQueue.poll();

                submitQueryMetadataTask(entry);
            }
        }

        if (downloadsRunning.get() && runningDownloads.isEmpty()) {
            stopDownloads();
        }
    }

    public void clearQueue(CloseReasonEnum reason) {
        capturedLinks.clear();
        capturedPlaylists.clear();

        for (QueueCategoryEnum category : QueueCategoryEnum.values()) {
            if (category == RUNNING) {
                continue;// Active downloads are intentionally immune to this.
            }

            clearQueue(category, reason, false);
        }

        fireListeners();
    }

    private Queue<QueueEntry> getQueue(QueueCategoryEnum category) {
        Queue<QueueEntry> queue;
        switch (category) {
            case FAILED ->
                queue = failedDownloads;
            case COMPLETED ->
                queue = completedDownloads;
            case QUEUED ->
                queue = downloadDeque;
            case RUNNING ->
                queue = runningDownloads;
            default ->
                throw new IllegalArgumentException();
        }

        return queue;
    }

    public void clearQueue(QueueCategoryEnum category, CloseReasonEnum reason) {
        clearQueue(category, reason, true);
    }

    public void clearQueue(QueueCategoryEnum category, CloseReasonEnum reason, boolean fireListeners) {
        Queue<QueueEntry> queue = getQueue(category);

        QueueEntry entry;
        while ((entry = queue.poll()) != null) {
            // TODO: more checkpoints
            if (reason == CloseReasonEnum.SHUTDOWN && !entry.getCancelHook().get()) {
                saveCheckpoint(entry);
            }

            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId(), reason);
        }

        if (fireListeners) {
            fireListeners();
        }
    }

    private void enqueueLast(QueueEntry entry) {
        dequeueFromAll(entry, QUEUED);

        entry.removeRightClick(_restartKey);
        entry.addRightClick(_forceStartKey,
            () -> submitDownloadTask(entry, true));

        if (!downloadDeque.contains(entry)) {
            entry.setCurrentQueueCategory(QUEUED);

            downloadDeque.offerLast(entry);
            fireListeners();
        }
    }

    private void enqueueFirst(QueueEntry entry) {
        dequeueFromAll(entry, QUEUED);

        entry.removeRightClick(_restartKey);
        entry.addRightClick(_forceStartKey,
            () -> submitDownloadTask(entry, true));

        if (!downloadDeque.contains(entry)) {
            entry.setCurrentQueueCategory(QUEUED);

            downloadDeque.offerFirst(entry);
            fireListeners();
        }
    }

    private void dequeueFromAll(QueueEntry entry) {
        dequeueFromAll(entry, null);
    }

    private void dequeueFromAll(QueueEntry entry, @Nullable QueueCategoryEnum except) {
        boolean success = false;
        for (QueueCategoryEnum category : QueueCategoryEnum.values()) {
            if (category == except) {
                continue;
            }

            if (dequeue(category, entry, false)) {
                success = true;
            }
        }

        if (success) {
            fireListeners();
        }
    }

    private boolean dequeue(QueueCategoryEnum category, QueueEntry entry) {
        return dequeue(category, entry, true);
    }

    private boolean dequeue(QueueCategoryEnum category, QueueEntry entry, boolean fireListeners) {
        boolean success = getQueue(category).remove(entry);

        if (success && fireListeners) {
            fireListeners();
        }

        return success;
    }

    private void offerTo(QueueCategoryEnum category, QueueEntry entry) {
        offerTo(category, entry, true);
    }

    private void offerTo(QueueCategoryEnum category, QueueEntry entry, boolean checkpoint) {
        if (category == QUEUED) {
            throw new IllegalArgumentException("Use enqueueFirst() or enqueueLast() to add to downloadDeque");
        }

        dequeueFromAll(entry);

        Queue<QueueEntry> queue = getQueue(category);
        if (!queue.contains(entry)) {
            entry.setCurrentQueueCategory(category);

            entry.addRightClick(_restartKey, () -> stopDownload(entry, () -> {
                resetDownload(entry);
                submitDownloadTask(entry, true);
            }));

            if (checkpoint) {
                saveCheckpoint(entry);
            }

            queue.offer(entry);
            fireListeners();
        }
    }

    private void queryMetadata(QueueEntry queueEntry) {
        if (!main.getConfig().isQueryMetadata() || queueEntry.getQueried().get()) {
            if (queueEntry.getDownloadStatus() == DownloadStatusEnum.QUERYING) {
                queueEntry.updateStatus(DownloadStatusEnum.QUEUED,
                    l10n("gui.download_status.not_started"));
            }

            return;
        }

        metadataQueryQueue.offer(queueEntry);
    }

    private void submitQueryMetadataTask(QueueEntry queueEntry) {
        currentlyQueryingCount.incrementAndGet();

        GDownloader.GLOBAL_THREAD_POOL.submitWithPriority(() -> {
            try {
                if (queueEntry.getCancelHook().get()) {
                    return;
                }

                for (AbstractDownloader downloader : queueEntry.getDownloaders()) {
                    if (downloader.tryQueryMetadata(queueEntry)) {
                        break;
                    }
                }

                queueEntry.markQueried();

                if (queueEntry.getDownloadStatus() == DownloadStatusEnum.QUERYING) {
                    queueEntry.updateStatus(DownloadStatusEnum.QUEUED,
                        l10n("gui.download_status.not_started"));
                }
            } finally {
                currentlyQueryingCount.decrementAndGet();
            }
        }, 1);
    }

    protected void resetDownload(QueueEntry queueEntry) {
        resetDownload(queueEntry, true);
    }

    protected void resetDownload(QueueEntry queueEntry, boolean fireListeners) {
        queueEntry.createDefaultRightClick(this);

        queueEntry.cleanDirectories();

        queueEntry.updateStatus(DownloadStatusEnum.QUEUED, l10n("gui.download_status.not_started"));
        queueEntry.resetDownloaderBlacklist();
        queueEntry.resetRetryCounter();// Normaly we want the retry count to stick around, but not in this case.

        removeArchiveEntries(queueEntry);

        enqueueLast(queueEntry);

        if (fireListeners) {
            fireListeners();
        }
    }

    private void removeArchiveEntries(QueueEntry queueEntry) {
        if (!main.getConfig().isRemoveFromDownloadArchive()) {
            return;
        }

        for (AbstractDownloader downloader : queueEntry.getDownloaders()) {
            downloader.removeArchiveEntry(queueEntry);
        }
    }

    protected CompletableFuture<Void> stopDownload(QueueEntry entry, Runnable runAfter) {
        entry.getCancelHook().set(true);

        return CompletableFuture.runAsync(() -> {
            while (entry.isRunning()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).thenRun(() -> {
            if (!entry.getMediaCard().isClosed()) {
                entry.getCancelHook().set(false);

                try {
                    runAfter.run();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }
        }).exceptionally(e -> {
            GDownloader.handleException(e);
            return null;
        });
    }

    protected void submitDownloadTask(QueueEntry entry, boolean force) {
        // Call to remove is needed when starting manually
        boolean success = downloadDeque.remove(entry);
        if (!success) {
            log.error("Entry was not in the download deque, ignoring");
            return;
        }

        entry.removeRightClick(_forceStartKey);

        MediaCard mediaCard = entry.getMediaCard();
        if (mediaCard.isClosed()) {
            return;
        }

        offerTo(RUNNING, entry);

        Runnable downloadTask = () -> {
            try {
                if (!downloadsRunning.get()) {
                    if (force) {
                        downloadsRunning.set(true);
                        fireListeners();
                    } else {
                        enqueueFirst(entry);
                        return;
                    }
                }

                entry.getRunning().set(true);

                try {
                    inProgressDownloads.offer(entry);

                    DownloaderIdEnum forcedDownloader = entry.getForcedDownloader();
                    if (forcedDownloader == null) {
                        forcedDownloader = suggestedDownloaderId.get();
                    }

                    int maxRetries = main.getConfig().isAutoDownloadRetry() ? main.getConfig().getMaxDownloadRetries() : 1;
                    String lastOutput = "";

                    boolean notifiedCookies = false;

                    while (entry.getRetryCounter().get() <= maxRetries) {
                        boolean downloadAttempted = false;

                        entry.resetForRestart();

                        for (AbstractDownloader downloader : entry.getDownloaders()) {
                            DownloaderIdEnum downloaderId = downloader.getDownloaderId();
                            if (log.isDebugEnabled()) {
                                log.info("Trying to download with {}", downloaderId);
                            }

                            if (forcedDownloader != null && forcedDownloader != downloaderId) {
                                continue;
                            }

                            if (entry.isDownloaderBlacklisted(downloaderId) && downloaderId != forcedDownloader) {
                                continue;
                            }

                            entry.setCurrentDownloader(downloaderId);

                            AbstractUrlFilter filter = entry.getFilter();

                            if (!notifiedCookies && filter.areCookiesRequired()
                                && (!main.getConfig().isReadCookiesFromBrowser() && downloader.getCookieJarFile() == null)) {
                                ToastMessenger.show(
                                    l10n("gui.cookies_required_for_website", filter.getFilterName()),
                                    3000,
                                    MessageTypeEnum.ERROR,
                                    false, true);

                                log.warn("Cookies are required for this website {}", entry.getOriginalUrl());
                                notifiedCookies = true;
                            }

                            if (entry.getRetryCounter().get() > 0) {
                                entry.updateStatus(DownloadStatusEnum.RETRYING, l10n("gui.download_status.retrying",
                                    String.format("%d/%d", entry.getRetryCounter().get(), maxRetries)) + ": " + lastOutput);
                            } else {
                                entry.updateStatus(DownloadStatusEnum.STARTING, l10n("gui.download_status.starting"));
                            }

                            DownloadResult result = downloader.tryDownload(entry);

                            BitSet flags = result.getFlags();
                            lastOutput = result.getLastOutput();

                            boolean unsupported = FLAG_UNSUPPORTED.isSet(flags);
                            boolean disabled = FLAG_DOWNLOADER_DISABLED.isSet(flags);

                            if (FLAG_MAIN_CATEGORY_FAILED.isSet(flags) || unsupported || disabled) {
                                entry.logError(lastOutput);

                                if (disabled || unsupported || entry.getRetryCounter().get() >= maxRetries) {
                                    entry.updateStatus(DownloadStatusEnum.FAILED, lastOutput);
                                    log.error("Download of {} failed on {}: {} supported downloader: {}",
                                        entry.getUrl(), downloaderId, lastOutput, !unsupported);

                                    entry.blackListDownloader(downloaderId);
                                } else {
                                    entry.updateStatus(DownloadStatusEnum.RETRYING, lastOutput);
                                    log.warn("Download of {} failed with {}, retrying ({}/{}): {}",
                                        entry.getUrl(),
                                        downloaderId,
                                        entry.getRetryCounter().get() + 1,
                                        maxRetries,
                                        lastOutput);

                                    downloadAttempted = true;
                                }

                                continue;
                            }

                            if (FLAG_NO_METHOD.isSet(flags)) {
                                entry.logError(lastOutput);

                                if (FLAG_NO_METHOD_VIDEO.isSet(flags)) {
                                    log.error("{} - No option to download.", filter);
                                    entry.updateStatus(DownloadStatusEnum.NO_METHOD, l10n("enums.download_status.no_method.video_tip"));
                                } else if (FLAG_NO_METHOD_AUDIO.isSet(flags)) {
                                    log.error("{} - No audio quality selected, but was set to download audio only.", filter);
                                    entry.updateStatus(DownloadStatusEnum.NO_METHOD, l10n("enums.download_status.no_method.audio_tip"));
                                } else {
                                    throw new IllegalStateException("Unhandled NO_METHOD");
                                }

                                offerTo(FAILED, entry);
                                return;
                            }

                            if (!downloadsRunning.get() || FLAG_STOPPED.isSet(flags)) {
                                entry.updateStatus(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
                                enqueueFirst(entry);
                                return;
                            } else if (!entry.getCancelHook().get() && FLAG_SUCCESS.isSet(flags)) {
                                entry.updateStatus(DownloadStatusEnum.POST_PROCESSING, l10n("gui.download_status.processing_media_files"));

                                downloader.processMediaFiles(entry);
                                entry.updateMediaRightClickOptions();

                                entry.updateStatus(DownloadStatusEnum.COMPLETE, l10n("gui.download_status.finished"));
                                entry.cleanDirectories();

                                offerTo(COMPLETED, entry);

                                if (main.getConfig().isRemoveSuccessfulDownloads()) {
                                    main.getGuiManager().removeMediaCard(mediaCard.getId(), CloseReasonEnum.SUCCEEDED);
                                }

                                return;
                            } else {
                                log.error("Unexpected download state");
                            }
                        }

                        if (!downloadAttempted) {
                            break; // No more downloaders to try.
                        }

                        entry.getRetryCounter().incrementAndGet();
                    }

                    if (!lastOutput.isEmpty()) {
                        entry.logError(lastOutput);

                        entry.updateStatus(DownloadStatusEnum.FAILED, lastOutput);
                    } else {
                        entry.updateStatus(DownloadStatusEnum.FAILED);
                    }

                    if (log.isDebugEnabled()) {
                        log.error("All downloaders failed for {}", entry.getUrl());
                    }

                    offerTo(FAILED, entry);
                } finally {
                    inProgressDownloads.remove(entry);
                }
            } catch (Exception e) {
                log.error("Failed to download", e);

                entry.updateStatus(DownloadStatusEnum.FAILED, e.getMessage());

                offerTo(FAILED, entry);

                GDownloader.handleException(e);
            } finally {
                entry.getRunning().set(false);

                dequeue(RUNNING, entry);
            }
        };

        if (force) {
            forcefulExecutor.execute(downloadTask);
        } else {
            GDownloader.GLOBAL_THREAD_POOL.submitWithPriority(downloadTask, 10);
        }
    }

    private void tryStopProcess(Process process) throws InterruptedException {
        if (process.isAlive()) {
            long quitTimer = System.currentTimeMillis();

            // First try to politely ask the process to excuse itself.
            process.destroy();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("Process did not terminate in time, forcefully stopping it.");
                // Time's up. I guess asking nicely wasn't in the cards.
                process.destroyForcibly();
            }

            log.info("Took {}ms to stop the process.",
                (System.currentTimeMillis() - quitTimer));
        }
    }

    @PreDestroy
    public void close() {
        stopDownloads();

        clearQueue(RUNNING, CloseReasonEnum.SHUTDOWN, false);
        clearQueue(CloseReasonEnum.SHUTDOWN);

        for (AbstractDownloader downloader : downloaders) {
            downloader.close();
        }

        processMonitor.shutdownNow();
        forcefulExecutor.shutdownNow();
    }
}
