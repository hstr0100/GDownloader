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
package net.brlns.gdownloader.settings.filters;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.File;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.AudioContainerEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.util.TemplateConverter;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.lang.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenericFilter extends AbstractUrlFilter {

    public static final String ID = "default";

    @SuppressWarnings("this-escape")
    public GenericFilter() {
        setId(ID);
        setVideoNamePattern("%(title).60s (%(resolution)s).%(ext)s");
        setAudioNamePattern(getVideoNamePattern().replace("%(resolution)s", "%(audio_bitrate)s"));
        setEmbedThumbnailAndMetadata(false);
    }

    @JsonIgnore
    @Override
    public String getDisplayName() {
        String name = getFilterName();
        if (name.isEmpty()) {
            name = l10n("enums.web_filter.default");
        }

        return name;
    }

    @JsonIgnore
    @Override
    protected ProcessArguments buildArguments(AbstractDownloader downloader, DownloadTypeEnum typeEnum, DownloadManager manager, File savePath, String inputUrl) {
        Settings config = manager.getMain().getConfig();
        QualitySettings quality = getActiveQualitySettings(config);
        AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

        String audioPattern = getAudioNamePattern();
        audioPattern = audioPattern.replace("%(audio_bitrate)s",
            (audioBitrate != AudioBitrateEnum.NO_AUDIO ? audioBitrate.getValue() : 0) + "kbps");

        ProcessArguments arguments = new ProcessArguments();

        File archiveFile = downloader.getArchiveFile(typeEnum);

        switch (downloader.getDownloaderId()) {
            case YT_DLP -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.add(
                        "--download-archive",
                        archiveFile.getAbsolutePath()
                    );
                }

                switch (typeEnum) {
                    case ALL -> {
                        if (config.isEnableExtraArguments()) {
                            for (String arg : config.getExtraYtDlpArguments().split(" ")) {
                                if (!arg.trim().isEmpty()) {
                                    arguments.add(arg);
                                }
                            }
                        }

                        if (config.isAutoDownloadRetry()) {
                            arguments.add(
                                "--fragment-retries", config.getMaxFragmentRetries()
                            );
                        }

                        if (config.isRandomIntervalBetweenDownloads()) {
                            arguments.add(
                                "--max-sleep-interval", 45,
                                "--min-sleep-interval", 25
                            );
                        }

                        if (config.isImpersonateBrowser()) {
                            arguments.add("--impersonate", "chrome:windows-10");
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.add("--proxy", proxyUrl);
                        }

                        boolean cookiesRead = false;
                        if (config.isReadCookiesFromBrowser()) {
                            arguments.add(
                                "--cookies-from-browser",
                                manager.getMain().getBrowserForCookies().getName()
                            );
                            cookiesRead = true;
                        } else {
                            File cookieJar = downloader.getCookieJarFile();
                            if (cookieJar != null) {
                                arguments.add(
                                    "--cookies",
                                    cookieJar.getAbsolutePath()
                                );
                                cookiesRead = true;
                            }
                        }

                        if (cookiesRead && manager.getMain().getConfig().isMissingFormatsWorkaround()) {
                            arguments.add(
                                "--extractor-args",
                                "youtube:player_client=web_safari"
                            );
                        }

                        if (GDownloader.isWindows()) {
                            // NTFS shenanigans ahead
                            // TODO: query registry for longpath support status
                            arguments.add(
                                "--windows-filenames",
                                "--trim-filenames", 240// Give some extra room for fragment files
                            );
                        }
                    }
                    case VIDEO -> {
                        VideoContainerEnum videoContainer = quality.getVideoContainer();

                        String qualitySelector = quality.buildQualitySelector();

                        if (config.isMergeAllAudioTracks()) {
                            // TODO: Update this once the following yt-dlp feature request is addressed:
                            // https://github.com/yt-dlp/yt-dlp/issues/1176
                            // Currently, it merges ALL the best audio tracks - including duplicates.
                            // The desired behavior is to keep only the best track for each language - no duplicates.
                            qualitySelector = qualitySelector.replace(
                                "bestaudio", "bestaudio+mergeall");
                            arguments.add("--audio-multistreams");
                        }

                        arguments.add(
                            "-o", savePath.getAbsolutePath() + File.separator + getVideoNamePattern(),
                            "-f", qualitySelector,
                            "--merge-output-format",
                            videoContainer.getValue()// TODO: same as source will actually default to mp4
                        );

                        if (isEmbedThumbnailAndMetadata()) {
                            switch (quality.getVideoContainer()) {
                                // aac, alac: not working
                                // flv, avi, mov: cannot merge (unsupported codecs)
                                case WEBM ->// WebM does not support thumbnail embedding
                                    arguments.add(
                                        "--embed-metadata",
                                        "--embed-chapters",
                                        "--embed-subs",
                                        "--sub-langs",
                                        "all,-live_chat"
                                    );
                                case MKV, MP4, MOV, DEFAULT ->
                                    arguments.add(
                                        "--convert-thumbnails", "png",
                                        "--embed-thumbnail",
                                        "--embed-metadata",
                                        "--embed-chapters",
                                        "--embed-subs",
                                        "--sub-langs",
                                        "all,-live_chat"
                                    );
                            }
                        }
                        // Transcoding is handled by our built-in ffmpeg transcoder.
                    }
                    case AUDIO -> {
                        if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                            AudioContainerEnum audioContainer = quality.getAudioContainer();
                            arguments.add(
                                "-o", savePath.getAbsolutePath() + File.separator + audioPattern,
                                "-f", "bestaudio/worstvideo*+bestaudio/best",
                                "--extract-audio",
                                "--audio-format",
                                audioContainer.getValue(),
                                "--audio-quality",
                                audioBitrate.getValue() + "k"
                            );

                            if (isEmbedThumbnailAndMetadata()) {
                                if (audioContainer != AudioContainerEnum.WAV
                                    && audioContainer != AudioContainerEnum.AAC) {
                                    arguments.add(
                                        "--embed-thumbnail",
                                        "--embed-metadata"
                                    );
                                }
                            }
                        }
                    }
                    case THUMBNAILS -> {
                        arguments.add(
                            "-o", savePath.getAbsolutePath() + File.separator
                            + (config.isDownloadVideo() ? getVideoNamePattern() : audioPattern),
                            "--write-thumbnail",
                            "--skip-download",
                            "--convert-thumbnails",
                            quality.getThumbnailContainer().getValue()
                        );
                    }
                    case SUBTITLES -> {
                        arguments.add(
                            "-o", savePath.getAbsolutePath() + File.separator
                            + (config.isDownloadVideo() ? getVideoNamePattern() : audioPattern),
                            "--all-subs",
                            "--skip-download",
                            "--sub-format",
                            quality.getSubtitleContainer().getValue(),
                            "--convert-subs",
                            quality.getSubtitleContainer().getValue()
                        );
                    }
                    default ->
                        throw new IllegalArgumentException();
                }
            }
            case GALLERY_DL -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.add(
                        "--download-archive",
                        archiveFile.getAbsolutePath()
                    );
                }

                switch (typeEnum) {
                    case ALL -> {
                        if (config.isEnableExtraArguments()) {
                            for (String arg : config.getExtraGalleryDlArguments().split(" ")) {
                                if (!arg.trim().isEmpty()) {
                                    arguments.add(arg);
                                }
                            }
                        }

                        arguments.add(
                            "--retries", config.getMaxDownloadRetries());

                        if (config.isRandomIntervalBetweenDownloads()) {
                            arguments.add(
                                "--sleep", "5.0-15.0",
                                "--sleep-request", 2
                            );
                        }

                        if (config.isImpersonateBrowser()) {
                            arguments.add("--user-agent", "browser");
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.add("--proxy", proxyUrl);
                        }

                        if (config.isReadCookiesFromBrowser()) {
                            arguments.add(
                                "--cookies-from-browser",
                                manager.getMain().getBrowserForCookies().getName()
                            );
                        } else {
                            File cookieJar = downloader.getCookieJarFile();
                            if (cookieJar != null) {
                                arguments.add(
                                    "--cookies",
                                    cookieJar.getAbsolutePath()
                                );
                            }
                        }
                    }
                    case GALLERY -> {
                        String fileName = URLUtils.getDirectoryPath(inputUrl);

                        arguments.add(
                            "-D", savePath.getAbsolutePath() + (fileName != null ? File.separator + fileName : "")
                        );
                    }
                    default ->
                        throw new IllegalArgumentException();
                }
            }

            case SPOTDL -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.add(
                        "--archive",
                        archiveFile.getAbsolutePath()
                    );
                }

                switch (typeEnum) {
                    case ALL -> {
                        if (config.isEnableExtraArguments()) {
                            for (String arg : config.getExtraSpotDLArguments().split(" ")) {
                                if (!arg.trim().isEmpty()) {
                                    arguments.add(arg);
                                }
                            }
                        }

                        arguments.add(
                            "--max-retries", config.getMaxDownloadRetries());

                        if (config.isUseSponsorBlock()) {
                            arguments.add("--sponsor-block");
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.add("--proxy", proxyUrl);
                        }

                        File cookieJar = downloader.getCookieJarFile();
                        if (cookieJar != null) {
                            arguments.add(
                                "--cookie-file",
                                cookieJar.getAbsolutePath()
                            );
                        }
                    }
                    case SPOTIFY -> {
                        if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                            String audioPatternWithBitrate = getAudioNamePattern()
                                .replace("%(audio_bitrate)s", audioBitrate.getValue() + "kbps")
                                .replace("{bitrate}", audioBitrate.getValue() + "kbps");

                            arguments.add(
                                "--output",
                                savePath.getAbsolutePath() + File.separator
                                + TemplateConverter.convertTemplateForSpotDL(audioPatternWithBitrate),
                                "--format",
                                quality.getAudioContainer().getValue(),
                                "--bitrate",
                                Math.clamp(audioBitrate.getValue(), 8, 320) + "k"
                            );

                            if (!isEmbedThumbnailAndMetadata()) {
                                // SpotDL does not appear to support disabling metadata embedding.
                                // This is the best we can do to respect user settings.
                                arguments.add("--skip-album-art");
                            }
                        }
                    }
                    default ->
                        throw new IllegalArgumentException();
                }
            }
            default -> {

            }
        }

        return arguments;
    }

    @JsonIgnore
    @Override
    public boolean areCookiesRequired() {
        return false;
    }

    @JsonIgnore
    @Override
    public boolean canAcceptUrl(String url, GDownloader main) {
        return true;
    }

}
