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
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
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
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.AudioContainerEnum;
import net.brlns.gdownloader.settings.enums.SubtitleContainerEnum;
import net.brlns.gdownloader.settings.enums.ThumbnailContainerEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.settings.enums.DescriptionContainerEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.FileUtils.relativize;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpDownloader extends AbstractDownloader {

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    public YtDlpDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent();// TODO: allow disabling?
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.YT_DLP;
    }

    @Override
    public boolean isMainDownloader() {
        return true;
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return List.of(VIDEO, AUDIO);
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        if (!queueEntry.getQueried().get() || queueEntry.getMediaInfo() == null) {
            // We can't have the id we need without querying for it.
            return;
        }

        try {
            for (DownloadTypeEnum downloadType : getArchivableTypes()) {
                FileUtils.removeLineIfExists(
                    getArchiveFile(downloadType),
                    queueEntry.getMediaInfo().getId());
            }
        } catch (Exception e) {
            log.error("Failed to remove archive entry for video: {}", queueEntry.getUrl(), e);
        }
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        // If SpotDL is disabled, try to consume url with yt-dlp instead (Not currently supported)
        boolean isSpotifyUrl = inputUrl.contains("spotify.com/") || inputUrl.contains("spotify.link/");
        boolean spotDlEnabled = main.getConfig().isSpotDLEnabled();

        // Check if it's not a garbage URL
        boolean isNotGarbageUrl = !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/")
            || inputUrl.endsWith(".jpg")
            || inputUrl.endsWith(".png")
            || inputUrl.endsWith(".webp"));

        return isEnabled()
            && isNotGarbageUrl
            && (!isSpotifyUrl || !spotDlEnabled);
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        try {
            if (!canConsumeUrl(queueEntry.getUrl())) {
                return false;
            }

            long start = System.currentTimeMillis();

            ProcessArguments arguments = new ProcessArguments(
                executablePath.get().getAbsolutePath(),
                "--dump-json",
                "--flat-playlist",
                "--playlist-items", "1",
                queueEntry.getUrl());

            if (main.getConfig().isReadCookiesFromBrowser()) {
                arguments.add(
                    "--cookies-from-browser",
                    main.getBrowserForCookies().getName()
                );
            } else {
                File cookieJar = getCookieJarFile();
                if (cookieJar != null) {
                    arguments.add(
                        "--cookies",
                        cookieJar.getAbsolutePath()
                    );
                }
            }

            List<String> list = main.readOutput(arguments);

            if (main.getConfig().isDebugMode()) {
                long what = System.currentTimeMillis() - start;
                double on = 1000L * 365.25 * 24 * 60 * 60 * 1000;
                double earth = (what / on) * 100;

                log.info("The slow as molasses 'yt-dlp --dump-json' took {}ms, jesus man! that's about {}% of a millenium",
                    what, String.format("%.12f", earth));
            }

            for (String line : list) {
                if (!line.startsWith("{")) {
                    continue;
                }

                MediaInfo info = GDownloader.OBJECT_MAPPER.readValue(line, MediaInfo.class);

                queueEntry.setMediaInfo(info);

                PersistenceManager persistence = main.getPersistenceManager();
                if (!queueEntry.getCancelHook().get() && persistence.isInitialized()) {
                    persistence.getMediaInfos().addMediaInfo(info.toEntity(queueEntry.getDownloadId()));
                }

                return true;
            }
        } catch (Exception e) {
            log.error("Failed to parse json, yt-dlp returned malformed data for url {}", queueEntry.getUrl(), e);
        }

        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        AbstractUrlFilter filter = entry.getFilter();

        boolean downloadAudio = main.getConfig().isDownloadAudio();
        boolean downloadVideo = main.getConfig().isDownloadVideo();

        if (!downloadAudio && !downloadVideo) {
            return new DownloadResult(combineFlags(FLAG_NO_METHOD, FLAG_NO_METHOD_VIDEO));
        }

        QualitySettings quality = filter.getActiveQualitySettings(main.getConfig());
        AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

        if (!downloadVideo && downloadAudio && audioBitrate == AudioBitrateEnum.NO_AUDIO) {
            return new DownloadResult(combineFlags(FLAG_NO_METHOD, FLAG_NO_METHOD_AUDIO));
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        ProcessArguments genericArguments = new ProcessArguments(
            executablePath.get().getAbsolutePath(),
            "-i");

        Optional<File> ffmpegPath = main.getFfmpegTranscoder().getFfmpegPath();
        if (ffmpegPath.isPresent()) {
            genericArguments.add(
                "--ffmpeg-location",
                ffmpegPath.get().getAbsolutePath()
            );
        }

        if (!main.getConfig().isRespectYtDlpConfigFile()) {
            genericArguments.add("--ignore-config");
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        boolean alreadyDownloaded = false;

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported
                || type == VIDEO && !downloadVideo
                || type == AUDIO && !main.getConfig().isDownloadAudio()
                || type == SUBTITLES && (alreadyDownloaded || !main.getConfig().isDownloadSubtitles())
                || type == THUMBNAILS && (alreadyDownloaded || !main.getConfig().isDownloadThumbnails())
                || type == DESCRIPTION && (alreadyDownloaded || !main.getConfig().isDownloadDescription())) {
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
                if (lastOutput.contains("Unsupported URL")) {
                    return new DownloadResult(FLAG_UNSUPPORTED, lastOutput);
                }

                if (type == VIDEO || type == AUDIO) {
                    // Non-zero output for a playlist likely means one or more items were unavailable.
                    if (lastOutput.contains("Finished downloading playlist")) {
                        if (type == VIDEO && main.getConfig().isYtDlpTranscoding()) {
                            DownloadResult transcodeResult = transcodeMediaFiles(entry);

                            if (main.getConfig().isFailDownloadsOnTranscodingFailures()
                                && !FLAG_SUCCESS.isSet(transcodeResult.getFlags())) {
                                return transcodeResult;
                            }
                        }

                        success = true;
                        continue;
                    }

                    return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
                } else {
                    // These can be treated as low priority downloads since thumbnails
                    // and subtitles are already embedded by default, if they fail we just move on.
                    // For now, downloading only subs or thumbs is not supported.
                    log.error("Failed to download {}: {}", type, lastOutput);
                }
            } else {
                if (type == VIDEO && main.getConfig().isYtDlpTranscoding()) {
                    DownloadResult transcodeResult = transcodeMediaFiles(entry);

                    if (main.getConfig().isFailDownloadsOnTranscodingFailures()
                        && !FLAG_SUCCESS.isSet(transcodeResult.getFlags())) {
                        return transcodeResult;
                    }
                }

                if (lastOutput.contains("recorded in the archive")) {
                    alreadyDownloaded = true;
                }

                success = true;
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected void processMediaFiles(QueueEntry entry) {
        File finalPath = main.getOrCreateDownloadsDirectory();
        File tmpPath = entry.getTmpDirectory();

        Path deepestDir = null;
        int maxDepth = -1;

        QualitySettings quality = entry.getFilter().getActiveQualitySettings(main.getConfig());

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .filter(path -> !path.equals(tmpPath.toPath())
                && !path.toString().contains(FileUtils.TMP_FILE_IDENTIFIER)
                && !path.toString().endsWith(".lock"))
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
                    Path targetPath = determineTargetPath(tmpPath, finalPath, path, quality);

                    if (DescriptionContainerEnum.isFileType(path) && main.getConfig().isSaveDescriptionFileAsTxt()) {
                        File derivedDescription = FileUtils.deriveFile(targetPath.toFile(), "", "txt");
                        targetPath = derivedDescription.toPath();
                    }

                    try {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);

                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);

                        if (!entry.isPlaylist() || !main.getConfig().isUseUploadTimeAsFileTime()) {
                            // TODO handle playlists correctly, for that we need to fetch the entire playlist data not just the first item.
                            // For now we're just passing through the dates as set by yt-dlp.
                            updateFileTimes(entry, targetPath);
                        }

                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                entry.getFinalMediaFiles().add(deepestDir.toFile());
            }

            updateThumbnailAndSubtitleTimes(entry, quality);
        } catch (IOException e) {
            log.error("Failed to process media files", e);
        }
    }

    private void updateThumbnailAndSubtitleTimes(QueueEntry entry, QualitySettings quality) {
        if (!entry.isPlaylist()) {
            return;
        }

        // File times aren't applied to subtitles or thumbnails; this is a temporary O(n²) workaround.
        for (File file : entry.getFinalMediaFiles()) {
            if (!file.exists()) {
                // We might unknowingly be holding references to deleted files here.
                continue;
            }

            Path path = file.toPath();

            boolean isAudio = AudioContainerEnum.isFileType(path);
            boolean isVideo = VideoContainerEnum.isFileType(path) || VideoContainerEnum.isGif(path);

            if (isAudio || isVideo) {
                String filenameWithoutExt = FileUtils.getFilenameWithoutExtension(file);
                for (File childFile : entry.getFinalMediaFiles()) {
                    if (!childFile.exists()) {
                        continue;
                    }

                    boolean isSubtitle = SubtitleContainerEnum.isFileType(childFile);
                    boolean isThumbnail = ThumbnailContainerEnum.isFileType(childFile);
                    boolean isDescription = DescriptionContainerEnum.isFileType(childFile);

                    if (childFile.getName().contains(filenameWithoutExt)
                        && (isSubtitle || isThumbnail || isDescription)) {
                        FileUtils.copyAllFileTimes(path, childFile.toPath());
                    }
                }
            }
        }
    }

    private Path determineTargetPath(File tmpPath, File finalPath, Path path, QualitySettings quality) {
        boolean isAudio = AudioContainerEnum.isFileType(path);
        boolean isVideo = VideoContainerEnum.isFileType(path) || VideoContainerEnum.isGif(path);
        boolean isSubtitle = SubtitleContainerEnum.isFileType(path);
        boolean isThumbnail = ThumbnailContainerEnum.isFileType(path);
        boolean isDescription = DescriptionContainerEnum.isFileType(path);

        if (isAudio || isVideo
            || (isSubtitle && main.getConfig().isDownloadSubtitles())
            || (isThumbnail && main.getConfig().isDownloadThumbnails())
            || (isDescription && main.getConfig().isDownloadDescription())) {
            return relativize(tmpPath, finalPath, path);
        }

        return relativize(tmpPath.toPath(), Paths.get(finalPath.toString(), l10n("system.unknown_directory_name")), path);
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception {
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);
        finalArgs.add(entry.getUrl());

        entry.setLastCommandLine(finalArgs, true);

        CancelHook cancelHook = entry.getCancelHook().derive(manager::isRunning, true);
        Process process = main.getProcessMonitor().startProcess(finalArgs, cancelHook);
        entry.setProcess(process);

        String lastOutput = "";

        try (
            ReadableByteChannel stdInput = Channels.newChannel(process.getInputStream())) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            StringBuilder output = new StringBuilder();
            char prevChar = '\0';

            while (!cancelHook.get() && process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    log.debug("Process is closing");
                    process.destroyForcibly();
                    throw new InterruptedException("Download interrupted");
                }

                buffer.clear();
                int bytesRead = stdInput.read(buffer);
                if (bytesRead > 0) {
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        char ch = (char)buffer.get();
                        output.append(ch);

                        if (ch == '\n' || (ch == '\r' && prevChar != '\n')) {
                            lastOutput = output.toString().replace("\n", "");
                            output.setLength(0);
                        }

                        prevChar = ch;
                    }

                    processProgress(entry, lastOutput);
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.debug("Sleep interrupted, closing process");
                    process.destroyForcibly();
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
        double lastPercentage = entry.getMediaCard().getPercentage();

        if (lastOutput.contains("Sleeping") && lastOutput.contains("...")) {
            entry.updateStatus(DownloadStatusEnum.WAITING, lastOutput);
            return;
        }

        if (lastOutput.contains("[download]") && !lastOutput.contains("Destination:")) {
            String[] parts = lastOutput.split("\\s+");
            for (String part : parts) {
                if (part.endsWith("%")) {
                    double percent = Double.parseDouble(part.replace("%", ""));
                    if (percent > lastPercentage || percent < 5
                        || Math.abs(percent - lastPercentage) > 10) {
                        entry.getMediaCard().setPercentage(percent);
                        lastPercentage = percent;
                    }
                }
            }

            entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput.replace("[download] ", ""), false);
        } else {
            if (main.getConfig().isDebugMode()) {
                log.debug("[{}] - {}", entry.getDownloadId(), lastOutput);
            }

            if ((lastOutput.contains("time=") && lastOutput.contains("bitrate=")) || lastOutput.contains(" Opening '")) {
                entry.getMediaCard().setPercentage(-1);
                entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput, false);
            } else {
                if (lastOutput.contains("Downloading webpage")) {// Reset when looping through a playlist
                    entry.getDownloadStarted().set(false);
                }

                if (entry.getDownloadStarted().get()) {
                    entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
                } else {
                    entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
                }
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {

    }
}
