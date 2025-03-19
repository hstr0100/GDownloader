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
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.AudioBitrateEnum;
import net.brlns.gdownloader.settings.enums.AudioContainerEnum;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.enums.ThumbnailContainerEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.menu.IMenuEntry;
import net.brlns.gdownloader.ui.menu.RunnableMenuEntry;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpDownloader extends AbstractDownloader {

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    @Getter
    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

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
    protected boolean canConsumeUrl(String inputUrl) {
        return isEnabled()
            && !(inputUrl.contains("ytimg")
            || inputUrl.contains("ggpht")
            || inputUrl.endsWith("youtube.com/")
            || inputUrl.endsWith(".jpg")
            || inputUrl.endsWith(".png")
            || inputUrl.endsWith(".webp"));
    }

    @Override
    protected boolean tryQueryVideo(QueueEntry queueEntry) {
        try {
            long start = System.currentTimeMillis();

            List<String> arguments = new ArrayList<>();
            arguments.addAll(List.of(
                executablePath.get().getAbsolutePath(),
                "--dump-json",
                "--flat-playlist",
                //"--extractor-args",// TODO: Sometimes complains about missing PO token, unreproducible. Investigate.
                //"youtube:player_skip=webpage,configs,js;player_client=android,web",
                queueEntry.getUrl()
            ));

            if (main.getConfig().isReadCookiesFromBrowser()) {
                arguments.addAll(List.of(
                    "--cookies-from-browser",
                    main.getBrowserForCookies().getName()
                ));
            }

            List<String> list = GDownloader.readOutput(
                arguments.stream().toArray(String[]::new));

            if (main.getConfig().isDebugMode()) {
                long what = System.currentTimeMillis() - start;
                double on = 1000L * 365.25 * 24 * 60 * 60 * 1000;
                double earth = (what / on) * 100;

                log.info("The slow as molasses thing took {}ms, jesus man! that's about {}% of a millenium",
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

        QualitySettings quality = filter.getQualitySettings();
        AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

        if (!downloadVideo && downloadAudio && audioBitrate == AudioBitrateEnum.NO_AUDIO) {
            return new DownloadResult(combineFlags(FLAG_NO_METHOD, FLAG_NO_METHOD_AUDIO));
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        List<String> genericArguments = new ArrayList<>();

        genericArguments.addAll(List.of(
            executablePath.get().getAbsolutePath(),
            "-i"
        ));

        if (ffmpegPath.isPresent()) {
            genericArguments.addAll(List.of(
                "--ffmpeg-location",
                ffmpegPath.get().getAbsolutePath()
            ));
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
                || type == THUMBNAILS && (alreadyDownloaded || !main.getConfig().isDownloadThumbnails())) {
                continue;
            }

            entry.getMediaCard().setPlaceholderIcon(type);

            List<String> arguments = new ArrayList<>(genericArguments);

            List<String> downloadArguments = filter.getArguments(this, type, manager, tmpPath, entry.getUrl());
            arguments.addAll(downloadArguments);

            if (main.getConfig().isDebugMode()) {
                log.debug("ALL {}: Type {} ({}): {}",
                    genericArguments,
                    type,
                    filter.getDisplayName(),
                    downloadArguments);
            }

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
                    return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
                } else {
                    // These can be treated as low priority downloads since thumbnails
                    // and subtitles are already embedded by default, if they fail we just move on.
                    // For now, downloading only subs or thumbs is not supported.
                    log.error("Failed to download {}: {}", type, lastOutput);
                }
            } else {
                if (lastOutput.contains("recorded in the archive")) {
                    alreadyDownloaded = true;
                }

                success = true;
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected Map<String, IMenuEntry> processMediaFiles(QueueEntry entry) {
        File finalPath = main.getOrCreateDownloadsDirectory();
        File tmpPath = entry.getTmpDirectory();

        QualitySettings quality = entry.getFilter().getQualitySettings();

        Map<String, IMenuEntry> rightClickOptions = new TreeMap<>();

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .sorted(Comparator.reverseOrder()) // Process files before directories
                .toList();

            for (Path path : paths) {
                if (path.equals(tmpPath.toPath())) {
                    continue;
                }

                Path relativePath = tmpPath.toPath().relativize(path);
                Path targetPath = determineTargetPath(path, finalPath, relativePath, quality);

                try {
                    if (Files.isDirectory(targetPath)) {
                        Files.createDirectories(targetPath);

                        log.info("Created directory: {}", targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);
                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());
                        updateRightClickOptions(path, quality, rightClickOptions, entry);

                        log.info("Moved file: {}", targetPath);
                    }
                } catch (FileAlreadyExistsException e) {
                    log.warn("File or directory already exists: {}", targetPath, e);
                } catch (IOException e) {
                    log.error("Failed to move file: {}", path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files", e);
        }

        return rightClickOptions;
    }

    private void updateRightClickOptions(Path path, QualitySettings quality, Map<String, IMenuEntry> rightClickOptions, QueueEntry entry) {
        if (isFileType(path, quality.getVideoContainer().getValue())) {
            rightClickOptions.putIfAbsent(l10n("gui.play_video"),
                new RunnableMenuEntry(() -> entry.play(VideoContainerEnum.class)));
        } else if (isFileType(path, quality.getAudioContainer().getValue())) {
            rightClickOptions.putIfAbsent(l10n("gui.play_audio"),
                new RunnableMenuEntry(() -> entry.play(AudioContainerEnum.class)));
        } else if (isFileType(path, quality.getThumbnailContainer().getValue())) {
            rightClickOptions.putIfAbsent(l10n("gui.view_thumbnail"),
                new RunnableMenuEntry(() -> entry.play(ThumbnailContainerEnum.class)));
        }
    }

    private Path determineTargetPath(Path path, File finalPath, Path relativePath, QualitySettings quality) {
        boolean isAudio = isFileType(path, quality.getAudioContainer().getValue());
        boolean isVideo = isFileType(path, quality.getVideoContainer().getValue());
        boolean isSubtitle = isFileType(path, quality.getSubtitleContainer().getValue());
        boolean isThumbnail = isFileType(path, quality.getThumbnailContainer().getValue());

        if (isAudio || isVideo
            || (isSubtitle && main.getConfig().isDownloadSubtitles())
            || (isThumbnail && main.getConfig().isDownloadThumbnails())
            || Files.isDirectory(path)) {
            return finalPath.toPath().resolve(relativePath);
        }

        return Path.of(finalPath.getAbsolutePath(), l10n("system.unknown_directory_name")).resolve(relativePath);
    }

    private boolean isFileType(Path path, String extension) {
        return path.getFileName().toString().toLowerCase().endsWith("." + extension);
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception {
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);
        finalArgs.add(entry.getUrl());

        ProcessBuilder processBuilder = new ProcessBuilder(finalArgs);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        entry.setProcess(process);

        String lastOutput = "";

        try (
            ReadableByteChannel stdInput = Channels.newChannel(process.getInputStream())) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            StringBuilder output = new StringBuilder();
            char prevChar = '\0';

            while (manager.isRunning() && !entry.getCancelHook().get() && process.isAlive()) {
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

            if (!manager.isRunning() || entry.getCancelHook().get()) {
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

            if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
            }
        }
    }

    @Override
    public void close() {

    }
}
