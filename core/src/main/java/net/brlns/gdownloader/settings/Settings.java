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
package net.brlns.gdownloader.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ffmpeg.enums.AudioCodecEnum;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.filters.GenericFilter;
import net.brlns.gdownloader.settings.downloader.*;
import net.brlns.gdownloader.settings.enums.BrowserEnum;
import net.brlns.gdownloader.settings.enums.LanguageEnum;
import net.brlns.gdownloader.settings.enums.PlayListOptionEnum;
import net.brlns.gdownloader.settings.enums.ThemeEnum;
import net.brlns.gdownloader.settings.enums.WebFilterEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {

    public static final int CONFIG_VERSION = 37;

    @JsonProperty("ConfigVersion")
    private int configVersion = CONFIG_VERSION;

    @JsonProperty("PersistenceDatabaseInitialized")
    private boolean persistenceDatabaseInitialized = false;

    @JsonProperty("ShowWelcomeScreen")
    private boolean showWelcomeScreen = true;

    @JsonProperty("MonitorClipboardForLinks")
    private boolean monitorClipboardForLinks = true;

    @JsonProperty("AutomaticUpdates")
    // With websites constantly changing, automatically enabling updates by default, at least for Windows, is the most sensible option.
    // If this program is meant to follow its idea of 'User Friendly even your grandma can use it', placing dialogs and barriers on top
    // of essentially required updates completely breaks that premise.
    //
    // Users can still toggle it off right at the welcome screen if they prefer to manage updates manually.
    private boolean automaticUpdates = GDownloader.isWindows();

    @JsonProperty("LanguageDefined")
    private boolean languageDefined = false;

    @JsonProperty("PreferSystemExecutables")
    private boolean preferSystemExecutables = false;

    @JsonProperty("Language")
    private LanguageEnum language = LanguageEnum.ENGLISH;

    @JsonProperty("AutoScrollToBottom")
    private boolean autoScrollToBottom = true;

    @JsonProperty("RestoreSessionAfterRestart")
    private boolean restoreSessionAfterRestart = true;

    @JsonProperty("KeepRawMediaFilesAfterTranscode")
    private boolean keepRawMediaFilesAfterTranscode = false;

    // Disable by default until NVENC is properly tested
    @JsonProperty("FailDownloadsOnTranscodingFailures")
    private boolean failDownloadsOnTranscodingFailures = false;

    @JsonProperty("ReadCookiesFromBrowser")
    private boolean readCookiesFromBrowser = false;

    @JsonProperty("ReadCookiesFromCookiesTxt")
    private boolean readCookiesFromCookiesTxt = true;

    @JsonProperty("BrowserForCookies")
    private BrowserEnum browser = BrowserEnum.UNSET;

    @JsonProperty("RemoveSuccessfulDownloads")
    private boolean removeSuccessfulDownloads = false;

    @JsonProperty("QueryMetadata")
    private boolean queryMetadata = true;

    @JsonProperty("MaxSimultaneousQueryMetadataTasks")
    private int maxSimultaneousQueryMetadataTasks = 2;

    @JsonProperty("RecordToDownloadArchive")
    private boolean recordToDownloadArchive = false;

    @JsonProperty("RemoveFromDownloadArchive")
    private boolean removeFromDownloadArchive = true;

    @JsonProperty("UseUploadTimeAsFileTime")
    private boolean useUploadTimeAsFileTime = true;

    @JsonProperty("YtDlpSettings")
    private YtDlpSettings ytDlpSettings = new YtDlpSettings();

    @JsonProperty("GalleryDLSettings")
    private GalleryDLSettings galleryDLSettings = new GalleryDLSettings();

    @JsonProperty("SpotDLSettings")
    private SpotDLSettings spotDLSettings = new SpotDLSettings();

    @JsonProperty("DirectHttpSettings")
    private DirectHttpSettings directHttpSettings = new DirectHttpSettings();

    @JsonProperty("DownloadsPath")
    private String downloadsPath = "";

    // Java only honors whole multiples (100/200/300...), anything else gets rounded.
    @JsonProperty("UIScalePercentage")
    private int uiScalePercentage = 100;

    @JsonProperty("FontSize")
    private int fontSize = 14;

    @JsonProperty("UseSystemFont")
    private boolean useSystemFont = false;

    @JsonProperty("LogMagnetLinks")
    private boolean logMagnetLinks = false;

    // Only editable via config file as this can and will implode the UI on certain systems.
    @JsonProperty("HardwareAcceleratedUI")
    private boolean hardwareAcceleratedUI = false;

    @JsonProperty("Theme")
    private ThemeEnum theme = ThemeEnum.DARK;

    @JsonProperty("CaptureAnyLinks")
    private boolean captureAnyLinks = false;

    @JsonProperty("LastSettingsExportDirectory")
    private String lastSettingsExportDirectory = "";

    @JsonProperty("ImpersonateBrowser")
    private boolean impersonateBrowser = false;

    @JsonProperty("MaxDownloadRetries")
    private int maxDownloadRetries = 10;

    @JsonProperty("MaxFragmentRetries")
    private int maxFragmentRetries = 10;

    @JsonProperty("MaxDownloadQueueColumns")
    private int maxDownloadQueueColumns = 0;

    @JsonProperty("DownloadAudio")
    private boolean downloadAudio = true;

    @JsonProperty("DownloadVideo")
    private boolean downloadVideo = true;

    @JsonProperty("AutoDownloadStart")
    private boolean autoDownloadStart = false;

    @JsonProperty("EnableSystemTray")
    private boolean enableSystemTray = true;

    // TODO: configure ranges
    @JsonProperty("RandomIntervalBetweenDownloads")
    private boolean randomIntervalBetweenDownloads = false;

    @JsonProperty("DisplayLinkCaptureNotifications")
    private boolean displayLinkCaptureNotifications = true;

    @JsonProperty("KeepWindowAlwaysOnTop")
    private boolean keepWindowAlwaysOnTop = false;

    @JsonProperty("MaximumSimultaneousDownloads")
    private int maxSimultaneousDownloads = 3;

    @JsonProperty("PlaylistDownloadOption")
    private PlayListOptionEnum playlistDownloadOption = PlayListOptionEnum.ALWAYS_ASK;

    @JsonProperty("DebugMode")
    private boolean debugMode = false;

    @JsonProperty("AutoStart")
    private boolean autoStart = false;

    @JsonProperty("ExitOnClose")
    private boolean exitOnClose = true;

    // Defaults to true because most users are likely running Windows,
    // which does not include Opus audio codec support by default.
    // Without transcoding, youtube videos will play without audio unless the user
    // installs a third-party media player or the required codecs.
    @JsonProperty("TranscodeAudioToAAC")
    private boolean transcodeAudioToAAC = true;

    @JsonProperty("DisableAACPns")
    private boolean disableAACPns = false;

    // TODO add more sounds
    @JsonProperty("PlaySounds")
    private boolean playSounds = true;

    @JsonProperty("DisplayDownloadsCompleteNotification")
    private boolean displayDownloadsCompleteNotification = true;

    @JsonProperty("UseNativeSystemNotifications")
    private boolean useNativeSystemNotifications = false;

    @JsonProperty("AutoDownloadRetry")
    private boolean autoDownloadRetry = true;

    // TODO: wire up, dynamically throttle downloaders when starting new tasks based on best-effort bandwidth allocation stategy.
    // we can calculate this based on max simultaneous downloads, or active downloads if the queue processor is MIA.
    @JsonProperty("GlobalMaxDownloadSpeedBytesPerSecond")
    private long globalMaxDownloadSpeedBytesPerSecond = 0l;

    @JsonProperty("ProxySettings")
    private ProxySettings proxySettings = new ProxySettings();

    @JsonProperty("UrlFilters")
    private List<AbstractUrlFilter> urlFilters = new ArrayList<>();

    @JsonProperty("GlobalQualitySettings")
    private QualitySettings globalQualitySettings = QualitySettings.builder().build();

    public Settings() {
        urlFilters.addAll(AbstractUrlFilter.getDefaultUrlFilters());
    }

    @JsonIgnore
    public Optional<AbstractUrlFilter> getUrlFilterById(String filterId) {
        return urlFilters.stream()
            .filter(savedFilter -> savedFilter.getId().equals(filterId))
            .findFirst();
    }

    @JsonIgnore
    @SuppressWarnings("deprecation")
    public void doMigration() {
        List<AbstractUrlFilter> defaultFilters = AbstractUrlFilter.getDefaultUrlFilters();

        if (urlFilters.isEmpty()) {
            urlFilters.addAll(defaultFilters);
        } else {
            defaultFilters.stream()
                .filter(
                    filter -> urlFilters.stream()
                        .noneMatch(savedFilter -> savedFilter.getId().equals(filter.getId()))
                )
                .forEach(urlFilters::add);
        }

        urlFilters.removeIf(savedFilter
            -> savedFilter.getId().equals(GenericFilter.ID)
            && (!savedFilter.getUrlRegex().isEmpty() || !savedFilter.getFilterName().isEmpty()));

        for (Map.Entry<WebFilterEnum, QualitySettings> entry : qualitySettings.entrySet()) {
            WebFilterEnum key = entry.getKey();
            QualitySettings value = entry.getValue();

            urlFilters.stream()
                .filter(filter -> filter.getId().equals(key.getId()))
                .forEach(filter -> {
                    filter.setQualitySettings(value);
                    log.info("Migrated {} -> {}", key, value);
                });
        }

        qualitySettings.clear();

        // Broken in Chromium
        // https://github.com/yt-dlp/yt-dlp/issues/7271
        // https://github.com/yt-dlp/yt-dlp/issues/10927
        if (isReadCookies() && getBrowser() == BrowserEnum.FIREFOX) {
            setReadCookiesFromBrowser(true);
            setReadCookies(false);
        }

        for (AbstractUrlFilter filter : urlFilters) {
            QualitySettings qSettings = filter.getQualitySettings();
            FFmpegConfig ffmpegConfig = qSettings.getTranscodingSettings();
            if (qSettings.getAudioCodec() != AudioCodecEnum.NO_CODEC) {
                ffmpegConfig.setAudioCodec(qSettings.getAudioCodec());
                ffmpegConfig.setAudioBitrate(qSettings.getAudioBitrate());

                qSettings.setEnableTranscoding(true);
                qSettings.setAudioCodec(AudioCodecEnum.NO_CODEC);
            }
        }

        // Reset this suboptimal default if launching from an ancient version.
        // Old launchers aren't aware of our current single-instance mode.
        if (!isExitOnClose() && getConfigVersion() <= 20) {
            setExitOnClose(true);
        }

        if (!getExtraYtDlpArguments().isEmpty() && getConfigVersion() < 33) {
            setEnableExtraArguments(true);
        }

        if (isShowWelcomeScreen() && getConfigVersion() < 34) {
            setShowWelcomeScreen(false);
        }

        if (getConfigVersion() < 35) {
            for (AbstractUrlFilter filter : getUrlFilters()) {
                filter.setEmbedMetadata(filter.isEmbedThumbnailAndMetadata());
                filter.setEmbedSubtitles(filter.isEmbedThumbnailAndMetadata());
                filter.setEmbedThumbnail(filter.isEmbedThumbnailAndMetadata());
            }
        }

        if (getConfigVersion() < 36) {
            directHttpSettings.setEnabled(isDirectHttpEnabled());
            directHttpSettings.setMediaTranscoding(isDirectHttpTranscoding());
            directHttpSettings.setMaxDownloadChunks(getDirectHttpMaxDownloadChunks());

            galleryDLSettings.setEnabled(isGalleryDlEnabled());
            galleryDLSettings.setFileDeduplication(isGalleryDlDeduplication());
            galleryDLSettings.setMediaTranscoding(isGalleryDlTranscoding());
            galleryDLSettings.setRespectConfigFile(isRespectGalleryDlConfigFile());
            galleryDLSettings.setUseOriginalFilenames(isGalleryDlUseOriginalFilenames());
            galleryDLSettings.setExtraCommandLineArguments(getExtraGalleryDlArguments());

            ytDlpSettings.setExtraCommandLineArguments(getExtraYtDlpArguments());
            ytDlpSettings.setRespectConfigFile(isRespectYtDlpConfigFile());
            ytDlpSettings.setMissingFormatsWorkaround(isMissingFormatsWorkaround());
            ytDlpSettings.setMergeAllAudioTracks(isMergeAllAudioTracks());
            ytDlpSettings.setDownloadYoutubeChannels(isDownloadYoutubeChannels());
            ytDlpSettings.setMediaTranscoding(isYtDlpTranscoding());
            ytDlpSettings.setUseSponsorBlock(isUseSponsorBlock());
            ytDlpSettings.setDownloadSubtitles(isDownloadSubtitles());
            ytDlpSettings.setDownloadAutoGeneratedSubtitles(isDownloadAutoGeneratedSubtitles());
            ytDlpSettings.setDownloadThumbnails(isDownloadThumbnails());
            ytDlpSettings.setDownloadDescription(isDownloadDescription());
            ytDlpSettings.setSaveDescriptionFileAsTxt(isSaveDescriptionFileAsTxt());

            spotDLSettings.setEnabled(isSpotDLEnabled());
            spotDLSettings.setExtraCommandLineArguments(getExtraSpotDLArguments());
            spotDLSettings.setRespectConfigFile(isRespectSpotDLConfigFile());
        }

        if (getConfigVersion() < 37) {
            boolean legacyGlobalToggle = isEnableExtraArguments();

            ytDlpSettings.setEnableExtraArguments(
                legacyGlobalToggle && !ytDlpSettings.getExtraCommandLineArguments().isEmpty());
            galleryDLSettings.setEnableExtraArguments(
                legacyGlobalToggle && !galleryDLSettings.getExtraCommandLineArguments().isEmpty());
            spotDLSettings.setEnableExtraArguments(
                legacyGlobalToggle && !spotDLSettings.getExtraCommandLineArguments().isEmpty());
        }

        setConfigVersion(CONFIG_VERSION);
    }

    // Graveyard Area
    @Deprecated
    @JsonProperty(value = "EnableExtraArguments", access = JsonProperty.Access.WRITE_ONLY)
    private boolean enableExtraArguments = false;

    @Deprecated
    @JsonProperty(value = "DownloadSubtitles", access = JsonProperty.Access.WRITE_ONLY)
    private boolean downloadSubtitles = false;

    @Deprecated
    @JsonProperty(value = "DownloadAutoGeneratedSubtitles", access = JsonProperty.Access.WRITE_ONLY)
    private boolean downloadAutoGeneratedSubtitles = false;

    @Deprecated
    @JsonProperty(value = "DownloadThumbnails", access = JsonProperty.Access.WRITE_ONLY)
    private boolean downloadThumbnails = false;

    @Deprecated
    @JsonProperty(value = "DownloadDescription", access = JsonProperty.Access.WRITE_ONLY)
    private boolean downloadDescription = false;

    @Deprecated
    @JsonProperty(value = "SaveDescriptionFileAsTxt", access = JsonProperty.Access.WRITE_ONLY)
    private boolean saveDescriptionFileAsTxt = true;

    @Deprecated
    @JsonProperty(value = "MissingFormatsWorkaround", access = JsonProperty.Access.WRITE_ONLY)
    private boolean missingFormatsWorkaround = false;

    @Deprecated
    @JsonProperty(value = "MergeAllAudioTracks", access = JsonProperty.Access.WRITE_ONLY)
    private boolean mergeAllAudioTracks = false;

    @Deprecated
    @JsonProperty(value = "DownloadYoutubeChannels", access = JsonProperty.Access.WRITE_ONLY)
    private boolean downloadYoutubeChannels = false;

    @Deprecated
    @JsonProperty(value = "YtDlpTranscoding", access = JsonProperty.Access.WRITE_ONLY)
    private boolean ytDlpTranscoding = true;

    @Deprecated
    @JsonProperty(value = "UseSponsorBlock", access = JsonProperty.Access.WRITE_ONLY)
    private boolean useSponsorBlock = false;

    @Deprecated
    @JsonProperty(value = "RespectYtDlpConfigFile", access = JsonProperty.Access.WRITE_ONLY)
    private boolean respectYtDlpConfigFile = false;

    @Deprecated
    @JsonProperty(value = "RespectSpotDLConfigFile", access = JsonProperty.Access.WRITE_ONLY)
    private boolean respectSpotDLConfigFile = true;

    @Deprecated
    @JsonProperty(value = "SpotDLEnabled", access = JsonProperty.Access.WRITE_ONLY)
    private boolean spotDLEnabled = !GDownloader.isWindows();

    @Deprecated
    @JsonProperty(value = "ExtraYtDlpArguments", access = JsonProperty.Access.WRITE_ONLY)
    private String extraYtDlpArguments = "";

    @Deprecated
    @JsonProperty(value = "ExtraGalleryDlArguments", access = JsonProperty.Access.WRITE_ONLY)
    private String extraGalleryDlArguments = "";

    @Deprecated
    @JsonProperty(value = "ExtraSpotDLArguments", access = JsonProperty.Access.WRITE_ONLY)
    private String extraSpotDLArguments = "";

    @Deprecated
    @JsonProperty(value = "GalleryDlDeduplication", access = JsonProperty.Access.WRITE_ONLY)
    private boolean galleryDlDeduplication = true;

    @Deprecated
    @JsonProperty(value = "GalleryDlTranscoding", access = JsonProperty.Access.WRITE_ONLY)
    private boolean galleryDlTranscoding = false;

    @Deprecated
    @JsonProperty(value = "GalleryDlEnabled", access = JsonProperty.Access.WRITE_ONLY)
    private boolean galleryDlEnabled = !GDownloader.isWindows();

    @Deprecated
    @JsonProperty(value = "RespectGalleryDlConfigFile", access = JsonProperty.Access.WRITE_ONLY)
    private boolean respectGalleryDlConfigFile = true;

    @Deprecated
    @JsonProperty(value = "GalleryDlUseOriginalFilenames", access = JsonProperty.Access.WRITE_ONLY)
    private boolean galleryDlUseOriginalFilenames = false;

    @Deprecated
    @JsonProperty(value = "DirectHttpEnabled", access = JsonProperty.Access.WRITE_ONLY)
    private boolean directHttpEnabled = false;

    @Deprecated
    @JsonProperty(value = "DirectHttpTranscoding", access = JsonProperty.Access.WRITE_ONLY)
    private boolean directHttpTranscoding = true;

    @Deprecated
    @JsonProperty(value = "DirectHttpMaxDownloadChunks", access = JsonProperty.Access.WRITE_ONLY)
    private int directHttpMaxDownloadChunks = 5;

    @Deprecated
    @JsonProperty(value = "ReadCookies", access = JsonProperty.Access.WRITE_ONLY)
    private boolean readCookies = false;

    @Deprecated
    @JsonProperty(value = "QualitySettings", access = JsonProperty.Access.WRITE_ONLY)
    private Map<WebFilterEnum, QualitySettings> qualitySettings = new TreeMap<>();
}
