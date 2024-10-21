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
package net.brlns.gdownloader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;
import net.brlns.gdownloader.settings.filters.YoutubeFilter;
import net.brlns.gdownloader.settings.filters.YoutubePlaylistFilter;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.GUIManager.DialogButton;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.util.ConcurrentRearrangeableDeque;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.PriorityThreadPoolExecutor;

import static net.brlns.gdownloader.Language.*;
import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.util.URLUtils.*;

//TODO media converter
//TODO implement CD Ripper
//TODO d&d files for conversion to different formats, we already have ffmpeg anyway
//
//TODO max simultaneous downloads should be independent per website
//TODO silence unnecessary debug messages
//TODO investigate adding AppImage build
//TODO we should only grab clipboard AFTER the toggle button is clicked
//TODO scale on resolution DPI
//TODO save last window size in config
//TODO keep older versions of ytdlp and retry failed downloads against them
//TODO Advanced users can edit the config.json directly to add extra yt-dlp arguments like proxy settings. but maybe expose those settings to the ui.
//TODO verify checksums during updates, add bouncycastle, check signatures
//TODO write a component factory for GUIManager
//TODO git actions build for different platforms
//FEEDBACK Should choose to download video and audio independently on each card
//TODO maybe add notifications for each toggled toolbar option
//DROPPED check updates on a timer, but do not ever restart when anything is in the queue.
//TODO individual 'retry failed download' button
//TODO --no-playlist when single video option is active
//TODO Artifacting seems to be happening on the scroll pane with AMD video cards
//TODO open a window asking which videos in a playlist to download or not
//TODO RearrangeableDeque's offerLast should be linked to the cards in the UI
//TODO Better visual eye candy for when dragging cards
//TODO Add setting to allow the user to manually specify the target codec for audio transcoding? currently it defaults to aac.
//TODO Add 'Clear Completed Downloads' button.
//TODO Javadoc, a whole lot of it.
//TODO Refactor this very class. Separate some logic into different methods.
//TODO Twitch settings purposefully default to suboptimal quality due to huge file sizes. Maybe consider adding a warning about this in the GUI.
//TODO Split GUI into a different subproject from core logic.
//TODO Investigate screen reader support (https://www.nvaccess.org/download/)
//TODO Send notifications when a NO_METHOD is triggered, explaining why it was triggered.
//TODO Test downloading sections of a livestream (currently it gets stuck on status PREPARING). Note: it also leaves a zombie ffmpeg process behind dealing with the hls stream.
//TODO The issue above is a yt-dlp bug https://github.com/yt-dlp/yt-dlp/issues/7927
//TODO Add a viewer for log files.
//TODO Implement rate-limiting options internally; the way it's currently implemented does not account for concurrent or non-playlist downloads.
//TODO Notify the user whenever a setting that requires restart was changed.
//TODO Settings version
//TODO Quit lingering ffmpeg processes spawned by yt-dlp
//Off to a bootcamp, project on pause
/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpDownloader{

    private final GDownloader main;

    private final ScheduledExecutorService processMonitor;
    private final PriorityThreadPoolExecutor downloadScheduler;

    private final List<Consumer<YtDlpDownloader>> listeners = new ArrayList<>();

    private final Set<String> capturedLinks = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> capturedPlaylists = Collections.synchronizedSet(new HashSet<>());

    private final ConcurrentRearrangeableDeque<QueueEntry> downloadDeque
        = new ConcurrentRearrangeableDeque<>();

    private final Queue<QueueEntry> runningQueue = new ConcurrentLinkedQueue<>();

    private final Queue<QueueEntry> completedDownloads = new ConcurrentLinkedQueue<>();
    private final Queue<QueueEntry> failedDownloads = new ConcurrentLinkedQueue<>();

    private final AtomicInteger runningDownloads = new AtomicInteger();
    private final AtomicInteger downloadCounter = new AtomicInteger();

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);
    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);

    @Getter
    @Setter
    private File ytDlpPath = null;

    @Getter
    @Setter
    private File ffmpegPath = null;

    public YtDlpDownloader(GDownloader mainIn){
        main = mainIn;

        processMonitor = Executors.newScheduledThreadPool(1);
        processMonitor.scheduleAtFixedRate(() -> {
            for(QueueEntry entry : runningQueue){
                if(!downloadsRunning.get() || entry.getCancelHook().get()){
                    Process process = entry.getProcess();

                    if(process != null && process.isAlive()){
                        log.info("Process Monitor is stopping {}", entry.getUrl());

                        try{
                            tryStopProcess(process);
                        }catch(Exception e){
                            log.error("Interrupted {}", e.getMessage());
                        }
                    }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        //10 Threads for now. This caps us at a maximum of 10 concurrent downloads.
        //I hope you have plenty of cpu threads to spare if you wish to push this further.
        downloadScheduler = new PriorityThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS);
    }

    public boolean isBlocked(){
        return downloadsBlocked.get();
    }

    public void block(){
        downloadsBlocked.set(true);

        fireListeners();
    }

    public void unblock(){
        downloadsBlocked.set(false);

        fireListeners();
    }

    public void registerListener(Consumer<YtDlpDownloader> consumer){
        listeners.add(consumer);
    }

    private boolean isGarbageUrl(String inputUrl){
        return inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/")
            || inputUrl.endsWith(".jpg")
            || inputUrl.endsWith(".png")
            || inputUrl.endsWith(".webp");
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force){
        return captureUrl(inputUrl, force, main.getConfig().getPlaylistDownloadOption());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, boolean force, PlayListOptionEnum playlistOption){
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if(downloadsBlocked.get() || ytDlpPath == null || inputUrl == null || isGarbageUrl(inputUrl) || capturedLinks.contains(inputUrl)){
            future.complete(false);
            return future;
        }

        Optional<AbstractUrlFilter> filterOptional = getFilterForUrl(inputUrl,
            main.getConfig().isCaptureAnyLinks() || force);

        if(!filterOptional.isPresent()){
            log.error("No filter found for url: {}. Ignoring.", inputUrl);
            future.complete(false);
            return future;
        }

        AbstractUrlFilter filter = filterOptional.get();
        log.info("URL: {} matched {}", inputUrl, filter);

        if(!filter.canAcceptUrl(inputUrl, main)){
            log.info("Filter {} has denied to accept url {}; Verify settings.", filter, inputUrl);
            future.complete(false);
            return future;
        }

        String filteredUrl;
        //TODO: move these to the appropriate classes.
        if(filter instanceof YoutubePlaylistFilter){
            switch(playlistOption){
                case DOWNLOAD_PLAYLIST: {
                    filteredUrl = filterPlaylist(inputUrl);

                    if(filteredUrl != null){
                        capturedPlaylists.add(filteredUrl);
                    }

                    break;
                }

                case DOWNLOAD_SINGLE: {
                    String playlist = filterPlaylist(inputUrl);

                    if(playlist != null){
                        capturedPlaylists.add(playlist);
                    }

                    String video = filterVideo(inputUrl);

                    log.info("Video url is {}", video);

                    if(video != null && video.contains("?v=") && !video.contains("list=")){
                        return captureUrl(video, force);
                    }else{
                        future.complete(false);
                        return future;
                    }
                }

                case ALWAYS_ASK:
                default: {
                    String playlist = filterPlaylist(inputUrl);

                    if(playlist == null){
                        future.complete(false);
                        return future;
                    }

                    if(!capturedPlaylists.contains(playlist)){
                        DialogButton playlistDialogOption = new DialogButton(PlayListOptionEnum.DOWNLOAD_PLAYLIST.getDisplayName(), (boolean setDefault) -> {
                            if(setDefault){
                                main.getConfig().setPlaylistDownloadOption(PlayListOptionEnum.DOWNLOAD_PLAYLIST);
                                main.updateConfig();
                            }

                            captureUrl(playlist, force, PlayListOptionEnum.DOWNLOAD_PLAYLIST)
                                .whenComplete((Boolean result, Throwable e) -> {
                                    if(e != null){
                                        main.handleException(e);
                                    }

                                    future.complete(result);
                                });
                        });

                        DialogButton singleDialogOption = new DialogButton(PlayListOptionEnum.DOWNLOAD_SINGLE.getDisplayName(), (boolean setDefault) -> {
                            if(setDefault){
                                main.getConfig().setPlaylistDownloadOption(PlayListOptionEnum.DOWNLOAD_SINGLE);
                                main.updateConfig();
                            }

                            captureUrl(inputUrl, force, PlayListOptionEnum.DOWNLOAD_SINGLE)
                                .whenComplete((Boolean result, Throwable e) -> {
                                    if(e != null){
                                        main.handleException(e);
                                    }

                                    future.complete(result);
                                });
                        });

                        DialogButton defaultOption = new DialogButton("", (boolean setDefault) -> {
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
                    }else{
                        //TODO I'm assuming this is a wanted behavior - having subsequent links being treated as individual videos
                        //It's odd that you'd download a whole playlist and then an individual video from it though, maybe investigate use cases
                        String video = filterVideo(inputUrl);

                        log.info("Individual video url is {}", video);

                        if(video != null && video.contains("?v=") && !video.contains("list=")){
                            return captureUrl(video, force);
                        }else{
                            future.complete(false);
                            return future;
                        }
                    }
                }
            }
        }else if(filter instanceof YoutubeFilter){
            filteredUrl = filterVideo(inputUrl);
        }else{
            filteredUrl = inputUrl;
        }

        if(filteredUrl == null){
            log.error("Filtered url was null.");
            future.complete(false);
            return future;
        }

        if(capturedLinks.add(filteredUrl)){
            capturedLinks.add(inputUrl);

            log.info("Captured {}", inputUrl);

            MediaCard mediaCard = main.getGuiManager().addMediaCard(main.getConfig().isDownloadVideo(), "");

            int downloadId = downloadCounter.incrementAndGet();

            QueueEntry queueEntry = new QueueEntry(main, mediaCard, filter, inputUrl, filteredUrl, downloadId);
            queueEntry.updateStatus(DownloadStatus.QUERYING, l10n("gui.download_status.querying"));

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
                if(downloadDeque.contains(queueEntry)){
                    try{
                        downloadDeque.moveToPosition(queueEntry,
                            Math.clamp(targetIndex, 0, downloadDeque.size() - 1));
                    }catch(Exception e){
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

            if(main.getConfig().isAutoDownloadStart()){
                if(!downloadsRunning.get() && !downloadDeque.isEmpty()){
                    startDownloads();
                }
            }

            future.complete(true);
            return future;
        }

        future.complete(false);
        return future;
    }

    private Optional<AbstractUrlFilter> getFilterForUrl(String url, boolean allowAnyLink){
        AbstractUrlFilter filter = null;

        for(AbstractUrlFilter filterNeedle : main.getConfig().getUrlFilters()){
            if(filterNeedle.matches(url)){
                filter = filterNeedle;
                break;
            }
        }

        if(filter == null && allowAnyLink){
            for(AbstractUrlFilter filterNeedle : main.getConfig().getUrlFilters()){
                if(filterNeedle.getId().equals(GenericFilter.ID)){
                    filter = filterNeedle;
                    break;
                }
            }
        }

        return Optional.ofNullable(filter);
    }

    public boolean isRunning(){
        return downloadsRunning.get();
    }

    public void toggleDownloads(){
        if(!isRunning()){
            startDownloads();
        }else{
            stopDownloads();
        }
    }

    public void startDownloads(){
        if(!downloadsBlocked.get()){
            downloadsRunning.set(true);

            fireListeners();
        }
    }

    public void stopDownloads(){
        downloadsRunning.set(false);

        fireListeners();
    }

    private void fireListeners(){
        for(Consumer<YtDlpDownloader> listener : listeners){
            listener.accept(this);
        }
    }

    public int getQueueSize(){
        return downloadDeque.size();
    }

    public int getDownloadsRunning(){
        return runningDownloads.get();
    }

    public int getFailedDownloads(){
        return failedDownloads.size();
    }

    public int getCompletedDownloads(){
        return completedDownloads.size();
    }

    public void retryFailedDownloads(){
        QueueEntry entry;
        while((entry = failedDownloads.poll()) != null){
            entry.updateStatus(DownloadStatus.QUEUED, l10n("gui.download_status.not_started"));
            entry.reset();

            downloadDeque.offerLast(entry);
        }

        startDownloads();

        fireListeners();
    }

    public void clearQueue(){
        capturedLinks.clear();
        capturedPlaylists.clear();

        //Active downloads are intentionally immune to this.
        QueueEntry entry;
        while((entry = downloadDeque.poll()) != null){
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());
            //downloadDeque.remove(entry);

            if(!entry.isRunning()){
                entry.clean();
            }
        }

        while((entry = failedDownloads.poll()) != null){
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());

            if(!entry.isRunning()){
                entry.clean();
            }
        }

        while((entry = completedDownloads.poll()) != null){
            main.getGuiManager().removeMediaCard(entry.getMediaCard().getId());
        }

        fireListeners();
    }

    private void queryVideo(QueueEntry queueEntry){
//      if(queueEntry.getWebFilter() == WebFilterEnum.DEFAULT){
//          queueEntry.updateStatus(DownloadStatus.QUEUED, l10n("gui.download_status.not_started"));
//          return;
//      }

        downloadScheduler.submitWithPriority(() -> {
            try{
                if(queueEntry.getCancelHook().get()){
                    return;
                }

                long start = System.currentTimeMillis();

                List<String> arguments = new ArrayList<>();
                arguments.addAll(List.of(
                    ytDlpPath.getAbsolutePath(),
                    "--dump-json",
                    "--flat-playlist",
                    "--extractor-args",
                    "youtube:player_skip=webpage,configs,js;player_client=android,web",
                    queueEntry.getUrl()
                ));

                if(main.getConfig().isReadCookies()){
                    arguments.addAll(List.of(
                        "--cookies-from-browser",
                        main.getBrowserForCookies().getName()
                    ));
                }

                List<String> list = GDownloader.readOutput(
                    arguments.stream().toArray(String[]::new));

                if(main.getConfig().isDebugMode()){
                    long what = System.currentTimeMillis() - start;
                    double on = 1000L * 365.25 * 24 * 60 * 60 * 1000;
                    double earth = (what / on) * 100;

                    log.info("The slow as molasses thing took {}ms, jesus man! that's about {}% of a millenium",
                        what, String.format("%.12f", earth));
                }

                for(String line : list){
                    if(!line.startsWith("{")){
                        continue;
                    }

                    VideoInfo info = GDownloader.OBJECT_MAPPER.readValue(line, VideoInfo.class);

                    queueEntry.setVideoInfo(info);
                    break;
                }
            }catch(Exception e){
                log.error("Failed to parse json {}", e.getLocalizedMessage());
            }finally{
                if(queueEntry.getDownloadStatus() == DownloadStatus.QUERYING){
                    queueEntry.updateStatus(DownloadStatus.QUEUED, l10n("gui.download_status.not_started"));
                }
            }
        }, 1);
    }

    public void processQueue(){
        while(downloadsRunning.get() && !downloadDeque.isEmpty()){
            if(runningDownloads.get() >= main.getConfig().getMaxSimultaneousDownloads()){
                break;
            }

            QueueEntry entry = downloadDeque.poll();
            //downloadDeque.remove(entry);

            MediaCard mediaCard = entry.getMediaCard();
            if(mediaCard.isClosed()){
                break;
            }

            runningDownloads.incrementAndGet();
            fireListeners();

            downloadScheduler.submitWithPriority(() -> {
                if(!downloadsRunning.get()){
                    downloadDeque.offerFirst(entry);

                    runningDownloads.decrementAndGet();
                    fireListeners();
                    return;
                }

                try{
                    AbstractUrlFilter filter = entry.getFilter();

                    if(filter.areCookiesRequired() && !main.getConfig().isReadCookies()){
                        //TODO: Visual cue
                        log.warn("Cookies required for this website {}", entry.getOriginalUrl());
                    }

                    boolean downloadAudio = main.getConfig().isDownloadAudio();
                    boolean downloadVideo = main.getConfig().isDownloadVideo();

                    if(!downloadAudio && !downloadVideo){
                        entry.updateStatus(DownloadStatus.NO_METHOD, l10n("enums.download_status.no_method.video_tip"));
                        entry.reset();

                        failedDownloads.offer(entry);

                        log.error("{} - No option to download.", filter);

                        if(downloadDeque.size() <= 1){
                            stopDownloads();
                        }

                        return;
                    }

                    QualitySettings quality = filter.getQualitySettings();

                    AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

                    if(!downloadVideo && downloadAudio && audioBitrate == AudioBitrateEnum.NO_AUDIO){
                        entry.updateStatus(DownloadStatus.NO_METHOD, l10n("enums.download_status.no_method.audio_tip"));
                        entry.reset();

                        failedDownloads.offer(entry);

                        log.error("{} - No audio quality selected, but was set to download audio only.", filter);

                        if(downloadDeque.size() <= 1){
                            stopDownloads();
                        }

                        return;
                    }

                    entry.getRunning().set(true);
                    entry.updateStatus(DownloadStatus.STARTING, l10n("gui.download_status.starting"));

                    File finalPath = main.getOrCreateDownloadsDirectory();

                    File tmpPath = GDownloader.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
                    entry.setTmpDirectory(tmpPath);

                    List<String> genericArguments = new ArrayList<>();

                    genericArguments.addAll(List.of(
                        ytDlpPath.getAbsolutePath(),
                        "-i"
                    ));

                    if(ffmpegPath != null){
                        genericArguments.addAll(List.of(
                            "--ffmpeg-location",
                            ffmpegPath.getAbsolutePath()
                        ));
                    }

                    genericArguments.addAll(filter.getArguments(ALL, main, tmpPath));

                    boolean wasStopped = false;

                    try{
                        runningQueue.add(entry);

                        for(DownloadTypeEnum type : DownloadTypeEnum.values()){
                            if(type == ALL){
                                continue;
                            }

                            if(type == VIDEO && !downloadVideo
                                || type == AUDIO && !downloadAudio
                                || type == SUBTITLES && !main.getConfig().isDownloadSubtitles()
                                || type == THUMBNAILS && !main.getConfig().isDownloadThumbnails()){
                                continue;
                            }

                            List<String> arguments = new ArrayList<>(genericArguments);

                            List<String> downloadArguments = filter.getArguments(type, main, tmpPath);
                            arguments.addAll(downloadArguments);

                            log.debug("ALL {}: Type {} ({}): {}",
                                genericArguments,
                                type,
                                filter.getDisplayName(),
                                downloadArguments);

                            Pair<Integer, String> result = processDownload(entry, arguments);

                            if(result != null){
                                if(result.getKey() != 0){
                                    if(!entry.getCancelHook().get()){
                                        if(type == VIDEO || type == AUDIO){
                                            entry.updateStatus(DownloadStatus.FAILED, result.getValue());
                                            entry.reset();

                                            failedDownloads.offer(entry);
                                        }else{
                                            //These can be treated as low priority downloads since thumbnails
                                            //and subtitles are already embedded by default, if they fail we just move on.
                                            //For now, downloading only subs or thumbs is not supported.
                                            log.error("Failed to download {}: {}", type, result.getValue());
                                            continue;
                                        }
                                    }

                                    fireListeners();
                                    return;
                                }
                            }else{
                                wasStopped = true;
                            }
                        }
                    }finally{
                        runningQueue.remove(entry);
                    }

                    if(!downloadsRunning.get() || wasStopped){
                        entry.updateStatus(DownloadStatus.STOPPED, l10n("gui.download_status.not_started"));
                        entry.reset();

                        downloadDeque.offerFirst(entry);
                        fireListeners();
                    }else if(!entry.getCancelHook().get()){
                        Map<String, Runnable> rightClickOptions = new TreeMap<>();

                        try(Stream<Path> dirStream = Files.walk(tmpPath.toPath())){
                            dirStream.forEach(path -> {
                                String fileName = path.getFileName().toString().toLowerCase();

                                boolean isAudio = fileName.endsWith(")." + quality.getAudioContainer().getValue());
                                boolean isVideo = fileName.endsWith(")." + quality.getVideoContainer().getValue());
                                boolean isSubtitle = fileName.endsWith("." + quality.getSubtitleContainer().getValue());
                                boolean isThumbnail = fileName.endsWith("." + quality.getThumbnailContainer().getValue());

                                if(isAudio || isVideo
                                    || isSubtitle && main.getConfig().isDownloadSubtitles()
                                    || isThumbnail && main.getConfig().isDownloadThumbnails()){

                                    Path relativePath = tmpPath.toPath().relativize(path);
                                    Path targetPath = finalPath.toPath().resolve(relativePath);

                                    try{
                                        Files.createDirectories(targetPath.getParent());
                                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                        entry.getFinalMediaFiles().add(targetPath.toFile());

                                        if(isVideo){
                                            rightClickOptions.put(
                                                l10n("gui.play_video"),
                                                () -> entry.play(VideoContainerEnum.class));
                                        }

                                        if(isAudio){
                                            rightClickOptions.put(
                                                l10n("gui.play_audio"),
                                                () -> entry.play(AudioContainerEnum.class));
                                        }

                                        if(isThumbnail){
                                            rightClickOptions.put(
                                                l10n("gui.view_thumbnail"),
                                                () -> entry.play(ThumbnailContainerEnum.class));
                                        }

                                        log.info("Copied file: {}", path.getFileName());
                                    }catch(IOException e){
                                        log.error("Failed to copy file: {} {}", path.getFileName(), e.getLocalizedMessage());
                                    }
                                }
                            });
                        }catch(IOException e){
                            log.error("Failed to list files {}", e.getLocalizedMessage());
                        }

                        mediaCard.getRightClickMenu().putAll(rightClickOptions);

                        GDownloader.deleteRecursively(tmpPath.toPath());

                        entry.updateStatus(DownloadStatus.COMPLETE, l10n("gui.download_status.finished"));

                        mediaCard.getRightClickMenu().put(
                            l10n("gui.restart_download"),
                            () -> {
                                entry.updateStatus(DownloadStatus.QUEUED, l10n("gui.download_status.not_started"));
                                entry.reset();

                                completedDownloads.remove(entry);
                                downloadDeque.offerLast(entry);
                                fireListeners();
                            });

                        mediaCard.getRightClickMenu().put(
                            l10n("gui.delete_files"),
                            () -> entry.deleteMediaFiles());

                        completedDownloads.offer(entry);
                        fireListeners();
                    }else{
                        log.error("Unexpected download state");
                    }
                }catch(Exception e){
                    entry.updateStatus(DownloadStatus.FAILED, e.getLocalizedMessage());
                    entry.reset();

                    downloadDeque.offerLast(entry);//Retry later
                    fireListeners();

                    main.handleException(e);
                }finally{
                    entry.getRunning().set(false);

                    runningDownloads.decrementAndGet();
                    fireListeners();
                }
            }, 10);
        }

        if(downloadsRunning.get() && runningDownloads.get() == 0){
            stopDownloads();
        }
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception{
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);
        finalArgs.add(entry.getUrl());

        log.info("Arguments {}", finalArgs);

        ProcessBuilder processBuilder = new ProcessBuilder(finalArgs);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        entry.setProcess(process);

        double lastPercentage = 0;
        boolean downloadStarted = false;
        String lastOutput = "";

        try(ReadableByteChannel stdInput = Channels.newChannel(process.getInputStream())){
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            StringBuilder output = new StringBuilder();
            char prevChar = '\0';

            while(downloadsRunning.get() && !entry.getCancelHook().get() && process.isAlive()){
                if(Thread.currentThread().isInterrupted()){
                    process.destroy();
                    throw new InterruptedException("Download interrupted");
                }

                buffer.clear();
                int bytesRead = stdInput.read(buffer);
                if(bytesRead > 0){
                    buffer.flip();

                    while(buffer.hasRemaining()){
                        char ch = (char)buffer.get();
                        output.append(ch);

                        if(ch == '\n' || (ch == '\r' && prevChar != '\n')){
                            lastOutput = output.toString();
                            output.setLength(0);
                        }

                        prevChar = ch;
                    }

                    if(lastOutput.contains("[download]") && !lastOutput.contains("Destination:")){
                        String[] parts = lastOutput.split("\\s+");
                        for(String part : parts){
                            if(part.endsWith("%")){
                                double percent = Double.parseDouble(part.replace("%", ""));
                                if(percent > lastPercentage || percent < 5
                                    || Math.abs(percent - lastPercentage) > 10){
                                    entry.getMediaCard().setPercentage(percent);
                                    lastPercentage = percent;
                                }
                            }
                        }

                        entry.updateStatus(DownloadStatus.DOWNLOADING, lastOutput.replace("[download] ", ""));
                        downloadStarted = true;
                    }else{
                        log.info("[{}] - {}", entry.getDownloadId(), lastOutput);

                        if(downloadStarted){
                            entry.updateStatus(DownloadStatus.PROCESSING, lastOutput);
                        }else{
                            entry.updateStatus(DownloadStatus.PREPARING, lastOutput);
                        }
                    }
                }

                Thread.sleep(100);
            }

            long stopped = System.currentTimeMillis() - start;

            if(!downloadsRunning.get() || entry.getCancelHook().get()){
                tryStopProcess(process);

                log.info("Download process halted after {}ms.", stopped);

                return null;
            }else{
                int exitCode = process.waitFor();
                log.info("Download process took {}ms", stopped);
                return new Pair<>(exitCode, lastOutput);
            }
        }finally{
            tryStopProcess(process);
        }
    }

    private void tryStopProcess(Process process) throws InterruptedException{
        if(process.isAlive()){
            long quitTimer = System.currentTimeMillis();

            //First try to politely ask the process to excuse itself.
            process.destroy();

            if(!process.waitFor(5, TimeUnit.SECONDS)){
                log.warn("Process did not terminate in time, forcefully stopping it.");
                //Time's up. I guess asking nicely wasn't in the cards.
                process.destroyForcibly();
            }

            log.info("Took {}ms to stop the process.",
                (System.currentTimeMillis() - quitTimer));
        }
    }

    private static String truncate(String input, int length){
        if(input.length() > length){
            input = input.substring(0, length - 3) + "...";
        }

        return input;
    }

    @Getter
    private enum DownloadStatus implements ISettingsEnum{
        QUERYING("enums.download_status.querying"),
        STOPPED("enums.download_status.stopped"),
        QUEUED("enums.download_status.queued"),
        STARTING("enums.download_status.starting"),
        PREPARING("enums.download_status.preparing"),
        PROCESSING("enums.download_status.processing"),
        DOWNLOADING("enums.download_status.downloading"),
        COMPLETE("enums.download_status.complete"),
        FAILED("enums.download_status.failed"),
        NO_METHOD("enums.download_status.no_method");

        private final String translationKey;

        private DownloadStatus(String translationKeyIn){
            translationKey = translationKeyIn;
        }
    }

    @Data
    private static class QueueEntry{

        private final GDownloader main;

        private final MediaCard mediaCard;
        private final AbstractUrlFilter filter;
        private final String originalUrl;
        private final String url;
        private final int downloadId;

        @Setter(AccessLevel.NONE)
        private DownloadStatus downloadStatus;
        private VideoInfo videoInfo;

        private File tmpDirectory;
        private List<File> finalMediaFiles = new ArrayList<>();

        private AtomicBoolean cancelHook = new AtomicBoolean(false);
        private AtomicBoolean running = new AtomicBoolean(false);

        private Process process;

        public void openUrl(){
            main.openUrlInBrowser(originalUrl);
        }

        public <T extends Enum<T> & IContainerEnum> void play(Class<T> container){
            if(!finalMediaFiles.isEmpty()){
                for(File file : finalMediaFiles){
                    if(!file.exists()){
                        continue;
                    }

                    String fileName = file.getAbsolutePath().toLowerCase();

                    String[] values = IContainerEnum.getContainerValues(container);

                    for(String value : values){
                        if(fileName.endsWith("." + value)){
                            main.open(file);
                            return;
                        }
                    }
                }
            }

            main.openDownloadsDirectory();
        }

        public void copyUrlToClipboard(){
            StringSelection stringSelection = new StringSelection(originalUrl);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);

            main.getGuiManager().showMessage(
                l10n("gui.copy_url.notification_title"),
                l10n("gui.copy_url.copied"),
                3500,
                GUIManager.MessageType.INFO,
                false
            );
        }

        public void deleteMediaFiles(){
            boolean success = false;

            for(File file : finalMediaFiles){
                try{
                    if(Files.deleteIfExists(file.toPath())){
                        success = true;
                    }
                }catch(IOException e){
                    main.handleException(e);
                }
            }

            main.getGuiManager().showMessage(
                l10n("gui.delete_files.notification_title"),
                success ? l10n("gui.delete_files.deleted") : l10n("gui.delete_files.no_files"),
                3500,
                GUIManager.MessageType.INFO,
                false
            );
        }

        public boolean isRunning(){
            return running.get();
        }

        public void clean(){
            if(tmpDirectory != null && tmpDirectory.exists()){
                GDownloader.deleteRecursively(tmpDirectory.toPath());
            }
        }

        public void close(){
            cancelHook.set(true);

            if(process != null){
                process.destroy();
            }

            clean();
        }

        public void reset(){
            cancelHook.set(false);
            process = null;
        }

        public void setVideoInfo(VideoInfo videoInfoIn){
            videoInfo = videoInfoIn;

            Optional<BufferedImage> optional = videoInfo.supportedThumbnails()
                .limit(3)
                .map(urlIn -> tryLoadThumbnail(urlIn))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            optional.ifPresentOrElse(
                img -> mediaCard.setThumbnailAndDuration(img, videoInfoIn.getDuration()),
                () -> log.error("Failed to load a valid thumbnail")
            );
        }

        private Optional<BufferedImage> tryLoadThumbnail(String url){
            try{
                BufferedImage img = ImageIO.read(new URI(url).toURL());

                if(img != null){
                    return Optional.of(img);
                }else{
                    log.error("ImageIO.read returned null for {}", url);
                }
            }catch(IOException | URISyntaxException e){
                log.error("ImageIO.read exception {} {}", e.getLocalizedMessage(), url);
            }

            return Optional.empty();
        }

        private String getTitle(){
            if(videoInfo != null && !videoInfo.getTitle().isEmpty()){
                return truncate(videoInfo.getTitle(), 40);
            }

            return truncate(url
                .replace("https://", "")
                .replace("www.", ""), 30);
        }

        public void updateStatus(DownloadStatus status, String text){
            mediaCard.setLabel(filter.getDisplayName(), getTitle(),
                status != DownloadStatus.DOWNLOADING ? truncate(text, 40) : truncate(text, 51));

            mediaCard.setTooltip(text);

            if(status != downloadStatus){
                downloadStatus = status;

                switch(status){
                    case QUERYING:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(Color.MAGENTA);
                        break;
                    case PROCESSING:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(Color.ORANGE);
                        break;
                    case PREPARING:
                    case QUEUED:
                    case STOPPED:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(Color.GRAY);
                        break;
                    case DOWNLOADING:
                        mediaCard.setPercentage(0);
                        mediaCard.setString(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%");
                        mediaCard.setColor(new Color(255, 214, 0));
                        break;
                    case STARTING:
                        mediaCard.setPercentage(0);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(new Color(255, 214, 0));
                        break;
                    case COMPLETE:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setTextColor(Color.WHITE);
                        mediaCard.setColor(new Color(0, 200, 83));
                        break;
                    case NO_METHOD:
                    case FAILED:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(Color.RED);
                        break;
                    default:
                        throw new RuntimeException("Unhandled status: " + status);
                }
            }else if(status == DownloadStatus.DOWNLOADING){
                mediaCard.setString(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%");
            }
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Thumbnail{

        @JsonProperty("url")
        private String url;

        @JsonProperty("preference")
        private int preference;

        @JsonProperty("id")
        private String id;

        @JsonProperty("height")
        private int height;

        @JsonProperty("width")
        private int width;

        @JsonProperty("resolution")
        private String resolution;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoInfo{

        @JsonProperty("id")
        private String id;

        @JsonProperty("title")
        private String title = "";

        @JsonProperty("thumbnail")
        private String thumbnail = "";

        @JsonProperty("thumbnails")
        private List<Thumbnail> thumbnails = new ArrayList<>();

        @JsonIgnore
        private Stream<String> supportedThumbnails(){
            Stream.Builder<String> builder = Stream.builder();

            if(thumbnail != null && thumbnail.startsWith("http")){
                builder.add(thumbnail);
            }

            thumbnails.stream()
                .filter(thumb -> thumb.getUrl() != null)
                .sorted(Comparator.comparingInt(Thumbnail::getPreference).reversed())
                .map(Thumbnail::getUrl)
                .forEach(builder::add);

            return builder.build();
        }

        @JsonProperty("description")
        private String description;

        @JsonProperty("channel_id")
        private String channelId;

        @JsonProperty("channel_url")
        private String channelUrl;

        @JsonProperty("duration")
        private long duration;

        @JsonProperty("view_count")
        private int viewCount;

        @JsonProperty("upload_date")
        private String uploadDate;

        @JsonIgnore
        @Nullable
        public LocalDate getUploadDateAsLocalDate(){
            if(uploadDate != null && !uploadDate.isEmpty()){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                return LocalDate.parse(uploadDate, formatter);
            }

            return null;
        }

        @JsonProperty("timestamp")
        private long timestamp;

        @JsonProperty("width")
        private int width;

        @JsonProperty("height")
        private int height;

        @JsonProperty("resolution")
        private String resolution;

        @JsonProperty("fps")
        private int fps;

    }

}
