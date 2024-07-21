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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.ui.GUIManager.DialogButton;
import net.brlns.gdownloader.ui.MediaCard;
import net.brlns.gdownloader.util.Nullable;

import static net.brlns.gdownloader.Language.*;

//TODO first clipboard copy tends to fail
//TODO max simultaneous downloads should be independent per website
//TODO we should only grab clipboard AFTER the button is clicked
//TODO expand thumbails a little when window is resized
//TODO implement CD Ripper
//TODO winamp icon for mp3's in disc
//TODO add button to convert individually
//TODO output filename settings
//TODO delete cache folder when its location changes
//TODO shutdown and startup hook to delete cache folder
//TODO add custom ytdlp filename modifiers to the settings
//TODO TEST - empty queue should delete directories too
//TODO check if empty spaces in filenames are ok
//TODO restart program on language change
//TODO dark and white themes
//TODO scale on resolution DPI
//TODO should we move the window back up when a new card is added?
//TODO save last window size in config
//TODO setting to download channels as playlists
//TODO keep older versions of ytdlp and retry failed downloads against them
//TODO open url in default browser for linux, don't try to guess it
//TODO d&d files for conversion
//TODO extra ytdlp args
//TODO updater
//TODO splash screen
//TODO ffmpeg updater
//TODO media converter
//TODO right click program to exit
//TODO rework WebP support for modular system
//TODO prefer to NOT create the download folder until a download is happening
//TODO output directories for media converter
//TODO do not quit when yt-dlp fails to download
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

    private final AtomicBoolean downloadsBlocked = new AtomicBoolean(true);

    private final AtomicInteger runningDownloads = new AtomicInteger();

    private final AtomicInteger downloadCounter = new AtomicInteger();

    private final AtomicBoolean downloadsRunning = new AtomicBoolean(false);

    public YtDlpDownloader(GDownloader mainIn){
        main = mainIn;

        //I know what era of computer this will be running on, so more than 10 threads would be insanity
        //But maybe add it as a setting later
        downloadScheduler = new ThreadPoolExecutor(0, 10, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
    }

    public boolean isBlocked(){
        return downloadsBlocked.get();
    }

    public void unblock(){
        downloadsBlocked.set(false);

        fireListeners();
    }

    public void registerListener(Consumer<YtDlpDownloader> consumer){
        listeners.add(consumer);
    }

    public CompletableFuture<Boolean> captureUrl(String inputUrl){
        return captureUrl(inputUrl, main.getConfig().getPlaylistDownloadOption());
    }

    public CompletableFuture<Boolean> captureUrl(@Nullable String inputUrl, PlayListOptionEnum playlistOption){
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if(downloadsBlocked.get() || inputUrl == null || inputUrl.contains("ytimg") || WebFilterEnum.isYoutubeChannel(inputUrl)){//Nope, maybe as a setting later
            future.complete(false);
            return future;
        }

        for(WebFilterEnum webFilter : WebFilterEnum.values()){
            if(webFilter == WebFilterEnum.DEFAULT && main.getConfig().isCaptureAnyLinks() || webFilter.getPattern().apply(inputUrl)){
                if(!capturedLinks.contains(inputUrl)){
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

                                    return captureUrl(filterVideo(inputUrl));
                                }

                                case ALWAYS_ASK:
                                default: {
                                    String playlist = filterPlaylist(inputUrl);

                                    if(playlist != null){
                                        if(!capturedPlaylists.contains(playlist)){
                                            DialogButton playlistDialogOption = new DialogButton(PlayListOptionEnum.DOWNLOAD_PLAYLIST.getDisplayName(), (boolean setDefault) -> {
                                                if(setDefault){
                                                    main.getConfig().setPlaylistDownloadOption(PlayListOptionEnum.DOWNLOAD_PLAYLIST);
                                                    main.updateConfig();
                                                }

                                                captureUrl(playlist, PlayListOptionEnum.DOWNLOAD_PLAYLIST)
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

                                                captureUrl(inputUrl, PlayListOptionEnum.DOWNLOAD_SINGLE)
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
                                            return captureUrl(filterVideo(inputUrl));
                                        }
                                    }

                                    future.complete(false);
                                    return future;
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

                        MediaCard mediaCard = main.getGuiManager().addMediaCard(!main.getConfig().isDownloadAudioOnly(), "");

                        int downloadId = downloadCounter.incrementAndGet();

                        QueueEntry queueEntry = new QueueEntry(mediaCard, webFilter, inputUrl, filteredUrl, downloadId);
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

                        mediaCard.setOnClick(() -> {
                            queueEntry.launch(main);
                        });

                        queryVideo(queueEntry);

                        downloadDeque.offerLast(queueEntry);
                        fireListeners();

                        future.complete(true);
                        return future;
                    }
                }
            }
        }

        future.complete(false);
        return future;
    }

    public boolean isRunning(){
        return downloadsRunning.get();
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

                List<String> list = readOutput(
                    main.getYtDlpUpdater().getYtDlpExecutablePath().toString(),
                    "--dump-json",
                    "--flat-playlist",
                    queueEntry.getUrl()
                );

                if(!list.isEmpty()){
                    VideoInfo info = GDownloader.OBJECT_MAPPER.readValue(list.get(0), VideoInfo.class);

                    queueEntry.setVideoInfo(info);
                }
            }catch(Exception e){
                log.error("Failed to parse json {}", e.getLocalizedMessage());
            }finally{
                queueEntry.updateStatus(DownloadStatus.QUEUED, get("gui.download_status.not_started"));
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

            if(next.getDownloadStatus() == DownloadStatus.QUERYING){
                break;
            }

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
                    next.getRunning().set(true);
                    next.updateStatus(DownloadStatus.STARTING, get("gui.download_status.starting"));

                    File finalPath = main.getOrCreateDownloadsDirectory();

                    File tmpPath = GDownloader.getOrCreate(finalPath, "cache", String.valueOf(next.getDownloadId()));
                    next.setTmpDirectory(tmpPath);

                    List<String> args = new ArrayList<>();

                    args.addAll(Arrays.asList(
                        main.getYtDlpUpdater().getYtDlpExecutablePath().toString(),
                        "-i"
                    ));

                    if(main.getYtDlpUpdater().getFfmpegExecutablePath() != null){
                        args.addAll(Arrays.asList(
                            "--ffmpeg-location",
                            main.getYtDlpUpdater().getFfmpegExecutablePath().toString()
                        ));
                    }

                    QualitySettings check = main.getConfig().getQualitySettings().get(next.getWebFilter());

                    QualitySettings quality;
                    if(check != null){
                        quality = check;
                    }else{
                        quality = main.getConfig().getDefaultQualitySettings();
                    }

                    AudioBitrateEnum audioBitrate = quality.getAudioBitrate();
                    if(main.getConfig().isDownloadAudioOnly()){
                        if(audioBitrate != AudioBitrateEnum.NO_AUDIO){
                            args.addAll(Arrays.asList(
                                "-f",
                                "bestaudio"
                            ));
                        }else{
                            next.updateStatus(DownloadStatus.NO_METHOD, get("enums.download_status.no_method.audio_tip"));
                            next.reset();

                            failedDownloads.offer(next);

                            log.error("{} - No audio quality selected, but was set to download audio only.", next.getWebFilter());

                            if(downloadDeque.size() <= 1){
                                stopDownloads();
                            }

                            return;
                        }
                    }else{
                        args.addAll(Arrays.asList(
                            "-f",
                            quality.getQualitySettings()
                        ));
                    }

                    if(!main.getConfig().isDownloadAudioOnly()){
                        args.addAll(Arrays.asList(
                            "--merge-output-format",
                            quality.getContainer().getValue(),
                            "--keep-video"//This is a hack, we should run two separate commands instead
                        ));
                    }

                    if(audioBitrate != AudioBitrateEnum.NO_AUDIO){
                        args.addAll(Arrays.asList(
                            "--extract-audio",
                            "--audio-format",
                            "mp3",
                            "--audio-quality",
                            audioBitrate.getValue() + "k"
                        ));
                    }

                    switch(next.getWebFilter()){
                        case YOUTUBE_PLAYLIST:
                            args.addAll(Arrays.asList(
                                "--yes-playlist"
                            ));

                        //Intentional fall-through
                        case YOUTUBE:
                            if(main.getConfig().isDownloadAudioOnly()){
                                args.addAll(Arrays.asList(
                                    "-o",
                                    tmpPath.getAbsolutePath() + "/%(title)s (" + audioBitrate.getValue() + "kbps).%(ext)s",
                                    "--embed-thumbnail",
                                    "--embed-metadata",
                                    "--sponsorblock-mark",
                                    "sponsor,intro,outro,selfpromo,interaction,music_offtopic"
                                ));
                            }else{
                                args.addAll(Arrays.asList(
                                    "-o",
                                    tmpPath.getAbsolutePath() + "/%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s",
                                    "--embed-thumbnail",
                                    "--embed-metadata",
                                    "--embed-subs",
                                    "--sub-langs",
                                    "all,-live_chat",
                                    "--parse-metadata",
                                    "description:(?s)(?P<meta_comment>.+)",
                                    "--embed-chapters",
                                    "--sponsorblock-mark",
                                    "sponsor,intro,outro,selfpromo,interaction,music_offtopic"
                                ));
                            }

                            if(next.getUrl().contains("liked") || next.getUrl().contains("list=LL") || next.getUrl().contains("list=WL")){
                                if(main.getConfig().isReadCookies()){
                                    args.addAll(Arrays.asList(
                                        "--cookies-from-browser",
                                        getBrowserForCookies().getName()
                                    ));
                                }
                            }

                            break;
                        case TWITCH:
                            args.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s",
                                "--verbose",
                                "--continue",
                                "--hls-prefer-native"
                            ));

                            if(!main.getConfig().isDownloadAudioOnly()){
                                args.addAll(Arrays.asList(
                                    "--parse-metadata",
                                    ":%(?P<is_live>)"
                                ));
                            }

                            break;
                        case TWITTER:
                            args.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s"
                            ));

                            if(main.getConfig().isReadCookies()){
                                args.addAll(Arrays.asList(
                                    "--cookies-from-browser",
                                    getBrowserForCookies().getName()
                                ));
                            }

                            break;
                        case FACEBOOK:
                            args.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(upload_date)s %(resolution)s).%(ext)s",
                                "--max-sleep-interval",
                                "30",
                                "--min-sleep-interval",
                                "15"
                            ));

                            break;
                        case CRUNCHYROLL:
                        case DROPOUT:
                            if(!main.getConfig().isReadCookies()){
                                log.warn("Cookies Required for this website " + next.getOriginalUrl());
                            }

                        //fall
                        default:
                            args.addAll(Arrays.asList(
                                "-o",
                                tmpPath.getAbsolutePath() + "/%(title)s (%(resolution)s).%(ext)s"
                            ));

                            if(main.getConfig().isReadCookies()){
                                args.addAll(Arrays.asList(
                                    "--cookies-from-browser",
                                    getBrowserForCookies().getName()
                                ));
                            }

                            break;
                    }

                    args.addAll(Arrays.asList(main.getConfig()
                        .getExtraYtDlpArguments().split(" ")));

                    args.add(next.getUrl());

                    log.info("exec {}", args);

                    Process process = Runtime.getRuntime().exec(args.stream().toArray(String[]::new));

                    next.setProcess(process);

                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                    double lastPercentage = 0;
                    String s;

                    while(downloadsRunning.get() && !next.getCancelHook().get() && (s = stdInput.readLine()) != null){
                        log.info("[{}] - {}", next.getDownloadId(), s);

                        if(s.contains("[download]")){
                            String[] parts = s.split("\\s+");
                            for(String part : parts){
                                if(part.endsWith("%")){
                                    //TODO
                                    double percent = Double.parseDouble(part.replace("%", ""));

                                    if(percent > lastPercentage || percent < 5){
                                        mediaCard.setPercentage(percent);
                                        lastPercentage = percent;
                                    }
                                }
                            }

                            next.updateStatus(DownloadStatus.DOWNLOADING, s.replace("[download] ", ""));
                        }else{
                            next.updateStatus(DownloadStatus.PROCESSING, s);
                        }
                    }

                    String lastError = "- -";

                    while(downloadsRunning.get() && !next.getCancelHook().get() && (s = stdError.readLine()) != null){
                        log.error("[{}] - {}", next.getDownloadId(), s);

                        lastError = s;
                    }

                    if(!downloadsRunning.get()){
                        next.updateStatus(DownloadStatus.STOPPED, get("gui.download_status.not_started"));
                        next.reset();

                        downloadDeque.offerFirst(next);
                        fireListeners();
                    }else if(!next.getCancelHook().get()){
                        int exitCode = process.waitFor();

                        if(exitCode != 0){
                            next.updateStatus(DownloadStatus.FAILED, lastError);
                            next.reset();

                            failedDownloads.offer(next);
                        }else{
                            next.updateStatus(DownloadStatus.COMPLETE, get("gui.download_status.finished"));

                            try(Stream<Path> dirStream = Files.walk(tmpPath.toPath())){
                                dirStream.forEach(path -> {
                                    String fileName = path.getFileName().toString().toLowerCase();
                                    if(fileName.endsWith(").mp3") || fileName.endsWith(")." + quality.getContainer().getValue())){
                                        Path targetPath = finalPath.toPath().resolve(path.getFileName());

                                        try{
                                            Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                            next.getFinalMediaFiles().add(targetPath.toFile());
                                            log.info("Moved file: {}", path.getFileName());
                                        }catch(IOException e){
                                            log.error("Failed to move file: {} {}", path.getFileName(), e.getLocalizedMessage());
                                        }
                                    }
                                });
                            }catch(IOException e){
                                log.error("Failed to list files {}", e.getLocalizedMessage());
                            }

                            GDownloader.deleteRecursively(tmpPath.toPath());

                            completedDownloads.offer(next);
                        }

                        fireListeners();
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

    @Nullable
    private static String filterVideo(String youtubeUrl){
        try{
            URL url = new URI(youtubeUrl).toURL();
            String host = url.getHost();

            if(host != null && host.contains("youtube.com")){
                String videoId = getParameter(url, "v");
                if(videoId != null){
                    return "https://www.youtube.com/watch?v=" + videoId;
                }
            }

            return null;
        }catch(MalformedURLException | URISyntaxException e){
            log.warn("Invalid url {} {}", youtubeUrl, e.getLocalizedMessage());
        }

        return null;
    }

    @Nullable
    private static String filterPlaylist(String youtubeUrl){
        try{
            URL url = new URI(youtubeUrl).toURL();
            String host = url.getHost();

            if(host != null && host.contains("youtube.com")){
                String videoId = getParameter(url, "list");
                if(videoId != null){
                    return "https://www.youtube.com/playlist?list=" + videoId;
                }
            }

            return null;
        }catch(MalformedURLException | URISyntaxException e){
            log.warn("Invalid url {} {}", youtubeUrl, e.getLocalizedMessage());
        }

        return null;
    }

    @Nullable
    private static String getParameter(URL url, String parameterName){
        String query = url.getQuery();

        if(query != null){
            String[] params = query.split("&");
            for(String param : params){
                String[] keyValue = param.split("=");
                if(keyValue.length == 2 && keyValue[0].equals(parameterName)){
                    return keyValue[1];
                }
            }
        }

        return null;
    }

    private static BrowserEnum _cachedBrowser;

    private BrowserEnum getBrowserForCookies(){
        if(_cachedBrowser != null){//I realize setting changes will have no effect until a restart
            return _cachedBrowser;
        }

        if(main.getConfig().getBrowser() == BrowserEnum.UNSET){
            String os = System.getProperty("os.name").toLowerCase();
            String browserName = "";

            try{
                if(os.contains("win")){
                    List<String> output = readOutput("reg", "query", "HKEY_CLASSES_ROOT\\http\\shell\\open\\command");

                    log.info("Default browser: {}", output);

                    for(String line : output){
                        if(line.contains(".exe")){
                            browserName = line.substring(0, line.indexOf(".exe") + 4);
                            break;
                        }
                    }
                }else if(os.contains("mac")){
                    browserName = "safari";//Why bother
                }else if(os.contains("nix") || os.contains("nux")){
                    List<String> output = readOutput("xdg-settings", "get", "default-web-browser");

                    log.info("Default browser: {}", output);

                    for(String line : output){
                        if(!line.isEmpty()){
                            browserName = line.trim();
                            break;
                        }
                    }
                }
            }catch(Exception e){
                log.error("{}", e.getCause());

            }

            BrowserEnum browser = BrowserEnum.getBrowserForName(browserName);

            if(browser == BrowserEnum.UNSET){
                _cachedBrowser = BrowserEnum.FIREFOX;
            }else{
                _cachedBrowser = browser;
            }
        }else{
            _cachedBrowser = main.getConfig().getBrowser();
        }

        return _cachedBrowser;
    }

    public static List<String> readOutput(String... command) throws IOException, InterruptedException{
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        List<String> list = new ArrayList<>();
        try(BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))){
            String line;
            while((line = in.readLine()) != null){
                list.add(line);
            }
        }

        int exitCode = process.waitFor();
        if(exitCode != 0){
            log.warn("Failed command for {}", Arrays.toString(command));
        }

        return list;
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

        public void launch(GDownloader main){
            if(!finalMediaFiles.isEmpty()){
                for(File file : finalMediaFiles){
                    if(!file.exists()){
                        continue;
                    }

                    String fileName = file.getAbsolutePath().toLowerCase();

                    //Video files get priority
                    for(VideoContainerEnum container : VideoContainerEnum.values()){
                        if(!main.getConfig().isDownloadAudioOnly() && fileName.endsWith(")." + container.getValue())
                            || main.getConfig().isDownloadAudioOnly() && fileName.endsWith(").mp3")){
                            main.open(file);
                            return;
                        }
                    }

                    main.open(file);
                    return;
                }
            }

            main.openUrlInBrowser(originalUrl);
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

            if(videoInfo.getThumbnail().startsWith("http")){
                mediaCard.setThumbnailAndDuration(videoInfo.getThumbnail(), videoInfoIn.getDuration());
            }
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
            mediaCard.setLabel(webFilter.getDisplayName(), getTitle(), truncate(text, 50));

            if(status != downloadStatus){
                downloadStatus = status;

                switch(status){
                    case QUERYING:
                        mediaCard.setPercentage(100);
                        mediaCard.setString(status.getDisplayName());
                        mediaCard.setColor(Color.MAGENTA);
                        break;
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
                }
            }else if(status == DownloadStatus.DOWNLOADING){
                mediaCard.setString(status.getDisplayName() + ": " + mediaCard.getPercentage() + "%");
            }
        }
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
