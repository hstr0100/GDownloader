/*
 * Copyright (C) 2025 hstr0100
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
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.FlagUtil;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.util.FileUtils.relativize;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SpotDLDownloader extends AbstractDownloader {

    private static final byte NOTIFY_COOKIE_JAR = 0x01;
    private static final byte NOTIFY_USER_AUTH = 0x02;

    private final AtomicInteger notificationFlags = new AtomicInteger();

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    public SpotDLDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent()
            && main.getConfig().isSpotDLEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.SPOTDL;
    }

    @Override
    public boolean isMainDownloader() {
        return false;
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.singletonList(SPOTIFY);
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        try {
            for (DownloadTypeEnum downloadType : getArchivableTypes()) {
                FileUtils.removeLineIfExists(
                    getArchiveFile(downloadType),
                    queueEntry.getUrl());
            }
        } catch (Exception e) {
            log.error("Failed to remove archive entry for video: {}", queueEntry.getUrl(), e);
        }
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return isEnabled() && (inputUrl.contains("spotify.com") || inputUrl.contains("spotify.link"));
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        try {
            String url = queueEntry.getUrl();

            Optional<MediaInfo> mediaInfoOptional = manager.getMetadataManager().fetchMetadata(url);

            if (mediaInfoOptional.isPresent()) {
                MediaInfo mediaInfo = mediaInfoOptional.get();
                queueEntry.setMediaInfo(mediaInfo);

                PersistenceManager persistence = main.getPersistenceManager();
                if (!queueEntry.getCancelHook().get() && persistence.isInitialized()) {
                    persistence.getMediaInfos().addMediaInfo(mediaInfo.toEntity(queueEntry.getDownloadId()));
                }

                return true;
            }
        } catch (Exception e) {
            log.error("{} failed to query for metadata {}: {}", getDownloaderId(), queueEntry.getUrl(), e.getMessage());

            if (log.isDebugEnabled()) {
                log.error("Exception:", e);
            }
        }

        return false;
    }

    @Nullable
    @Override
    public File getCookieJarFile() {
        File cookieJar = super.getCookieJarFile();

        // Lets not spam the logs for every download.
        if (cookieJar == null && !FlagUtil.isSet(notificationFlags, NOTIFY_COOKIE_JAR)) {
            log.info("""
                If you have a YouTube Music Premium account, consider setting up a cookies.txt file at:
                    {}
                for better quality downloads (256kbps vs 128kbps). Please visit
                    https://github.com/spotDL/spotify-downloader/blob/master/docs/usage.md#youtube-music-premium
                for more information""", cookieJar);

            FlagUtil.set(notificationFlags, NOTIFY_COOKIE_JAR);
        }

        return cookieJar;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        AbstractUrlFilter filter = entry.getFilter();

        if (!main.getConfig().isSpotDLEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        ProcessArguments genericArguments = new ProcessArguments(
            executablePath.get().getAbsolutePath(),
            "--simple-tui"//As far as I can tell, these change nothing. The way it's displayed now, Java cannot read SpotDL's progress bar.
        //, "--log-level", "DEBUG"
        );

        if (main.getConfig().isRespectSpotDLConfigFile()) {
            // We can't specify config location for spotDL, our only choice is to copy or symlink it.
            genericArguments.add("--config");
        }

        Optional<String> ffmpegExecutable = main.getFfmpegTranscoder().getFFmpegExecutable();
        if (ffmpegExecutable.isPresent()) {
            genericArguments.add("--ffmpeg", ffmpegExecutable.get());
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != SPOTIFY
                // TODO: Verify if it makes sense to respect the audio toggle when the platform is strictly audio-only.
                //|| !main.getConfig().isDownloadAudio()
                || !main.getConfig().isSpotDLEnabled()) {
                continue;
            }

            entry.setCurrentDownloadType(type);

            ProcessArguments arguments = new ProcessArguments(
                genericArguments,
                filter.getArguments(this, type, manager, tmpPath, entry.getUrl()));

            Pair<Integer, String> result = processDownload(entry, arguments);

            if (result == null || entry.getCancelHook().get()) {
                return new DownloadResult(FLAG_STOPPED);
            }

            lastOutput = result.getValue();

            if (result.getKey() != 0) {
                // SpotDL will attempt to download anything you feed it, even non-spotify links.
                // Since this behavior is undesirable for our use case (downloads take longer to exit),
                // we must limit its functionality to Spotify links only.
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
    protected DownloadResult transcodeMediaFiles(QueueEntry entry) {
        // TODO
        return null;
    }

    @Override
    protected void processMediaFiles(QueueEntry entry) {
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "SpotDL");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

        File tmpPath = entry.getTmpDirectory();
        Path deepestDir = null;
        int maxDepth = -1;

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .filter(path -> !path.equals(tmpPath.toPath()))
                .collect(Collectors.toList());

            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    Path targetPath = relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath);
                        int depth = targetPath.getNameCount();
                        if (depth > maxDepth) {
                            maxDepth = depth;
                            deepestDir = targetPath;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to create directory: {}", targetPath, e);
                    }
                }
            }

            for (Path path : paths) {
                if (!Files.isDirectory(path)) {
                    Path targetPath = relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);
                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                entry.getFinalMediaFiles().add(deepestDir.toFile());
            }
        } catch (IOException e) {
            log.error("Failed to process media files", e);
        }
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception {
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);

        String finalUrl = entry.getUrl();
        if (finalUrl.contains("spotify.com/collection/tracks")) {
            finalUrl = "saved";
        } else if (finalUrl.contains("spotify.com/collection/playlists")) {
            // This url just redirects to /tracks, but here we can map them internally.
            finalUrl = "all-user-playlists";
        } else if (finalUrl.contains("spotify.com/collection/albums")) {
            finalUrl = "all-user-saved-albums";
        }

        if (!finalUrl.contains("http")) {
            // Accessing user playlists requires authentication
            finalArgs.add("--user-auth");
        }

        finalArgs.add("download");
        finalArgs.add(finalUrl);

        entry.setLastCommandLine(finalArgs, true);

        CancelHook cancelHook = entry.getCancelHook().derive(manager::isRunning, true);
        Process process = main.getProcessMonitor().startProcess(finalArgs, cancelHook);
        entry.setProcess(process);

        String lastOutput = "";

        boolean tainted = false;

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while (!cancelHook.get() && process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedException("Download interrupted");
                }

                if (reader.ready()) {
                    if ((line = reader.readLine()) != null) {
                        lastOutput = line;

                        processProgress(entry, lastOutput);

                        if (lastOutput.contains(" download error")) {
                            tainted = true;
                        }
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.debug("Sleep interrupted, closing process");
                        process.destroyForcibly();
                    }
                }
            }

            long stopped = System.currentTimeMillis() - start;

            if (cancelHook.get()) {
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process halted after {}ms.", stopped);
                }

                return null;
            } else {
                int exitCode = process.waitFor();
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process took {}ms, exit code: {}", stopped, exitCode);
                }

                if (exitCode == 0 && tainted) {
                    // Under certain conditions, spotDL erroneously returns 0 even if all downloads have failed.
                    // e.g:
                    // AudioProviderError: YT-DLP download error - https://...
                    // 1/1 complete
                    //
                    // This is incorrect.
                    exitCode = 1;

                    log.warn("spotDL was unable to download some or all items.");
                }

                return new Pair<>(exitCode, lastOutput);
            }
        } catch (IOException e) {
            log.info("IO error: {}", e.getMessage());

            return null;
        } finally {
            entry.getDownloadStarted().set(false);

            // Our ProcessMonitor will take care of closing the underlying process.
        }
    }

    private void processProgress(QueueEntry entry, String lastOutput) {
        if (main.getConfig().isDebugMode()) {
            log.debug("[{}] - {}", entry.getDownloadId(), lastOutput);
        }

        if (lastOutput.contains("Replacing with empty") || lastOutput.endsWith("string.")) {
            return;
        }

        if (lastOutput.contains("Go to the following URL: ")) {
            if (!FlagUtil.isSet(notificationFlags, NOTIFY_USER_AUTH)) {
                int httpIndex = lastOutput.indexOf("https://");

                if (httpIndex != -1) {
                    String url = lastOutput.substring(httpIndex).trim();
                    main.openUrlInBrowser(url);

                    FlagUtil.set(notificationFlags, NOTIFY_USER_AUTH);
                }
            }
        }

        if (lastOutput.contains("Downloading")) {
            entry.getMediaCard().setPercentage(-1);
            entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput);
        } else {
            if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {

    }
}
