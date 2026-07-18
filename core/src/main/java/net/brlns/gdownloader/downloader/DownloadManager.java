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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.*;
import net.brlns.gdownloader.downloader.extractors.MetadataManager;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.FormatInfo;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.IEvent;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.filters.GenericFilter;
import net.brlns.gdownloader.filters.YoutubeFilter;
import net.brlns.gdownloader.filters.YoutubePlaylistFilter;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.persistence.entity.CounterTypeEnum;
import net.brlns.gdownloader.persistence.entity.DownloadHistoryEntity;
import net.brlns.gdownloader.persistence.entity.QueueEntryEntity;
import net.brlns.gdownloader.process.ProcessMonitor;
import net.brlns.gdownloader.settings.enums.PlayListOptionEnum;
import net.brlns.gdownloader.system.ShutdownRegistry.CloseBefore;
import net.brlns.gdownloader.system.taskbar.ITaskbarManager.TaskbarState;
import net.brlns.gdownloader.system.taskbar.TaskbarManager;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.mediacard.MediaCard;
import net.brlns.gdownloader.ui.mediacard.MediaCard.StartButtonMode;
import net.brlns.gdownloader.ui.mediacard.MediaInfoPopup;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.MathUtils;
import net.brlns.gdownloader.util.collection.ExpiringSet;
import net.brlns.gdownloader.util.collection.LRUCache;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.URLUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@CloseBefore(before = {PersistenceManager.class, ProcessMonitor.class})
public class DownloadManager implements IEvent, AutoCloseable {

    private final AtomicLong downloadIdGenerator = new AtomicLong();

    @Getter
    private final GDownloader main;

    @Getter
    private final AtomicBoolean initialized = new AtomicBoolean();

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

    private final DownloadIntervalometer intervalometer = new DownloadIntervalometer(30, 60);

    private final LRUCache<String, Long> recentlyDeletedUrls = new LRUCache<>(1000);

    @SuppressWarnings("this-escape")
    public DownloadManager(GDownloader mainIn) {
        main = mainIn;

        persistence = main.getPersistenceManager();
        metadataManager = new MetadataManager();

        registerDownloader(new YtDlpDownloader(this));
        registerDownloader(new GalleryDlDownloader(this));
        registerDownloader(new SpotDLDownloader(this));
        registerDownloader(new DirectHttpDownloader(this));
    }

    public final void registerDownloader(AbstractDownloader downloader) {
        downloaders.add(downloader);
    }

    @PostConstruct
    public void init() {
        metadataManager.init();

        if (persistence.isInitialized()) {
            if (main.getConfig().isPersistenceDatabaseInitialized()
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

                    Map<QueueEntryEntity, Integer> originalIndex = new IdentityHashMap<>();
                    for (int i = 0; i < entities.size(); i++) {
                        originalIndex.put(entities.get(i), i);
                    }

                    entities.sort((e1, e2) -> {
                        Long seq1 = e1.getCurrentDownloadSequence();
                        Long seq2 = e2.getCurrentDownloadSequence();

                        if (seq1 == null && seq2 == null) {
                            return Integer.compare(originalIndex.get(e1), originalIndex.get(e2));
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
                            queueEntry.updateStatusQuiet(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
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

                    main.getClipboardManager().unblock();

                    boolean dbInitialized = main.getConfig().isPersistenceDatabaseInitialized();
                    if (!dbInitialized) {
                        main.getConfig().setPersistenceDatabaseInitialized(true);
                        main.updateConfig();
                    }

                    initialized.set(true);
                    fireListeners();
                }
            });
        }
    }

    public boolean isInitialized() {
        return initialized.get();
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
            .sorted((d1, d2)
                -> Integer.compare(d2.getPreferenceScore(inputUrl), d1.getPreferenceScore(inputUrl)))
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

    private StartButtonMode resolveStartButtonMode(QueueEntry entry) {
        QueueCategoryEnum category = entry.getCurrentQueueCategory();
        if (category == null) {
            return StartButtonMode.START;
        }

        return switch (category) {
            case RUNNING ->
                StartButtonMode.STOP;
            case COMPLETED, FAILED ->
                StartButtonMode.RESTART;
            default ->
                StartButtonMode.START;
        };
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
                                    true,
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

            if (!force && main.getConfig().isEnableDownloadHistory()
                && main.getConfig().isSkipDuplicatesInHistory()
                && persistence.isHistoryInitialized()
                && persistence.getDownloadHistory().isUrlKnown(filteredUrl, inputUrl)) {

                log.info("Skipping {} - already present in the download history", inputUrl);

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

            if (reason == CloseReasonEnum.MANUAL) {
                recentlyDeletedUrls.put(queueEntry.getOriginalUrl(), System.currentTimeMillis());
            }
        });

        queueEntry.getMediaCard().setOnBecomeVisible(() -> {
            queueEntry.refreshLabelIfNeeded();
        });

        queueEntry.getMediaCard().setOnLeftClick(() -> {
            queueEntry.tryOpenMediaFiles();
        });

        queueEntry.getMediaCard().setOnRightClick(() -> {
            queueEntry.createDefaultRightClick(this);
            updateRightClick(queueEntry, queueEntry.getCurrentQueueCategory());
            return queueEntry.getRightClickMenu();
        });

        queueEntry.getMediaCard().setOnInfoClick(() -> {
            MediaInfoPopup.showDetails(main.getGuiManager(), queueEntry);
        });

        Consumer<DownloadManager> startButtonUpdater = (downloadManager) -> {
            MediaCard mediaCard = queueEntry.getMediaCard();
            if (!mediaCard.isClosed()) {
                mediaCard.setStartButtonMode(resolveStartButtonMode(queueEntry));
            }
        };

        // set initial state
        startButtonUpdater.accept(this);

        queueEntry.setStartButtonEventHandler(
            EventDispatcher.registerEDT(DownloadManager.class, startButtonUpdater));

        queueEntry.getMediaCard().setOnStartClick(() -> {
            switch (resolveStartButtonMode(queueEntry)) {
                case STOP ->
                    stopSingleDownload(queueEntry);
                case RESTART ->
                    stopDownload(queueEntry, () -> {
                        resetDownload(queueEntry);
                        submitDownloadTask(queueEntry, true);
                    });
                case START ->
                    submitDownloadTask(queueEntry, true);
            }
        });

        queueEntry.getMediaCard().setOnInfoHover((hovering) -> {
            if (hovering) {
                MediaInfoPopup.showPreview(main.getGuiManager(), queueEntry);
            } else {
                MediaInfoPopup.hidePreview(main.getGuiManager(), queueEntry);
            }
        });

        queueEntry.getMediaCard().setOnFormatsClick(() -> {
            MediaInfoPopup.showFormatSelector(main.getGuiManager(), queueEntry);
        });

        queueEntry.getMediaCard().setOnSwap((targetCard) -> {
            Optional<QueueEntry> entryOptional = locateEntryByMediaCard(targetCard);

            if (entryOptional.isPresent()) {
                sequencer.reorderEntries(queueEntry, entryOptional.get());
            }
        });

        queueEntry.getMediaCard().setDropTargetValidator(() -> {
            return sequencer.contains(queueEntry);
        });

        queryMetadata(queueEntry);

        sequencer.addNewEntry(queueEntry);
        fireListeners();

        setSkipDownload(queueEntry, queueEntry.isSkipped());

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

    private void recordHistoryEntry(QueueEntry entry) {
        if (!main.getConfig().isEnableDownloadHistory()
            || !persistence.isHistoryInitialized()
            || !entry.getRecordedInHistory().compareAndSet(false, true)) {
            return;
        }

        try {
            DownloadHistoryEntity historyEntity = new DownloadHistoryEntity();
            historyEntity.setUrl(entry.getUrl());
            historyEntity.setOriginalUrl(entry.getOriginalUrl());
            historyEntity.setTitle(entry.getTitle());
            historyEntity.setHostDisplayName(entry.getHostDisplayName());
            historyEntity.setDownloaderId(entry.getCurrentDownloader());

            MediaInfo mediaInfo = entry.getMediaInfo();
            if (mediaInfo != null) {
                historyEntity.setBase64EncodedThumbnail(mediaInfo.getBase64EncodedThumbnail());
            }

            List<String> filePaths = entry.getFinalMediaFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
            historyEntity.setFilePaths(filePaths);

            historyEntity.setDownloadedAt(System.currentTimeMillis());

            persistence.getDownloadHistory().upsert(historyEntity);
        } catch (Exception e) {
            log.error("Failed to record download history entry for {}", entry.getUrl(), e);
        }
    }

    public void redownloadFromHistory(DownloadHistoryEntity historyEntry) {
        String urlToCapture = notNullOrEmpty(historyEntry.getOriginalUrl())
            ? historyEntry.getOriginalUrl() : historyEntry.getUrl();

        QueueEntry existingEntry = sequencer.getEntry(candidate
            -> candidate.getUrl().equals(historyEntry.getUrl())
            || candidate.getUrl().equals(historyEntry.getOriginalUrl())
            || candidate.getOriginalUrl().equals(historyEntry.getUrl())
            || candidate.getOriginalUrl().equals(historyEntry.getOriginalUrl()));

        if (existingEntry != null) {
            existingEntry.dispose(CloseReasonEnum.MANUAL);
        }

        linkCaptureLock.lock();
        try {
            capturedLinks.remove(historyEntry.getUrl());
            capturedLinks.remove(historyEntry.getOriginalUrl());
            capturedPlaylists.remove(historyEntry.getUrl());
            capturedPlaylists.remove(historyEntry.getOriginalUrl());
        } finally {
            linkCaptureLock.unlock();
        }

        captureUrl(urlToCapture, true);
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
            requeueEntry(entry, false);
        });

        startDownloads(suggestedDownloaderId.get());
        fireListeners();
    }

    public void requeueEntry(QueueEntry entry) {
        requeueEntry(entry, true);
    }

    public void requeueEntry(QueueEntry entry, boolean cleanDirectories) {
        resetDownload(entry, cleanDirectories);
        offerTo(QUEUED, entry);
    }

    // TODO: reinsert card at the correct spot / undo history
    public void undoLastDelete() {
        String url = recentlyDeletedUrls.pollNewest();

        if (url != null) {
            captureUrl(url, true);
        }
    }

    public void queueSpecificFormat(QueueEntry entry, FormatInfo format) {
        if (format == null || format.getFormatId() == null) {
            return;
        }

        boolean ytDlpCompatible = entry.getDownloaders().stream()
            .anyMatch(downloader -> downloader.getDownloaderId() == DownloaderIdEnum.YT_DLP);

        if (!ytDlpCompatible) {
            log.warn("Cannot queue a specific format for a non yt-dlp compatible entry: {}", entry.getUrl());
            return;
        }

        entry.queueFormatForDownload(format.getFormatId());

        ToastMessenger.show(Message.builder()
            .message("gui.media_info.format_queued", format.getFormatId(), entry.getQueuedFormatCount())
            .durationMillis(3000)
            .messageType(MessageTypeEnum.INFO)
            .discardDuplicates(true)
            .build());

        QueueCategoryEnum category = entry.getCurrentQueueCategory();
        if (category == COMPLETED || category == FAILED) {
            // Nudge back into the queue
            requeueEntry(entry, false);
        }
    }

    public void dequeueSpecificFormat(QueueEntry entry, String formatId) {
        if (entry.dequeueFormat(formatId)) {
            ToastMessenger.show(Message.builder()
                .message("gui.media_info.format_dequeued", formatId, entry.getQueuedFormatCount())
                .durationMillis(3000)
                .messageType(MessageTypeEnum.INFO)
                .discardDuplicates(true)
                .build());
        }
    }

    public void processQueue() {
        int maxDownloads = main.getConfig().getMaxSimultaneousDownloads();

        while (downloadsRunning.get()
            && downloadsManuallyStarted.get()
            && !sequencer.isEmpty(QUEUED)
            && sequencer.getCount(RUNNING) < maxDownloads) {

            QueueEntry entry = sequencer.fetchNext();
            if (entry == null) {
                //log.info("No more entries to fetch from queue");
                break;
            }

            submitDownloadTask(entry, false);
        }

        if (!metadataQueryQueue.isEmpty()) {
            if (currentlyQueryingCount.get() < main.getConfig().getMaxSimultaneousQueryMetadataTasks()) {
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
        entry.updateExtraRightClickOptions();

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

    protected void setSkipDownload(QueueEntry queueEntry, boolean shouldSkip) {
        queueEntry.setSkipped(shouldSkip);

        stopDownload(queueEntry, () -> {
            if (shouldSkip) {
                queueEntry.updateStatus(DownloadStatusEnum.SKIPPED,
                    l10n("gui.download_status.skipped"));

                if (queueEntry.getCurrentQueueCategory() == RUNNING) {
                    offerTo(QUEUED, queueEntry);
                }
            } else if (queueEntry.getDownloadStatus() == DownloadStatusEnum.SKIPPED) {
                queueEntry.updateStatus(DownloadStatusEnum.QUEUED,
                    l10n("gui.download_status.not_started"));
            }
        });
    }

    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        try {
            String url = queueEntry.getUrl();

            Optional<MediaInfo> mediaInfoOptional = getMetadataManager().fetchMetadata(url);

            if (mediaInfoOptional.isPresent()) {
                MediaInfo mediaInfo = mediaInfoOptional.get();
                queueEntry.setMediaInfo(mediaInfo);

                return true;
            }
        } catch (Exception e) {
            log.error("Failed to query for metadata {}: {}", queueEntry.getUrl(), e.getMessage());

            if (log.isDebugEnabled()) {
                log.error("Exception:", e);
            }
        }

        return false;
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
        if (!main.getConfig().isQueryMetadata()) {
            return;
        }

        currentlyQueryingCount.incrementAndGet();

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            try {
                if (queueEntry.getCancelHook().get()) {
                    return;
                }

                boolean handledByDownloader = false;
                for (AbstractDownloader downloader : queueEntry.getDownloaders()) {
                    if (downloader.tryQueryMetadata(queueEntry)) {
                        log.debug("Metadata provided by downloader: {}", downloader.getDownloaderId());
                        handledByDownloader = true;
                        break;
                    }
                }

                if (!handledByDownloader) {
                    tryQueryMetadata(queueEntry);
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
        resetDownload(queueEntry, true);
    }

    protected void resetDownload(QueueEntry queueEntry, boolean cleanDirectories) {
        queueEntry.createDefaultRightClick(this);

        if (cleanDirectories) {
            queueEntry.cleanDirectories();
        }

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
        }, GDownloader.GLOBAL_THREAD_POOL).thenRun(() -> {
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

    public void stopSingleDownload(QueueEntry entry) {
        if (entry.getCurrentQueueCategory() != RUNNING) {
            return;
        }

        if (entry.getStartedFromQueueProcessor().get()) {
            setSkipDownload(entry, true);
        } else {
            stopDownload(entry, () -> {
                entry.updateStatus(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
                offerTo(QUEUED, entry);
            });
        }
    }

    protected void submitDownloadTask(QueueEntry entry, boolean force) {
        MediaCard mediaCard = entry.getMediaCard();
        if (mediaCard.isClosed()) {
            return;
        }

        if (entry.getForcedFormatId() == null && entry.hasQueuedFormats()) {
            String nextFormatId = entry.pollNextQueuedFormat();
            if (nextFormatId != null) {
                entry.setForcedFormatId(nextFormatId);
                // unless someone with more time and patience than me wants to implement and maintain individual Java bindings for every single gallery-dl extractor
                // gallery-dl support is basic at best and crippled until proper universal metadata extraction through the CLI (or better yet, sockets/http) is possible
                // until then, we mostly default to YT_DLP, which exposes a mature, standardized JSON object we can actually rely on
                // no matter what URL we throw at it, the fields we need are exactly where they're supposed to be
                entry.setForcedDownloader(DownloaderIdEnum.YT_DLP);
            }
        }

        entry.getStartedFromQueueProcessor().set(!force);

        if (entry.getCurrentQueueCategory() != RUNNING) {
            offerTo(RUNNING, entry, false);
        }

        fireListeners();

        entry.setSkipped(false);

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
                                    boolean isBackingOff = intervalometer.getCurrentBackoffTier(entry.getUrl()) > 0;
                                    entry.updateStatus(DownloadStatusEnum.WAITING,
                                        isBackingOff
                                            ? l10n("gui.intervalometer.waiting_backoff", currentWaitTime)
                                            : l10n("gui.intervalometer.waiting", currentWaitTime));

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

                        if (main.getConfig().isRandomIntervalBetweenDownloads()
                            && entry.getRateLimitDetected().compareAndSet(true, false)) {
                            intervalometer.notifyRateLimited(entry.getUrl());
                        }

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
                            if (main.getConfig().isRandomIntervalBetweenDownloads()) {
                                intervalometer.notifySuccess(entry.getUrl());
                            }

                            entry.updateStatus(DownloadStatusEnum.POST_PROCESSING,
                                l10n("gui.download_status.processing_media_files"));
                            downloader.processMediaFiles(entry);

                            entry.updateMediaRightClickOptions();

                            updatePriority(entry, DownloadPriorityEnum.NORMAL);

                            entry.updateStatus(DownloadStatusEnum.COMPLETE,
                                l10n("gui.download_status.finished"));
                            entry.cleanDirectories();

                            if (entry.hasQueuedFormats()) {
                                stopDownload(entry, () -> {
                                    resetDownload(entry);
                                    submitDownloadTask(entry, false);
                                });

                                return;
                            }

                            offerTo(COMPLETED, entry);

                            recordHistoryEntry(entry);

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

                if (entry.getForcedFormatId() != null) {
                    entry.setForcedFormatId(null);
                    entry.setForcedDownloader(null);
                }

                if (entry.getCurrentQueueCategory() == RUNNING
                    && sequencer.contains(entry)
                    && !entry.hasQueuedFormats()) {

                    if (!entry.getCancelHook().get()) {
                        log.error("Unexpected RUNNING download state, switching to QUEUED");
                    } else {
                        log.debug("Download {} left in RUNNING pending a manual stop, recovering to QUEUED", entry.getDownloadId());
                    }

                    offerTo(QUEUED, entry);
                }
            }
        });
    }

    public void downloadSpecificFormat(QueueEntry entry, FormatInfo format) {
        if (format == null || format.getFormatId() == null) {
            return;
        }

        boolean ytDlpCompatible = entry.getDownloaders().stream()
            .anyMatch(downloader -> downloader.getDownloaderId() == DownloaderIdEnum.YT_DLP);

        if (!ytDlpCompatible) {
            log.warn("Cannot download a specific format for a non yt-dlp compatible entry: {}", entry.getUrl());
            return;
        }

        entry.setForcedFormatId(format.getFormatId());
        entry.setForcedDownloader(DownloaderIdEnum.YT_DLP);

        stopDownload(entry, () -> {
            resetDownload(entry);
            submitDownloadTask(entry, true);
        });
    }

    public void migrateCacheDirectory() {
        // TODO: Move partial playlists when changing download paths
    }

    public void processTaskbarProgress() {
        TaskbarManager taskbarManager = main.getGuiManager().getTaskbarManager();
        if (taskbarManager == null || !taskbarManager.isTaskbarSupported()) {
            return;
        }

        int runningCount = getRunningDownloads();
        int failedCount = getFailedDownloads();
        int queuedCount = getQueuedDownloads();
        int completedCount = getCompletedDownloads();

        int badgeCount = queuedCount + runningCount;

        if (!isRunning()) {
            taskbarManager.setBadgeValue(0);
            taskbarManager.setProgressValue(0);

            if (failedCount > 0 && queuedCount == 0) {
                taskbarManager.setTaskbarState(TaskbarState.ERROR);
            } else {
                taskbarManager.setTaskbarState(TaskbarState.OFF);
            }

            return;
        }

        taskbarManager.setBadgeValue(badgeCount);

        if (badgeCount > 50) {
            // With >50 downloads, the variation becomes so small we can simplify the progress calculation
            int finishedDownloads = completedCount + failedCount;
            int totalCount = sequencer.getTotalCount();

            double completionPercentage = (double)finishedDownloads / totalCount * 100;
            taskbarManager.setTaskbarState(TaskbarState.NORMAL);
            taskbarManager.setProgressValue(completionPercentage);

            return;
        }

        List<QueueEntry> allEntries = sequencer.getAllEntries();
        List<Double> validPercentages = allEntries.stream()
            .map(e -> e.getPerceivedPercentage())
            .filter(percentage -> percentage >= 0)
            .collect(Collectors.toList());

        if (validPercentages.isEmpty()) {
            taskbarManager.setTaskbarState(TaskbarState.INDETERMINATE);
            // This might happen if all entries return -1, indicating-
            // gallery-dl/spotDL downloaders where we are unable to track progress.
            return;
        }

        int fillerNeeded = allEntries.size() - validPercentages.size();
        for (int i = 0; i < fillerNeeded; i++) {
            validPercentages.add(0d);
        }

        double averagePercentage = MathUtils.calculateAveragePercentage(validPercentages);

        taskbarManager.setTaskbarState(TaskbarState.NORMAL);
        taskbarManager.setProgressValue(averagePercentage);
    }

    @PreDestroy
    @Override
    public void close() {
        stopDownloads();

        clearQueue(RUNNING, CloseReasonEnum.SHUTDOWN, false);
        clearQueue(CloseReasonEnum.SHUTDOWN);

        for (AbstractDownloader downloader : downloaders) {
            downloader.close();
        }
    }
}
