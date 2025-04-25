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
import net.brlns.gdownloader.downloader.enums.*;
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
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DownloadIntervalometer;
import net.brlns.gdownloader.util.collection.ExpiringSet;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.*;
import static net.brlns.gdownloader.util.URLUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadManager implements IEvent {

    private final AtomicLong downloadIdGenerator = new AtomicLong();

    @Getter
    private final GDownloader main;

    private final PersistenceManager persistence;

    @Getter
    private final MetadataManager metadataManager;

    private final List<AbstractDownloader> downloaders = new ArrayList<>();

    private final Set<String> capturedLinks = new HashSet<>();
    private final Set<String> capturedPlaylists = new HashSet<>();
    private final ReentrantLock linkCaptureLock = new ReentrantLock(true);// Fair-mode reentrant lock

    private final AtomicInteger currentlyQueryingCount = new AtomicInteger();
    private final Queue<QueueEntry> metadataQueryQueue = new ConcurrentLinkedQueue<>();

    private final DownloadSequencer sequencer = new DownloadSequencer();

    private final AtomicBoolean shouldNotifyCompletion = new AtomicBoolean();

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);
    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);
    private final AtomicBoolean downloadsManuallyStarted = new AtomicBoolean(false);

    private final AtomicReference<DownloaderIdEnum> suggestedDownloaderId = new AtomicReference<>(null);

    private final ExpiringSet<String> urlIgnoreSet = new ExpiringSet<>(TimeUnit.SECONDS, 20);

    private final String _downloadPriorityKey = l10n("gui.download_priority");
    private final String _forceStartKey = l10n("gui.force_download_start");
    private final String _restartKey = l10n("gui.restart_download");

    private final DownloadIntervalometer intervalometer = new DownloadIntervalometer(5, 15);

    @SuppressWarnings("this-escape")
    public DownloadManager(GDownloader mainIn) {
        main = mainIn;

        persistence = main.getPersistenceManager();
        metadataManager = new MetadataManager();

        downloaders.add(new YtDlpDownloader(this));
        downloaders.add(new GalleryDlDownloader(this));
        downloaders.add(new SpotDLDownloader(this));
        downloaders.add(new DirectHttpDownloader(this));
    }

    @PostConstruct
    public void init() {
        try {
            metadataManager.init();

            if (persistence.isInitialized()) {
                if (!persistence.isFirstBoot()
                    && main.getConfig().isRestoreSessionAfterRestart()) {
                    ToastMessenger.show(Message.builder()
                        .message("gui.restoring_session.toast")
                        .durationMillis(5000)
                        .messageType(MessageTypeEnum.INFO)
                        .discardDuplicates(true)
                        .build());
                }

                // Even if persistance is disabled, we still initialize the db so a restart is not
                // needed and no data is lost if the user toggles persistence back on during runtime.
                long nextId = persistence.getCounters().getCurrentValue(CounterTypeEnum.DOWNLOAD_ID);
                downloadIdGenerator.set(nextId);
                log.info("Current download id: {}", nextId);

                GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
                    linkCaptureLock.lock();// Intentionally block url capture during the entire restoring proccess
                    try {
                        int count = 0;

                        // We need an ugly comparator here because of null sequence numbers.
                        // This way, we still preserve the original insertion order until a checkpoint updates the sequence.
                        List<QueueEntryEntity> entities = persistence.getQueueEntries().getAll();
                        List<QueueEntryEntity> originalOrder = new ArrayList<>(entities);
                        entities.sort((e1, e2) -> {
                            Long seq1 = e1.getCurrentDownloadSequence();
                            Long seq2 = e2.getCurrentDownloadSequence();

                            if (seq1 == null && seq2 == null) {
                                return Integer.compare(originalOrder.indexOf(e1), originalOrder.indexOf(e2));
                            } else if (seq1 == null) {
                                return 1;
                            } else if (seq2 == null) {
                                return -1;
                            } else {
                                return seq1.compareTo(seq2);
                            }
                        });

                        for (QueueEntryEntity entity : entities) {
                            String downloadUrl = entity.getUrl();

                            capturedLinks.add(downloadUrl);
                            capturedLinks.add(entity.getOriginalUrl());

                            List<AbstractDownloader> compatibleDownloaders = getCompatibleDownloaders(downloadUrl);

                            if (compatibleDownloaders.isEmpty()) {
                                log.error("No compatible downloaders found for: {}", downloadUrl);
                                continue;
                            }

                            MediaCard mediaCard = main.getGuiManager()
                                .getMediaCardManager().addMediaCard(downloadUrl);

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
                });
            }
        } catch (Exception e) {
            GDownloader.handleException(e);
        } finally {
            main.getClipboardManager().unblock();
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

    public AbstractDownloader getDownloader(DownloaderIdEnum downloaderId) {
        return downloaders.stream()
            .filter(downloader -> downloader.getDownloaderId() == downloaderId)
            .findFirst().get();
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force) {
        return captureUrl(inputUrl, force, main.getConfig().getPlaylistDownloadOption());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl,
        boolean force, PlayListOptionEnum playlistOption) {
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
                            GUIManager.DialogButton playlistDialogOption = new GUIManager.DialogButton(
                                PlayListOptionEnum.DOWNLOAD_PLAYLIST.getDisplayName(),
                                (boolean setDefault) -> {
                                    if (setDefault) {
                                        main.getConfig().setPlaylistDownloadOption(
                                            PlayListOptionEnum.DOWNLOAD_PLAYLIST);
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

                            GUIManager.DialogButton singleDialogOption = new GUIManager.DialogButton(
                                PlayListOptionEnum.DOWNLOAD_SINGLE.getDisplayName(),
                                (boolean setDefault) -> {
                                    if (setDefault) {
                                        main.getConfig().setPlaylistDownloadOption(
                                            PlayListOptionEnum.DOWNLOAD_SINGLE);
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

                            GUIManager.DialogButton defaultOption = new GUIManager.DialogButton(
                                "", (boolean setDefault) -> {
                                    future.complete(false);
                                });

                            // TODO: This whole section really needs to be refactored
                            if (urlIgnoreSet.contains(playlist) && !force) {// Very temporary fix for double popups
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

                MediaCard mediaCard = main.getGuiManager()
                    .getMediaCardManager().addMediaCard(filteredUrl);

                long downloadId = downloadIdGenerator.incrementAndGet();

                if (persistence.isInitialized()) {
                    persistence.getCounters().setCurrentValue(CounterTypeEnum.DOWNLOAD_ID, downloadId);
                }

                QueueEntry queueEntry = new QueueEntry(
                    main,
                    mediaCard,
                    filter.getId(),
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

            if (sequencer.removeEntry(queueEntry)) {
                fireListeners();
            }

            if (reason != CloseReasonEnum.SHUTDOWN) {
                deleteCheckpoint(queueEntry);
            }
        });

        queueEntry.getMediaCard().setOnLeftClick(() -> {
            queueEntry.tryOpenMediaFiles();
        });

        queueEntry.createDefaultRightClick(this);

        queueEntry.getMediaCard().setOnSwap((targetCard) -> {
            Optional<QueueEntry> entryOptional = locateEntryByMediaCard(targetCard);

            if (entryOptional.isPresent()) {
                sequencer.reorderEntries(queueEntry, entryOptional.get());
            }
        });

        queueEntry.getMediaCard().setValidateDropTarget(() -> {
            return sequencer.contains(queueEntry);
        });

        queryMetadata(queueEntry);

        sequencer.addNewEntry(queueEntry);
        fireListeners();

        updateRightClick(queueEntry, queueEntry.getCurrentQueueCategory());

        if (main.getConfig().isAutoDownloadStart() && !downloadsRunning.get()) {
            startDownloads(suggestedDownloaderId.get());
        }
    }

    protected Optional<QueueEntry> locateEntryByMediaCard(MediaCard cardToSeach) {
        QueueEntry found = sequencer.getEntry((entry) -> {
            MediaCard mediaCard = entry.getMediaCard();
            return mediaCard.getId() == cardToSeach.getId();
        });

        return Optional.ofNullable(found);
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
        return sequencer.getCount(QUEUED);
    }

    public int getRunningDownloads() {
        return sequencer.getCount(RUNNING);
    }

    public int getFailedDownloads() {
        return sequencer.getCount(FAILED);
    }

    public int getCompletedDownloads() {
        return sequencer.getCount(COMPLETED);
    }

    public void retryFailedDownloads() {
        sequencer.requeueFailed((entry) -> {
            requeueEntry(entry);
        });

        startDownloads(suggestedDownloaderId.get());
        fireListeners();
    }

    public void requeueEntry(QueueEntry entry) {
        resetDownload(entry);
        offerTo(QUEUED, entry);
    }

    public void processQueue() {
        int maxDownloads = main.getConfig().getMaxSimultaneousDownloads();

        while (downloadsRunning.get()
            && downloadsManuallyStarted.get()
            && !sequencer.isEmpty(QUEUED)
            && sequencer.getCount(RUNNING) < maxDownloads) {

            QueueEntry entry = sequencer.fetchNext();
            if (entry == null) {
                log.info("No more entries to fetch from queue");
                break;
            }

            submitDownloadTask(entry, false);
        }

        if (!metadataQueryQueue.isEmpty()) {
            if (currentlyQueryingCount.get() < 2) {
                QueueEntry entry = metadataQueryQueue.poll();

                if (!entry.getCancelHook().get()) {
                    submitQueryMetadataTask(entry);
                }
            }
        }

        if (downloadsRunning.get() && sequencer.isEmpty(RUNNING)) {
            if (main.getConfig().isDisplayDownloadsCompleteNotification()
                && shouldNotifyCompletion.get() && !sequencer.isEmpty()) {
                PopupMessenger.show(Message.builder()
                    .title("gui.downloads_complete.notification_title")
                    .message("gui.downloads_complete.complete")
                    .durationMillis(5000)
                    .messageType(MessageTypeEnum.INFO)
                    .playTone(true)
                    .discardDuplicates(true)
                    .build());

                shouldNotifyCompletion.set(false);
            }

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

    public void clearQueue(QueueCategoryEnum category, CloseReasonEnum reason) {
        clearQueue(category, reason, true);
    }

    public void clearQueue(QueueCategoryEnum category, CloseReasonEnum reason, boolean fireListeners) {
        sequencer.removeAll(category, (entry) -> {
            if (reason == CloseReasonEnum.SHUTDOWN && !entry.getCancelHook().get()) {
                saveCheckpoint(entry);
            }

            entry.dispose(reason);
        });

        if (fireListeners) {
            fireListeners();
        }
    }

    protected void updatePriority(QueueEntry entry, DownloadPriorityEnum priority) {
        sequencer.updatePriority(entry, priority);
    }

    public List<Integer> getSortedMediaCardIds() {
        return sequencer.getSnapshot().stream()
            .filter(e -> !e.getCancelHook().get())
            .map(e -> e.getMediaCard().getId())
            .collect(Collectors.toList());
    }

    public void setSortOrder(QueueSortOrderEnum sortOrder) {
        sequencer.setSortOrder(sortOrder);

        main.getGuiManager().getMediaCardManager()
            .reorderMediaCards(getSortedMediaCardIds());
    }

    public QueueSortOrderEnum getSortOrder() {
        return sequencer.getCurrentSortOrder();
    }

    private void updateRightClick(QueueEntry entry, QueueCategoryEnum category) {
        if (category == RUNNING) {
            entry.removeRightClick(_forceStartKey);
            return;
        }

        if (category == COMPLETED || category == FAILED) {
            if (category == COMPLETED) {
                entry.removeRightClick(_downloadPriorityKey);
            }

            entry.removeRightClick(_forceStartKey);
            entry.addRightClick(_restartKey, () -> stopDownload(entry, () -> {
                resetDownload(entry);
                submitDownloadTask(entry, true);
            }));
        } else {
            entry.removeRightClick(_restartKey);
            entry.addRightClick(_forceStartKey,
                () -> submitDownloadTask(entry, true));
            entry.addRightClick(_downloadPriorityKey,
                entry.getDownloadPriorityMenu(this));
        }
    }

    private void offerTo(QueueCategoryEnum category, QueueEntry entry) {
        offerTo(category, entry, true);
    }

    private void offerTo(QueueCategoryEnum category, QueueEntry entry, boolean checkpoint) {
        if (category == FAILED || category == COMPLETED) {
            shouldNotifyCompletion.set(true);
        }

        sequencer.changeCategory(entry, category);

        updateRightClick(entry, category);

        if (checkpoint) {
            saveCheckpoint(entry);
        }

        fireListeners();
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

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
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
        });
    }

    protected void resetDownload(QueueEntry queueEntry) {
        queueEntry.createDefaultRightClick(this);

        queueEntry.cleanDirectories();

        queueEntry.updateStatus(DownloadStatusEnum.QUEUED, l10n("gui.download_status.not_started"));
        queueEntry.resetDownloaderBlacklist();
        queueEntry.resetRetryCounter();// Normaly we want the retry count to stick around, but not in this case.

        removeArchiveEntries(queueEntry);
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
        MediaCard mediaCard = entry.getMediaCard();
        if (mediaCard.isClosed()) {
            return;
        }

        if (entry.getCurrentQueueCategory() != RUNNING) {
            offerTo(RUNNING, entry, false);
        }

        fireListeners();

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            try {
                if (!downloadsRunning.get()) {
                    if (force) {
                        downloadsRunning.set(true);
                        fireListeners();
                    } else {
                        offerTo(QUEUED, entry);
                        return;
                    }
                }

                entry.getRunning().set(true);

                DownloaderIdEnum forcedDownloader = entry.getForcedDownloader();
                if (forcedDownloader == null) {
                    forcedDownloader = suggestedDownloaderId.get();
                }

                int maxRetries = main.getConfig().isAutoDownloadRetry()
                    ? main.getConfig().getMaxDownloadRetries() : 1;
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

                        if (entry.isDownloaderBlacklisted(downloaderId)
                            && downloaderId != forcedDownloader) {
                            continue;
                        }

                        entry.setCurrentDownloader(downloaderId);

                        AbstractUrlFilter filter = entry.getFilter();

                        if (!notifiedCookies && filter.areCookiesRequired()
                            && (!main.getConfig().isReadCookiesFromBrowser()
                            && downloader.getCookieJarFile() == null)) {
                            ToastMessenger.show(Message.builder()
                                .message("gui.cookies_required_for_website", filter.getFilterName())
                                .durationMillis(3000)
                                .messageType(MessageTypeEnum.ERROR)
                                .discardDuplicates(true)
                                .build());

                            log.warn("Cookies are required for this website {}", entry.getOriginalUrl());
                            notifiedCookies = true;
                        }

                        if (entry.getRetryCounter().get() > 0) {
                            entry.updateStatus(DownloadStatusEnum.RETRYING, l10n("gui.download_status.retrying",
                                String.format("%d/%d", entry.getRetryCounter().get(), maxRetries)) + ": " + lastOutput);
                        } else {
                            if (main.getConfig().isRandomIntervalBetweenDownloads()) {
                                int currentWaitTime = intervalometer.getAndCompute(entry.getUrl());
                                if (currentWaitTime > 0) {
                                    entry.updateStatus(DownloadStatusEnum.WAITING,
                                        l10n("gui.intervalometer.waiting", currentWaitTime));

                                    try {
                                        CancelHook cancelHook = entry.getCancelHook().derive(this::isRunning, true);
                                        intervalometer.park(currentWaitTime, cancelHook);
                                    } catch (InterruptedException e) {
                                        log.warn("Interrupted");
                                    }
                                }
                            }

                            entry.updateStatus(DownloadStatusEnum.STARTING,
                                l10n("gui.download_status.starting"));
                        }

                        DownloadResult result = downloader.tryDownload(entry);

                        BitSet flags = result.getFlags();
                        lastOutput = result.getLastOutput();

                        boolean unsupported = FLAG_UNSUPPORTED.isSet(flags);
                        boolean disabled = FLAG_DOWNLOADER_DISABLED.isSet(flags);
                        boolean transcodingFailed = FLAG_TRANSCODING_FAILED.isSet(flags);

                        if (FLAG_MAIN_CATEGORY_FAILED.isSet(flags)
                            || unsupported || disabled || transcodingFailed) {
                            entry.logError(lastOutput);

                            if (transcodingFailed || disabled || unsupported
                                || entry.getRetryCounter().get() >= maxRetries) {
                                entry.updateStatus(DownloadStatusEnum.FAILED, lastOutput);
                                log.error("Download of {} failed on {}: {} supported downloader: {}",
                                    entry.getUrl(), downloaderId, lastOutput, !unsupported);

                                if (!transcodingFailed) {
                                    entry.blackListDownloader(downloaderId);
                                }
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
                                entry.updateStatus(DownloadStatusEnum.NO_METHOD,
                                    l10n("enums.download_status.no_method.video_tip"));
                            } else if (FLAG_NO_METHOD_AUDIO.isSet(flags)) {
                                log.error("{} - No audio quality selected, but was set to download audio only.", filter);
                                entry.updateStatus(DownloadStatusEnum.NO_METHOD,
                                    l10n("enums.download_status.no_method.audio_tip"));
                            } else {
                                throw new IllegalStateException("Unhandled NO_METHOD");
                            }

                            offerTo(FAILED, entry);
                            return;
                        }

                        if (!downloadsRunning.get() || FLAG_STOPPED.isSet(flags)) {
                            if (!entry.getCancelHook().get()) {
                                entry.updateStatus(DownloadStatusEnum.STOPPED,
                                    l10n("gui.download_status.not_started"));
                                offerTo(QUEUED, entry);
                            }

                            return;
                        } else if (!entry.getCancelHook().get() && FLAG_SUCCESS.isSet(flags)) {
                            entry.updateStatus(DownloadStatusEnum.POST_PROCESSING,
                                l10n("gui.download_status.processing_media_files"));
                            downloader.processMediaFiles(entry);

                            entry.updateMediaRightClickOptions();

                            updatePriority(entry, DownloadPriorityEnum.NORMAL);

                            entry.updateStatus(DownloadStatusEnum.COMPLETE,
                                l10n("gui.download_status.finished"));
                            entry.cleanDirectories();

                            offerTo(COMPLETED, entry);

                            if (main.getConfig().isRemoveSuccessfulDownloads()) {
                                entry.dispose(CloseReasonEnum.SUCCEEDED);
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
            } catch (Exception e) {
                log.error("Failed to download", e);

                entry.updateStatus(DownloadStatusEnum.FAILED, e.getMessage());

                offerTo(FAILED, entry);

                GDownloader.handleException(e);
            } finally {
                entry.getRunning().set(false);

                if (entry.getCurrentQueueCategory() == RUNNING
                    && !entry.getCancelHook().get()) {
                    log.error("Unexpected RUNNING download state, switching to QUEUED");
                    offerTo(QUEUED, entry);
                }
            }
        });
    }

    @PreDestroy
    public void close() {
        stopDownloads();

        clearQueue(RUNNING, CloseReasonEnum.SHUTDOWN, false);
        clearQueue(CloseReasonEnum.SHUTDOWN);

        for (AbstractDownloader downloader : downloaders) {
            downloader.close();
        }
    }
}
