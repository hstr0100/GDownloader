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
import net.brlns.gdownloader.settings.enums.BrowserEnum;
import net.brlns.gdownloader.settings.enums.LanguageEnum;
import net.brlns.gdownloader.settings.enums.PlayListOptionEnum;
import net.brlns.gdownloader.settings.enums.ThemeEnum;
import net.brlns.gdownloader.settings.enums.WebFilterEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {

    public static final int CONFIG_VERSION = 34;

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

    @JsonProperty("MissingFormatsWorkaround")
    private boolean missingFormatsWorkaround = false;

    @JsonProperty("MergeAllAudioTracks")
    private boolean mergeAllAudioTracks = false;

    @JsonProperty("KeepRawMediaFilesAfterTranscode")
    private boolean keepRawMediaFilesAfterTranscode = false;

    // Disable by default until NVENC is properly tested
    @JsonProperty("FailDownloadsOnTranscodingFailures")
    private boolean failDownloadsOnTranscodingFailures = false;

    @Deprecated
    @JsonProperty("ReadCookies")
    private boolean readCookies = false;

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

    @JsonProperty("DownloadYoutubeChannels")
    private boolean downloadYoutubeChannels = false;

    @JsonProperty("RecordToDownloadArchive")
    private boolean recordToDownloadArchive = false;

    @JsonProperty("RemoveFromDownloadArchive")
    private boolean removeFromDownloadArchive = true;

    @JsonProperty("RespectYtDlpConfigFile")
    private boolean respectYtDlpConfigFile = false;

    // TODO ui
    @JsonProperty("YtDlpTranscoding")
    private boolean ytDlpTranscoding = true;

    @JsonProperty("GalleryDlDeduplication")
    private boolean galleryDlDeduplication = true;

    @JsonProperty("GalleryDlTranscoding")
    private boolean galleryDlTranscoding = false;

    @JsonProperty("UseUploadTimeAsFileTime")
    private boolean useUploadTimeAsFileTime = true;

    @JsonProperty("GalleryDlEnabled")
    // gallery-dl appears to be unsigned
    // let's leave it off by default on Windows to avoid any possible issues with that
    private boolean galleryDlEnabled = !GDownloader.isWindows();

    @JsonProperty("SpotDLEnabled")
    private boolean spotDLEnabled = !GDownloader.isWindows();

    @JsonProperty("DirectHttpEnabled")
    private boolean directHttpEnabled = false;

    // TODO ui
    @JsonProperty("DirectHttpTranscoding")
    private boolean directHttpTranscoding = true;

    @JsonProperty("DirectHttpMaxDownloadChunks")
    private int directHttpMaxDownloadChunks = 5;

    @JsonProperty("RespectGalleryDlConfigFile")
    private boolean respectGalleryDlConfigFile = true;

    @JsonProperty("GalleryDlUseOriginalFilenames")
    private boolean GalleryDlUseOriginalFilenames = false;

    @JsonProperty("RespectSpotDLConfigFile")
    private boolean respectSpotDLConfigFile = true;

    @JsonProperty("DownloadsPath")
    private String downloadsPath = "";

    // TODO implement
    @JsonProperty("UIScale")
    private double uiScale = 1.0;

    @JsonProperty("FontSize")
    private int fontSize = 14;

    @JsonProperty("UseSystemFont")
    private boolean useSystemFont = false;

    @JsonProperty("LogMagnetLinks")
    private boolean logMagnetLinks = false;

    @JsonProperty("Theme")
    private ThemeEnum theme = ThemeEnum.DARK;

    @JsonProperty("CaptureAnyLinks")
    private boolean captureAnyLinks = false;

    @JsonProperty("EnableExtraArguments")
    private boolean enableExtraArguments = false;

    /**
     * These arguments are intended for quick, ad-hoc flags.
     * For more granular control and per-download-type arguments,
     * see {@link net.brlns.gdownloader.settings.filters.AbstractUrlFilter}
     */
    @JsonProperty("ExtraYtDlpArguments")
    private String extraYtDlpArguments = "";

    @JsonProperty("ExtraGalleryDlArguments")
    private String extraGalleryDlArguments = "";

    @JsonProperty("ExtraSpotDLArguments")
    private String extraSpotDLArguments = "";

    @JsonProperty("LastSettingsExportDirectory")
    private String lastSettingsExportDirectory = "";

    @JsonProperty("ImpersonateBrowser")
    private boolean impersonateBrowser = false;

    @JsonProperty("MaxDownloadRetries")
    private int maxDownloadRetries = 10;

    @JsonProperty("MaxFragmentRetries")
    private int maxFragmentRetries = 10;

    @JsonProperty("DownloadAudio")
    private boolean downloadAudio = true;

    @JsonProperty("DownloadVideo")
    private boolean downloadVideo = true;

    @JsonProperty("DownloadSubtitles")
    private boolean downloadSubtitles = false;

    @JsonProperty("DownloadAutoGeneratedSubtitles")
    private boolean downloadAutoGeneratedSubtitles = false;

    @JsonProperty("DownloadThumbnails")
    private boolean downloadThumbnails = false;

    @JsonProperty("AutoDownloadStart")
    private boolean autoDownloadStart = false;

    @JsonProperty("RandomIntervalBetweenDownloads")
    private boolean randomIntervalBetweenDownloads = false;

    @JsonProperty("DisplayLinkCaptureNotifications")
    private boolean displayLinkCaptureNotifications = true;

    @JsonProperty("UseSponsorBlock")
    private boolean useSponsorBlock = false;

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

    @JsonProperty("TranscodeAudioToAAC")
    private boolean transcodeAudioToAAC = true;

    // TODO add more sounds
    @JsonProperty("PlaySounds")
    private boolean playSounds = false;

    @JsonProperty("DisplayDownloadsCompleteNotification")
    private boolean displayDownloadsCompleteNotification = true;

    @JsonProperty("UseNativeSystemNotifications")
    private boolean useNativeSystemNotifications = false;

    @JsonProperty("AutoDownloadRetry")
    private boolean autoDownloadRetry = true;

    @JsonProperty("ProxySettings")
    private ProxySettings proxySettings = new ProxySettings();

    @Deprecated
    @JsonProperty("QualitySettings")
    private Map<WebFilterEnum, QualitySettings> qualitySettings = new TreeMap<>();

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

        setConfigVersion(CONFIG_VERSION);
    }
}
