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
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.AudioBitrateEnum;
import net.brlns.gdownloader.settings.enums.AudioCodecEnum;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.util.TemplateConverter;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

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

    @Override
    public void setFilterName(String name) {
        // Bizarre intermittent condition triggered by Jackson, unable to reliably reproduce with a fresh config.
        if (notNullOrEmpty(name) && getClass().getSimpleName().equals("GenericFilter")) {
            log.error("Tried to set a filter name ({}) to the generic filter {}", name, this);
            return;
        }

        super.setFilterName(name);
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
    protected List<String> buildArguments(AbstractDownloader downloader, DownloadTypeEnum typeEnum, DownloadManager manager, File savePath, String inputUrl) {
        Settings config = manager.getMain().getConfig();
        QualitySettings quality = getQualitySettings();
        AudioBitrateEnum audioBitrate = quality.getAudioBitrate();

        List<String> arguments = new ArrayList<>();

        File archiveFile = downloader.getArchiveFile(typeEnum);

        switch (downloader.getDownloaderId()) {
            case YT_DLP -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.addAll(List.of(
                        "--download-archive",
                        archiveFile.getAbsolutePath()
                    ));
                }

                switch (typeEnum) {
                    case ALL -> {
                        // For backwards compatibility. This should have been a list.
                        for (String arg : config.getExtraYtDlpArguments().split(" ")) {
                            if (!arg.isEmpty()) {
                                arguments.add(arg);
                            }
                        }

                        if (config.isAutoDownloadRetry()) {
                            arguments.addAll(List.of(
                                "--fragment-retries",
                                String.valueOf(config.getMaxFragmentRetries())
                            ));
                        }

                        if (config.isRandomIntervalBetweenDownloads()) {
                            arguments.addAll(List.of(
                                "--max-sleep-interval",
                                "45",
                                "--min-sleep-interval",
                                "25"
                            ));
                        }

                        if (config.isImpersonateBrowser()) {
                            arguments.addAll(List.of(
                                "--impersonate",
                                "chrome:windows-10"
                            ));
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.addAll(List.of(
                                "--proxy", proxyUrl
                            ));
                        }

                        boolean cookiesRead = false;
                        if (config.isReadCookiesFromBrowser()) {
                            arguments.addAll(List.of(
                                "--cookies-from-browser",
                                manager.getMain().getBrowserForCookies().getName()
                            ));
                            cookiesRead = true;
                        } else {
                            File cookieJar = downloader.getCookieJarFile();
                            if (cookieJar != null) {
                                arguments.addAll(List.of(
                                    "--cookies",
                                    cookieJar.getAbsolutePath()
                                ));
                                cookiesRead = true;
                            }
                        }

                        if (cookiesRead && manager.getMain().getConfig().isMissingFormatsWorkaround()) {
                            arguments.addAll(List.of(
                                "--extractor-args",
                                "youtube:player_client=web_safari"
                            ));
                        }

                        if (GDownloader.isWindows()) {
                            // NTFS shenanigans ahead
                            // TODO: query registry for longpath support status
                            arguments.addAll(List.of(
                                "--windows-filenames",
                                "--trim-filenames",
                                String.valueOf(240)// Give some extra room for fragment files
                            ));
                        }
                    }
                    case VIDEO -> {
                        VideoContainerEnum videoContainer = quality.getVideoContainer();

                        arguments.addAll(List.of(
                            "-o",
                            savePath.getAbsolutePath() + File.separator + getVideoNamePattern(),
                            "-f",
                            getQualitySettings().buildQualitySelector(),
                            "--merge-output-format",
                            videoContainer.getValue()
                        ));

                        if (isEmbedThumbnailAndMetadata()) {
                            arguments.addAll(List.of(
                                "--embed-thumbnail",
                                "--embed-metadata",
                                "--embed-chapters"
                            ));

                            switch (quality.getVideoContainer()) {
                                case MKV, MP4, WEBM ->
                                    arguments.addAll(List.of(
                                        "--embed-subs",
                                        "--sub-langs",
                                        "all,-live_chat"
                                    ));
                            }
                        }

                        String codec = null;
                        if (quality.getAudioCodec() != AudioCodecEnum.NO_CODEC) {
                            // Check if the user has selected a custom audio codec.
                            codec = quality.getAudioCodec().getFfmpegCodecName();
                        } else if (config.isTranscodeAudioToAAC()) {
                            // If no custom codec is set, check if "Convert audio to a widely supported codec" is enabled.
                            // If enabled, default to "aac".
                            codec = "aac"; // Opus is not supported by some native video players
                        }

                        // If no codec is defined, the default audio codec provided by the source will be passed through.
                        if (codec != null) {
                            // Transcode audio (Note: This can be very slow on some machines).
                            arguments.addAll(List.of(
                                "--postprocessor-args",
                                // Use the selected codec. Default bitrate is 320 kbps unless otherwise specified.
                                "ffmpeg:-c:a " + codec + " -b:a "
                                + (audioBitrate == AudioBitrateEnum.NO_AUDIO ? 320 : audioBitrate.getValue()) + "k"
                            ));
                        }
                    }
                    case AUDIO -> {
                        if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                            String audioPatternWithBitrate = getAudioNamePattern()
                                .replace("%(audio_bitrate)s", audioBitrate.getValue() + "kbps");

                            arguments.addAll(List.of(
                                "-o",
                                savePath.getAbsolutePath() + File.separator + audioPatternWithBitrate,
                                "-f",
                                "bestaudio/worstvideo*+bestaudio/best",
                                "--extract-audio",
                                "--audio-format",
                                quality.getAudioContainer().getValue(),
                                "--audio-quality",
                                audioBitrate.getValue() + "k"
                            ));

                            if (isEmbedThumbnailAndMetadata()) {
                                arguments.addAll(List.of(
                                    "--embed-thumbnail",
                                    "--embed-metadata"
                                ));
                            }
                        }
                    }
                    case THUMBNAILS -> {
                        arguments.addAll(List.of(
                            "-o",
                            savePath.getAbsolutePath() + File.separator + getVideoNamePattern(),
                            "--write-thumbnail",
                            "--skip-download",
                            "--convert-thumbnails",
                            quality.getThumbnailContainer().getValue()
                        ));
                    }
                    case SUBTITLES -> {
                        arguments.addAll(List.of(
                            "-o",
                            savePath.getAbsolutePath() + File.separator + getVideoNamePattern(),
                            "--all-subs",
                            "--skip-download",
                            "--sub-format",
                            quality.getSubtitleContainer().getValue(),
                            "--convert-subs",
                            quality.getSubtitleContainer().getValue()
                        ));
                    }
                    default ->
                        throw new IllegalArgumentException();
                }
            }
            case GALLERY_DL -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.addAll(List.of(
                        "--download-archive",
                        archiveFile.getAbsolutePath()
                    ));
                }

                switch (typeEnum) {
                    case ALL -> {
                        arguments.addAll(List.of(
                            "--retries",
                            String.valueOf(config.getMaxDownloadRetries())
                        ));

                        if (config.isRandomIntervalBetweenDownloads()) {
                            arguments.addAll(List.of(
                                "--sleep",
                                "5.0-15.0",
                                "--sleep-request",
                                "2"
                            ));
                        }

                        if (config.isImpersonateBrowser()) {
                            arguments.addAll(List.of(
                                "--user-agent",
                                "browser"
                            ));
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.addAll(List.of(
                                "--proxy", proxyUrl
                            ));
                        }

                        if (config.isReadCookiesFromBrowser()) {
                            arguments.addAll(List.of(
                                "--cookies-from-browser",
                                manager.getMain().getBrowserForCookies().getName()
                            ));
                        } else {
                            File cookieJar = downloader.getCookieJarFile();
                            if (cookieJar != null) {
                                arguments.addAll(List.of(
                                    "--cookies",
                                    cookieJar.getAbsolutePath()
                                ));
                            }
                        }
                    }
                    case GALLERY -> {
                        String fileName = URLUtils.getDirectoryPath(inputUrl);

                        arguments.addAll(List.of(
                            "-D",
                            savePath.getAbsolutePath() + (fileName != null ? File.separator + fileName : "")
                        ));
                    }
                    default ->
                        throw new IllegalArgumentException();
                }
            }

            case SPOTDL -> {
                if (archiveFile != null && config.isRecordToDownloadArchive()) {
                    arguments.addAll(List.of(
                        "--archive",
                        archiveFile.getAbsolutePath()
                    ));
                }

                switch (typeEnum) {
                    case ALL -> {
                        arguments.addAll(List.of(
                            "--max-retries",
                            String.valueOf(config.getMaxDownloadRetries())
                        ));

                        if (config.isUseSponsorBlock()) {
                            arguments.addAll(List.of(
                                "--sponsor-block"
                            ));
                        }

                        String proxyUrl = config.getProxySettings().createProxyUrl();
                        if (proxyUrl != null) {
                            arguments.addAll(List.of(
                                "--proxy", proxyUrl
                            ));
                        }

                        File cookieJar = downloader.getCookieJarFile();
                        if (cookieJar != null) {
                            arguments.addAll(List.of(
                                "--cookie-file",
                                cookieJar.getAbsolutePath()
                            ));
                        }
                    }
                    case SPOTIFY -> {
                        if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                            String audioPatternWithBitrate = getAudioNamePattern()
                                .replace("%(audio_bitrate)s", audioBitrate.getValue() + "kbps")
                                .replace("{bitrate}", audioBitrate.getValue() + "kbps");

                            arguments.addAll(List.of(
                                "--output",
                                savePath.getAbsolutePath() + File.separator
                                + TemplateConverter.convertTemplateForSpotDL(audioPatternWithBitrate),
                                "--format",
                                quality.getAudioContainer().getValue(),
                                "--bitrate",
                                Math.clamp(audioBitrate.getValue(), 8, 320) + "k"
                            ));

                            if (!isEmbedThumbnailAndMetadata()) {
                                // SpotDL does not appear to support disabling metadata embedding.
                                // This is the best we can do to respect user settings.
                                arguments.addAll(List.of(
                                    "--skip-album-art"
                                ));
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
