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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.PlayListOptionEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;
import net.brlns.gdownloader.settings.filters.YoutubeFilter;
import net.brlns.gdownloader.settings.filters.YoutubePlaylistFilter;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.util.ConcurrentRearrangeableDeque;
import net.brlns.gdownloader.util.LinkedIterableBlockingQueue;
import net.brlns.gdownloader.util.Nullable;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.ALWAYS_ASK;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.DOWNLOAD_PLAYLIST;
import static net.brlns.gdownloader.settings.enums.PlayListOptionEnum.DOWNLOAD_SINGLE;
import static net.brlns.gdownloader.util.URLUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadManager {

    private static final int MAX_DOWNLOAD_RETRIES = 10;

    @Getter(AccessLevel.PROTECTED)
    private final GDownloader main;

    private final ExecutorService processMonitor;

    private final List<Consumer<DownloadManager>> listeners = new ArrayList<>();

    private final List<AbstractDownloader> downloaders = new ArrayList<>();

    private final Set<String> capturedLinks = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> capturedPlaylists = Collections.synchronizedSet(new HashSet<>());

    private final ConcurrentRearrangeableDeque<QueueEntry> downloadDeque
        = new ConcurrentRearrangeableDeque<>();

    private final Queue<QueueEntry> runningQueue = new LinkedIterableBlockingQueue<>();

    private final Queue<QueueEntry> completedDownloads = new ConcurrentLinkedQueue<>();
    private final Queue<QueueEntry> failedDownloads = new ConcurrentLinkedQueue<>();

    private final AtomicInteger runningDownloads = new AtomicInteger();
    private final AtomicInteger downloadCounter = new AtomicInteger();

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);
    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);

    @SuppressWarnings("this-escape")
    public DownloadManager(GDownloader mainIn) {
        main = mainIn;

        processMonitor = Executors.newSingleThreadExecutor();
        processMonitor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                for (QueueEntry entry : runningQueue) {
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

    public void registerListener(Consumer<DownloadManager> consumer) {
        listeners.add(consumer);
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

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force) {
        return captureUrl(inputUrl, force, main.getConfig().getPlaylistDownloadOption());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force, PlayListOptionEnum playlistOption) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        List<AbstractDownloader> compatibleDownloaders = getCompatibleDownloaders(inputUrl);

        if (downloadsBlocked.get() || inputUrl == null
            || compatibleDownloaders.isEmpty() || capturedLinks.contains(inputUrl)) {
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
                        capturedPlaylists.add(filteredUrl);
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
                                            main.handleException(e);
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
                                            main.handleException(e);
                                        }

                                        future.complete(result);
                                    });
                            });

                        GUIManager.DialogButton defaultOption = new GUIManager.DialogButton("", (boolean setDefault) -> {
                            future.complete(false);
                        });

                        main.getGuiManager().showConfirmDialog(
                            l10n("dialog.confirm"),
                            l10n("dialog.download_playlist") + "\n\n" + playlist,
                            30000,
                            defaultOption,
                            playlistDialogOption,
                            singleDialogOption);

                        return future;
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

            MediaCard mediaCard = main.getGuiManager().addMediaCard(main.getConfig().isDownloadVideo(), "");

            int downloadId = downloadCounter.incrementAndGet();

            QueueEntry queueEntry = new QueueEntry(main, mediaCard, filter, inputUrl, filteredUrl, downloadId, compatibleDownloaders);
            queueEntry.updateStatus(DownloadStatusEnum.QUERYING, l10n("gui.download_status.querying"));

            String filtered = filteredUrl;
            mediaCard.setOnClose(() -> {
                queueEntry.close();

                capturedPlaylists.remove(inputUrl);
                capturedLinks.remove(inputUrl);
                capturedLinks.remove(filtered);

                downloadDeque.remove(queueEntry);
                failedDownloads.remove(queueEntry);
                completedDownloads.remove(queueEntry);

                fireListeners();
            });

            mediaCard.setOnLeftClick(() -> {
                main.openDownloadsDirectory();
            });

            mediaCard.getRightClickMenu().putAll(Map.of(
                l10n("gui.open_downloads_directory"),
                () -> main.openDownloadsDirectory(),
                l10n("gui.open_in_browser"),
                () -> queueEntry.openUrl(),
                l10n("gui.copy_url"),
                () -> queueEntry.copyUrlToClipboard()
            ));

            mediaCard.setOnDrag((targetIndex) -> {
                if (downloadDeque.contains(queueEntry)) {
                    try {
                        downloadDeque.moveToPosition(queueEntry,
                            Math.clamp(targetIndex, 0, downloadDeque.size() - 1));
                    } catch (Exception e) {
                        main.handleException(e, false);
                    }
                }
            });

            mediaCard.setValidateDropTarget(() -> {
                return downloadDeque.contains(queueEntry);
            });

            queryVideo(queueEntry);

            downloadDeque.offerLast(queueEntry);
            fireListeners();

            if (main.getConfig().isAutoDownloadStart() && !downloadsRunning.get()) {
                startDownloads();
            }

            future.complete(true);
            return future;
        }

        future.complete(false);
        return future;
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
        if (!downloadsBlocked.get()) {
            downloadsRunning.set(true);
            fireListeners();
        }
    }

    public void stopDownloads() {
        downloadsRunning.set(false);
        fireListeners();
    }

    private void fireListeners() {
        for (Consumer<DownloadManager> listener : listeners) {
            listener.accept(this);
        }
    }

    public int getQueueSize() {
        return downloadDeque.size();
    }

    public int getDownloadsRunning() {
        return runningDownloads.get();
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
            entry.updateStatus(DownloadStatusEnum.QUEUED, l10n("gui.download_status.not_started"));
            entry.resetRetryCounter();// Normaly we want the retry count to stick around, but not in this case.
            entry.resetForRestart();

            downloadDeque.offerLast(entry);
        }

        startDownloads();
        fireListeners();
    }

    public void clearQueue() {
        capturedLinks.clear();
        capturedPlaylists.clear();

        // Active downloads are intentionally immune to this.
        QueueEntry entry;
        while ((entry = downloadDeque.poll()) != null) {
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());

            if (!entry.isRunning()) {
                entry.cleanDirectories();
            }
        }

        while ((entry = failedDownloads.poll()) != null) {
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());

            if (!entry.isRunning()) {
                entry.cleanDirectories();
            }
        }

        while ((entry = completedDownloads.poll()) != null) {
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());
        }

        fireListeners();
    }

    private void queryVideo(QueueEntry queueEntry) {
        main.getGlobalThreadPool().submitWithPriority(() -> {
            if (queueEntry.getCancelHook().get()) {
                return;
            }

            for (AbstractDownloader downloader : queueEntry.getDownloaders()) {
                if (downloader.tryQueryVideo(queueEntry)) {
                    break;
                }
            }

            if (queueEntry.getDownloadStatus() == DownloadStatusEnum.QUERYING) {
                queueEntry.updateStatus(DownloadStatusEnum.QUEUED, l10n("gui.download_status.not_started"));
            }
        }, 1);
    }

    protected void restartDownload(QueueEntry queueEntry) {
        queueEntry.updateStatus(DownloadStatusEnum.QUEUED, l10n("gui.download_status.not_started"));
        queueEntry.resetForRestart();

        completedDownloads.remove(queueEntry);
        downloadDeque.offerLast(queueEntry);
        fireListeners();
    }

    public void processQueue() {
        while (downloadsRunning.get() && !downloadDeque.isEmpty()) {
            if (runningDownloads.get() >= main.getConfig().getMaxSimultaneousDownloads()) {
                break;
            }

            QueueEntry entry = downloadDeque.poll();

            MediaCard mediaCard = entry.getMediaCard();
            if (mediaCard.isClosed()) {
                break;
            }

            runningDownloads.incrementAndGet();
            fireListeners();

            main.getGlobalThreadPool().submitWithPriority(() -> {
                try {
                    if (!downloadsRunning.get()) {
                        downloadDeque.offerFirst(entry);
                        return;
                    }

                    AbstractUrlFilter filter = entry.getFilter();

                    if (filter.areCookiesRequired() && !main.getConfig().isReadCookiesFromBrowser()) {
                        // TODO: Visual cue
                        log.warn("Cookies are required for this website {}", entry.getOriginalUrl());
                    }

                    entry.getRunning().set(true);
                    entry.updateStatus(DownloadStatusEnum.STARTING, l10n("gui.download_status.starting"));

                    try {
                        runningQueue.offer(entry);

                        Iterator<AbstractDownloader> downloaderIterator = entry.getDownloaders().iterator();
                        while (downloaderIterator.hasNext()) {
                            AbstractDownloader downloader = downloaderIterator.next();
                            DownloadResult result = downloader.tryDownload(entry);

                            BitSet flags = result.getFlags();
                            String lastOutput = result.getLastOutput();

                            boolean unsupported = FLAG_UNSUPPORTED.isSet(flags);

                            if (FLAG_MAIN_CATEGORY_FAILED.isSet(flags) || unsupported) {
                                if (unsupported || !main.getConfig().isAutoDownloadRetry()
                                    || entry.getRetryCounter().incrementAndGet() > MAX_DOWNLOAD_RETRIES) {
                                    if (downloaderIterator.hasNext()) {
                                        continue;// Onto the next downloader
                                    } else {
                                        log.error("Download of {} failed, all retry attempts failed.: {} supported downloader: {}",
                                            entry.getUrl(), lastOutput, !unsupported);

                                        entry.updateStatus(DownloadStatusEnum.FAILED, lastOutput);
                                        entry.resetForRestart();

                                        mediaCard.getRightClickMenu().put(
                                            l10n("gui.copy_error_message"),
                                            () -> main.getClipboardManager().copyTextToClipboard(lastOutput));

                                        failedDownloads.offer(entry);
                                    }
                                } else {
                                    log.warn("Download of {} failed, retrying ({}/{}): {}",
                                        entry.getUrl(),
                                        entry.getRetryCounter().get(),
                                        MAX_DOWNLOAD_RETRIES,
                                        lastOutput);

                                    entry.updateStatus(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
                                    entry.resetForRestart();

                                    downloadDeque.offerFirst(entry);
                                }

                                fireListeners();
                                return;
                            }

                            // TODO: Account for other downloaders
                            if (FLAG_NO_METHOD.isSet(flags)) {
                                if (FLAG_NO_METHOD_VIDEO.isSet(flags)) {
                                    log.error("{} - No option to download.", filter);
                                    entry.updateStatus(DownloadStatusEnum.NO_METHOD, l10n("enums.download_status.no_method.video_tip"));
                                    entry.resetForRestart();

                                    failedDownloads.offer(entry);
                                    fireListeners();
                                    return;
                                } else if (FLAG_NO_METHOD_AUDIO.isSet(flags)) {
                                    log.error("{} - No audio quality selected, but was set to download audio only.", filter);
                                    entry.updateStatus(DownloadStatusEnum.NO_METHOD, l10n("enums.download_status.no_method.audio_tip"));
                                    entry.resetForRestart();

                                    failedDownloads.offer(entry);
                                    fireListeners();
                                    return;
                                } else {
                                    throw new IllegalStateException("Unhandled NO_METHOD");
                                }
                            }

                            if (!downloadsRunning.get() || FLAG_STOPPED.isSet(flags)) {
                                entry.updateStatus(DownloadStatusEnum.STOPPED, l10n("gui.download_status.not_started"));
                                entry.resetForRestart();

                                downloadDeque.offerFirst(entry);
                                fireListeners();
                                return;
                            } else if (!entry.getCancelHook().get() && FLAG_SUCCESS.isSet(flags)) {
                                Map<String, Runnable> rightClickOptions = downloader.processMediaFiles(entry);
                                rightClickOptions.put(
                                    l10n("gui.restart_download"),
                                    () -> restartDownload(entry));
                                rightClickOptions.put(
                                    l10n("gui.delete_files"),
                                    () -> entry.deleteMediaFiles());// TODO: remove also menu entries

                                mediaCard.getRightClickMenu().putAll(rightClickOptions);

                                entry.updateStatus(DownloadStatusEnum.COMPLETE, l10n("gui.download_status.finished"));

                                entry.cleanDirectories();

                                completedDownloads.offer(entry);
                                fireListeners();
                                return;
                            } else {
                                log.error("Unexpected download state");
                            }
                        }
                    } finally {
                        runningQueue.remove(entry);
                    }
                } catch (Exception e) {
                    log.error("Failed to download", e);

                    entry.updateStatus(DownloadStatusEnum.FAILED, e.getLocalizedMessage());
                    entry.resetForRestart();

                    downloadDeque.offerLast(entry);// Retry later
                    fireListeners();

                    main.handleException(e);
                } finally {
                    entry.getRunning().set(false);

                    runningDownloads.decrementAndGet();
                    fireListeners();
                }
            }, 10);
        }

        if (downloadsRunning.get() && runningDownloads.get() == 0) {
            stopDownloads();
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
}