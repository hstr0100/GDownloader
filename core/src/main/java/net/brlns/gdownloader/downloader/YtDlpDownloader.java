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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.AudioBitrateEnum;
import net.brlns.gdownloader.settings.enums.AudioContainerEnum;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.enums.ThumbnailContainerEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.util.DirectoryUtils;
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
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.YT_DLP;
    }

    @Override
    public boolean isMainDownloader() {
        return true;
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return getExecutablePath().isPresent()
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
                "--extractor-args",
                "youtube:player_skip=webpage,configs,js;player_client=android,web",
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

        genericArguments.addAll(filter.getArguments(getDownloaderId(), ALL, main, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported
                || type == VIDEO && !downloadVideo
                || type == AUDIO && !main.getConfig().isDownloadAudio()
                || type == SUBTITLES && !main.getConfig().isDownloadSubtitles()
                || type == THUMBNAILS && !main.getConfig().isDownloadThumbnails()) {
                continue;
            }

            List<String> arguments = new ArrayList<>(genericArguments);

            List<String> downloadArguments = filter.getArguments(getDownloaderId(), type, main, tmpPath, entry.getUrl());
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
                success = true;
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected Map<String, Runnable> processMediaFiles(QueueEntry entry) {
        File finalPath = main.getOrCreateDownloadsDirectory();
        File tmpPath = entry.getTmpDirectory();

        QualitySettings quality = entry.getFilter().getQualitySettings();

        Map<String, Runnable> rightClickOptions = new TreeMap<>();

        try (Stream<Path> dirStream = Files.walk(tmpPath.toPath())) {
            dirStream.forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase();

                boolean isAudio = fileName.endsWith(")." + quality.getAudioContainer().getValue());
                boolean isVideo = fileName.endsWith(")." + quality.getVideoContainer().getValue());
                boolean isSubtitle = fileName.endsWith("." + quality.getSubtitleContainer().getValue());
                boolean isThumbnail = fileName.endsWith("." + quality.getThumbnailContainer().getValue());

                if (isAudio || isVideo
                    || isSubtitle && main.getConfig().isDownloadSubtitles()
                    || isThumbnail && main.getConfig().isDownloadThumbnails()) {

                    Path relativePath = tmpPath.toPath().relativize(path);
                    Path targetPath = finalPath.toPath().resolve(relativePath);

                    try {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());

                        if (isVideo) {
                            rightClickOptions.put(
                                l10n("gui.play_video"),
                                () -> entry.play(VideoContainerEnum.class));
                        }

                        if (isAudio) {
                            rightClickOptions.put(
                                l10n("gui.play_audio"),
                                () -> entry.play(AudioContainerEnum.class));
                        }

                        if (isThumbnail) {
                            rightClickOptions.put(
                                l10n("gui.view_thumbnail"),
                                () -> entry.play(ThumbnailContainerEnum.class));
                        }

                        log.info("Copied file: {}", path.getFileName());
                    } catch (IOException e) {
                        log.error("Failed to copy file: {}", path.getFileName(), e);
                    }
                }
            });
        } catch (IOException e) {
            log.error("Failed to list files", e);
        }

        return rightClickOptions;
    }

    @Override
    protected void processProgress(QueueEntry entry, String lastOutput) {
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

            entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput.replace("[download] ", ""));
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
}
