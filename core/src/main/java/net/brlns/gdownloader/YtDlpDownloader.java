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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
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
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.GUIManager.DialogButton;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.Language.*;
import static net.brlns.gdownloader.util.URLUtils.*;

//TODO media converter
//TODO implement CD Ripper
//TODO d&d files for conversion to different formats, we already have ffmpeg anyway
//
//TODO max simultaneous downloads should be independent per website
//TODO silence debug messages
//TODO investigate adding AppImage build
//TODO we should only grab clipboard AFTER the button is clicked
//TODO add custom ytdlp filename modifiers to the settings
//TODO scale on resolution DPI
//TODO save last window size in config
//TODO keep older versions of ytdlp and retry failed downloads against them
//TODO extra ytdlp args @TODO bad arguments could break downloads, leave as is? users can edit the config.json directly to add things like proxy arguments
//TODO rework WebP support for modular system @TODO test again, the workaround for the gradle bug might have broken it again @TODO it in fact did @TODO cannot fix
//TODO verify checksums during updates, add bouncycastle, check signatures
//TODO write a component factory for GUIManager
//TODO git actions build for different platforms
//FEEDBACK Icons too small
//FEEDBACK Should choose to download video and audio independently on each card
//TODO maybe add notifications for each toggled toolbar option
//TODO check updates on a timer, but do not ever restart when anything is in the queue.
//TODO individual 'retry failed download' button
//TODO --no-playlist when single video option is active
//TODO Artifacting seems to be happening on the scroll pane with AMD video cards
/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpDownloader{

    private final GDownloader main;

    private final ExecutorService downloadScheduler;

    private final List<Consumer<YtDlpDownloader>> listeners = new ArrayList<>();

    private final Set<String> capturedLinks = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> capturedPlaylists = Collections.synchronizedSet(new HashSet<>());

    private final Deque<QueueEntry> downloadDeque = new LinkedBlockingDeque<>();
    private final Queue<QueueEntry> completedDownloads = new ConcurrentLinkedQueue<>();
    private final Queue<QueueEntry> failedDownloads = new ConcurrentLinkedQueue<>();

    private final AtomicInteger runningDownloads = new AtomicInteger();
    private final AtomicInteger downloadCounter = new AtomicInteger();

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);
    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);

    public YtDlpDownloader(GDownloader mainIn){
        main = mainIn;

        //I know what era of computer this will be running on, so more than 10 threads would be insanity
        //But maybe add it as a setting later
        downloadScheduler = Executors.newFixedThreadPool(10);
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

        if(downloadsBlocked.get() || inputUrl == null || isGarbageUrl(inputUrl) || capturedLinks.contains(inputUrl)
            || WebFilterEnum.isYoutubeChannel(inputUrl) && !main.getConfig().isDownloadYoutubeChannels()){
            future.complete(false);
            return future;
        }

        for(WebFilterEnum webFilter : WebFilterEnum.values()){
            if(webFilter == WebFilterEnum.DEFAULT && (main.getConfig().isCaptureAnyLinks() || force) || webFilter.getPattern().apply(inputUrl)){
                if(capturedLinks.contains(inputUrl)){
                    continue;
                }

                String filteredUrl;

                switch(webFilter){
                    case YOUTUBE -> {
                        filteredUrl = filterVideo(inputUrl);
                    }

                    case YOUTUBE_PLAYLIST -> {
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

                                    //The dialog is synchronized
                                    main.getGuiManager().showConfirmDialog(
                                        get("dialog.confirm"),
                                        get("dialog.download_playlist") + "\n\n" + playlist,
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
                    }

                    default -> {
                        filteredUrl = inputUrl;
                    }
                }

                if(filteredUrl == null){
                    if(main.getConfig().isDebugMode()){
                        main.handleException(new Throwable("Filtered url was null"));
                    }

                    future.complete(false);
                    return future;
                }

                if(capturedLinks.add(filteredUrl)){
                    capturedLinks.add(inputUrl);

                    log.info("Captured {}", inputUrl);

                    MediaCard mediaCard = main.getGuiManager().addMediaCard(main.getConfig().isDownloadVideo(), "");

                    int downloadId = downloadCounter.incrementAndGet();

                    QueueEntry queueEntry = new QueueEntry(main, mediaCard, webFilter, inputUrl, filteredUrl, downloadId);
                    queueEntry.updateStatus(DownloadStatus.QUERYING, get("gui.download_status.querying"));

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
                        get("gui.open_downloads_directory"),
                        () -> main.openDownloadsDirectory(),
                        get("gui.open_in_browser"),
                        () -> queueEntry.openUrl(),
                        get("gui.copy_url"),
                        () -> queueEntry.copyUrlToClipboard()
                    ));

                    queryVideo(queueEntry);

                    downloadDeque.offerLast(queueEntry);
                    fireListeners();

                    future.complete(true);
                    return future;
                }
            }
        }

        future.complete(false);
        return future;
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
        QueueEntry next;
        while((next = failedDownloads.poll()) != null){
            next.updateStatus(DownloadStatus.QUEUED, get("gui.download_status.not_started"));
            next.reset();

            downloadDeque.offerLast(next);
        }

        startDownloads();

        fireListeners();
    }

    public void clearQueue(){
        capturedLinks.clear();
        capturedPlaylists.clear();

        //We deliberately keep the download processes running
        QueueEntry next;
        while((next = downloadDeque.peek()) != null){
            main.getGuiManager().removeMediaCard(next.getMediaCard().getId());
            downloadDeque.remove(next);

            if(!next.isRunning()){
                next.clean();
            }
        }

        while((next = failedDownloads.poll()) != null){
            main.getGuiManager().removeMediaCard(next.getMediaCard().getId());

            if(!next.isRunning()){
                next.clean();
            }
        }

        while((next = completedDownloads.poll()) != null){
            main.getGuiManager().removeMediaCard(next.getMediaCard().getId());
        }

        fireListeners();
    }

    private void queryVideo(QueueEntry queueEntry){
        if(queueEntry.getWebFilter() == WebFilterEnum.DEFAULT){
            queueEntry.updateStatus(DownloadStatus.QUEUED, get("gui.download_status.not_started"));
            return;
        }

        downloadScheduler.execute(() -> {
            try{
                if(queueEntry.getCancelHook().get()){
                    return;
                }

                List<String> list = GDownloader.readOutput(
                    main.getYtDlpUpdater().getExecutablePath().toString(),
                    "--dump-json",
                    "--flat-playlist",
                    queueEntry.getUrl()
                );

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
                    queueEntry.updateStatus(DownloadStatus.QUEUED, get("gui.download_status.not_started"));
                }
            }
        });
    }

    @SuppressWarnings("fallthrough")
    public void processQueue(){
        while(downloadsRunning.get() && !downloadDeque.isEmpty()){
            if(runningDownloads.get() >= main.getConfig().getMaxSimultaneousDownloads()){
                break;
            }

            QueueEntry next = downloadDeque.peek();

            downloadDeque.remove(next);

            MediaCard mediaCard = next.getMediaCard();
            if(mediaCard.isClosed()){
                break;
            }

            runningDownloads.incrementAndGet();
            fireListeners();

            downloadScheduler.execute(() -> {
                if(!downloadsRunning.get()){
                    downloadDeque.offerFirst(next);

                    runningDownloads.decrementAndGet();
                    fireListeners();
                    return;
                }

                try{
                    boolean downloadAudio = main.getConfig().isDownloadAudio();
                    boolean downloadVideo = main.getConfig().isDownloadVideo();

                    if(!downloadAudio && !downloadVideo){
                        next.updateStatus(DownloadStatus.NO_METHOD, get("enums.download_status.no_method.video_tip"));
                        next.reset();

                        failedDownloads.offer(next);

                        log.error("{} - No option to download.", next.getWebFilter());

                        if(downloadDeque.size() <= 1){
                            stopDownloads();
                        }

                        return;
                    }

                    QualitySettings needle = main.getConfig().getQualitySettings().get(next.getWebFilter());

                    QualitySettings quality;
                    if(needle != null){
                        quality = needle;
                    }else{
                        quality = main.getConfig().getDefaultQualitySettings();
                    }

                    AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

                    if(!downloadVideo && downloadAudio && audioBitrate == AudioBitrateEnum.NO_AUDIO){
                        next.updateStatus(DownloadStatus.NO_METHOD, get("enums.download_status.no_method.audio_tip"));
                        next.reset();

                        failedDownloads.offer(next);

                        log.error("{} - No audio quality selected, but was set to download audio only.", next.getWebFilter());

                        if(downloadDeque.size() <= 1){
                            stopDownloads();
                        }

                        return;
                    }

                    next.getRunning().set(true);
                    next.updateStatus(DownloadStatus.STARTING, get("gui.download_status.starting"));

                    File finalPath = main.getOrCreateDownloadsDirectory();

                    File tmpPath = GDownloader.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(next.getDownloadId()));
                    next.setTmpDirectory(tmpPath);

                    List<String> genericArgs = new ArrayList<>();
                    List<String> audioArgs = new ArrayList<>();
                    List<String> videoArgs = new ArrayList<>();

                    genericArgs.addAll(Arrays.asList(
                        main.getYtDlpUpdater().getExecutablePath().toString(),
                        "-i"
                    ));

                    if(main.getFfmpegUpdater().getExecutablePath() != null){
                        genericArgs.addAll(Arrays.asList(
                            "--ffmpeg-location",
                            main.getFfmpegUpdater().getExecutablePath().toString()
                        ));
                    }

                    log.info("Browser is {}", main.getBrowserForCookies());

                    switch(next.getWebFilter()){
                        case YOUTUBE_PLAYLIST:
                            genericArgs.addAll(Arrays.asList(
                                "--yes-playlist"
                            ));

                        //Intentional fall-through
                        case YOUTUBE:
                            if(main.getConfig().isUseSponsorBlock()){
                                genericArgs.addAll(Arrays.asList(
                                    "--sponsorblock-mark",
                                    "sponsor,intro,outro,selfpromo,interaction,music_offtopic"
                                ));
                            }

                            audioArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath()
                                + (next.getWebFilter() == WebFilterEnum.YOUTUBE_PLAYLIST ? "/%(playlist)s/" : "/")
                                + "%(title)s (" + audioBitrate.getValue() + "kbps).%(ext)s",
                                "--embed-thumbnail",
                                "--embed-metadata"
                            ));

                            videoArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath()
                                + (next.getWebFilter() == WebFilterEnum.YOUTUBE_PLAYLIST ? "/%(playlist)s/" : "/")
                                + "%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s",
                                "--embed-thumbnail",
                                "--embed-metadata",
                                "--embed-subs",
                                "--sub-langs",
                                "all,-live_chat",
                                "--parse-metadata",
                                "description:(?s)(?P<meta_comment>.+)",
                                "--embed-chapters"
                            ));

                            if(next.getUrl().contains("liked") || next.getUrl().contains("list=LL") || next.getUrl().contains("list=WL")){
                                if(main.getConfig().isReadCookies()){
                                    genericArgs.addAll(Arrays.asList(
                                        "--cookies-from-browser",
                                        main.getBrowserForCookies().getName()
                                    ));
                                }
                            }

                            break;
                        case TWITCH:
                            genericArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s",
                                "--verbose",
                                "--continue",
                                "--hls-prefer-native"
                            ));

                            videoArgs.addAll(Arrays.asList(
                                "--parse-metadata",
                                ":%(?P<is_live>)"
                            ));

                            break;
                        case TWITTER:
                            genericArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s"
                            ));

                            if(main.getConfig().isReadCookies()){
                                genericArgs.addAll(Arrays.asList(
                                    "--cookies-from-browser",
                                    main.getBrowserForCookies().getName()
                                ));
                            }

                            break;
                        case FACEBOOK:
                            genericArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(upload_date)s %(resolution)s).%(ext)s",
                                "--max-sleep-interval",
                                "30",
                                "--min-sleep-interval",
                                "15"
                            ));

                            if(main.getConfig().isReadCookies()){
                                genericArgs.addAll(Arrays.asList(
                                    "--cookies-from-browser",
                                    main.getBrowserForCookies().getName()
                                ));
                            }

                            break;
                        case CRUNCHYROLL:
                        case DROPOUT:
                            if(!main.getConfig().isReadCookies()){
                                log.warn("Cookies required for this website {}", next.getOriginalUrl());
                            }

                        //fall-through
                        default:
                            genericArgs.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(resolution)s).%(ext)s"
                            ));

                            if(main.getConfig().isReadCookies()){
                                genericArgs.addAll(Arrays.asList(
                                    "--cookies-from-browser",
                                    main.getBrowserForCookies().getName()
                                ));
                            }

                            break;
                    }

                    for(String arg : main.getConfig().getExtraYtDlpArguments().split(" ")){
                        if(!arg.isEmpty()){
                            genericArgs.add(arg);
                        }
                    }

                    boolean success = false;

                    if(downloadVideo){
                        videoArgs.addAll(Arrays.asList(
                            "-f",
                            quality.getQualitySettings(),
                            "--postprocessor-args",
                            //Opus is not supported by some native video players
                            "ffmpeg:-c:a aac -b:a " + audioBitrate.getValue() + "k",
                            "--merge-output-format",
                            quality.getVideoContainer().getValue()
                        ));

                        Pair<Integer, String> result = processDownload(next, genericArgs, videoArgs);

                        if(result.getKey() != 0){
                            if(!next.getCancelHook().get()){
                                next.updateStatus(DownloadStatus.FAILED, result.getValue());
                                next.reset();

                                failedDownloads.offer(next);
                            }

                            fireListeners();
                            return;
                        }

                        success = true;
                    }

                    //isDownloadAudio() might have changed at this point, do a fresh check just to be sure
                    if(main.getConfig().isDownloadAudio() && audioBitrate != AudioBitrateEnum.NO_AUDIO){
                        audioArgs.addAll(Arrays.asList(
                            "-f",
                            "bestaudio",
                            "--extract-audio",
                            "--audio-format",
                            quality.getAudioContainer().getValue(),
                            "--audio-quality",
                            audioBitrate.getValue() + "k"
                        ));

                        Pair<Integer, String> result = processDownload(next, genericArgs, audioArgs);

                        if(result.getKey() != 0){
                            if(!next.getCancelHook().get()){
                                next.updateStatus(DownloadStatus.FAILED, result.getValue());
                                next.reset();

                                failedDownloads.offer(next);
                            }

                            fireListeners();
                            return;
                        }

                        success = true;
                    }

                    if(!downloadsRunning.get()){
                        next.updateStatus(DownloadStatus.STOPPED, get("gui.download_status.not_started"));
                        next.reset();

                        downloadDeque.offerFirst(next);
                        fireListeners();
                    }else if(!next.getCancelHook().get() && success){
                        try(Stream<Path> dirStream = Files.walk(tmpPath.toPath())){
                            dirStream.forEach(path -> {
                                String fileName = path.getFileName().toString().toLowerCase();

                                boolean isAudio = fileName.endsWith(")." + quality.getAudioContainer().getValue());
                                boolean isVideo = fileName.endsWith(")." + quality.getVideoContainer().getValue());

                                if(isAudio || isVideo){
                                    Path relativePath = tmpPath.toPath().relativize(path);
                                    Path targetPath = finalPath.toPath().resolve(relativePath);

                                    try{
                                        Files.createDirectories(targetPath.getParent());
                                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                        next.getFinalMediaFiles().add(targetPath.toFile());

                                        if(isVideo){
                                            mediaCard.getRightClickMenu().put(
                                                get("gui.play_video"),
                                                () -> next.play(true));
                                        }

                                        if(isAudio){
                                            mediaCard.getRightClickMenu().put(
                                                get("gui.play_audio"),
                                                () -> next.play(false));
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

                        GDownloader.deleteRecursively(tmpPath.toPath());

                        next.updateStatus(DownloadStatus.COMPLETE, get("gui.download_status.finished"));

                        mediaCard.getRightClickMenu().put(
                            get("gui.restart_download"),
                            () -> {
                                next.updateStatus(DownloadStatus.QUEUED, get("gui.download_status.not_started"));
                                next.reset();

                                completedDownloads.remove(next);
                                downloadDeque.offerLast(next);
                                fireListeners();
                            });

                        mediaCard.getRightClickMenu().put(
                            get("gui.delete_files"),
                            () -> next.deleteMediaFiles());

                        completedDownloads.offer(next);

                        fireListeners();
                    }else{
                        log.error("Unexpected download state");
                    }
                }catch(Exception e){
                    next.updateStatus(DownloadStatus.FAILED, e.getLocalizedMessage());
                    next.reset();

                    downloadDeque.offerLast(next);//Retry later
                    fireListeners();

                    main.handleException(e);
                }finally{
                    next.getRunning().set(false);

                    runningDownloads.decrementAndGet();
                    fireListeners();
                }
            });
        }

        if(downloadsRunning.get() && runningDownloads.get() == 0){
            stopDownloads();
        }
    }

    private Pair<Integer, String> processDownload(QueueEntry next,
        List<String> genericArgs, List<String> specificArgs) throws Exception{
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(genericArgs);
        finalArgs.addAll(specificArgs);
        finalArgs.add(next.getUrl());

        log.info("exec {}", finalArgs);

        Process process = Runtime.getRuntime().exec(finalArgs.stream().toArray(String[]::new));

        next.setProcess(process);

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        double lastPercentage = 0;
        String s;

        boolean downloadStarted = false;

        while(downloadsRunning.get() && !next.getCancelHook().get() && (s = stdInput.readLine()) != null){
            log.info("[{}] - {}", next.getDownloadId(), s);

            if(s.contains("[download]") && !s.contains("Destination:")){
                String[] parts = s.split("\\s+");
                for(String part : parts){
                    if(part.endsWith("%")){
                        double percent = Double.parseDouble(part.replace("%", ""));

                        if(percent > lastPercentage || percent < 5 || Math.abs(percent - lastPercentage) > 10){
                            next.getMediaCard().setPercentage(percent);
                            lastPercentage = percent;
                        }
                    }
                }

                next.updateStatus(DownloadStatus.DOWNLOADING, s.replace("[download] ", ""));

                downloadStarted = true;
            }else if(downloadStarted){
                next.updateStatus(DownloadStatus.PROCESSING, s);
            }else{
                next.updateStatus(DownloadStatus.PREPARING, s);
            }
        }

        String lastError = "- -";

        while(downloadsRunning.get() && !next.getCancelHook().get() && (s = stdError.readLine()) != null){
            log.error("[{}] - {}", next.getDownloadId(), s);

            lastError = s;
        }

        int exitCode = process.waitFor();

        log.info("Download took {}ms", (System.currentTimeMillis() - start));

        return new Pair<>(exitCode, lastError);
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
        private final WebFilterEnum webFilter;
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

        public void play(boolean video){
            if(!finalMediaFiles.isEmpty()){
                for(File file : finalMediaFiles){
                    if(!file.exists()){
                        continue;
                    }

                    String fileName = file.getAbsolutePath().toLowerCase();

                    if(video){
                        for(VideoContainerEnum container : VideoContainerEnum.values()){
                            if(fileName.endsWith(")." + container.getValue())){
                                main.open(file);
                                return;
                            }
                        }
                    }else{
                        for(AudioContainerEnum container : AudioContainerEnum.values()){
                            if(fileName.endsWith(")." + container.getValue())){
                                main.open(file);
                                return;
                            }
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
                get("gui.copy_url.notification_title"),
                get("gui.copy_url.copied"),
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
                get("gui.delete_files.notification_title"),
                success ? get("gui.delete_files.deleted") : get("gui.delete_files.no_files"),
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
            mediaCard.setLabel(webFilter.getDisplayName(), getTitle(),
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

            if(thumbnail != null && thumbnail.startsWith("http") && !thumbnail.endsWith(".webp")){
                builder.add(thumbnail);
            }

            thumbnails.stream()
                .filter(thumb -> thumb.getUrl() != null
                && (thumb.getUrl().endsWith(".jpg") || thumb.getUrl().endsWith(".png")))
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
