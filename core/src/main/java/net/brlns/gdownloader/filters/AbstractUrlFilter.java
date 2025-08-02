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
package net.brlns.gdownloader.filters;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.lang.ITranslatable;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "Id",
    defaultImpl = GenericFilter.class,
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = YoutubeFilter.class, name = YoutubeFilter.ID),
    @JsonSubTypes.Type(value = YoutubePlaylistFilter.class, name = YoutubePlaylistFilter.ID),

    @JsonSubTypes.Type(value = BiliBiliFilter.class, name = BiliBiliFilter.ID),
    @JsonSubTypes.Type(value = CrunchyrollFilter.class, name = CrunchyrollFilter.ID),
    @JsonSubTypes.Type(value = DailymotionFilter.class, name = DailymotionFilter.ID),
    @JsonSubTypes.Type(value = DropoutFilter.class, name = DropoutFilter.ID),
    @JsonSubTypes.Type(value = FacebookFilter.class, name = FacebookFilter.ID),
    @JsonSubTypes.Type(value = ImgurFilter.class, name = ImgurFilter.ID),
    @JsonSubTypes.Type(value = PatreonFilter.class, name = PatreonFilter.ID),
    @JsonSubTypes.Type(value = RedditFilter.class, name = RedditFilter.ID),
    @JsonSubTypes.Type(value = SpotifyFilter.class, name = SpotifyFilter.ID),
    @JsonSubTypes.Type(value = TwitchFilter.class, name = TwitchFilter.ID),
    @JsonSubTypes.Type(value = VimeoFilter.class, name = VimeoFilter.ID),
    @JsonSubTypes.Type(value = XFilter.class, name = XFilter.ID),

    @JsonSubTypes.Type(value = GenericFilter.class, name = GenericFilter.ID)
})
public abstract class AbstractUrlFilter implements ITranslatable {

    @JsonIgnore
    private static final List<Class<?>> DEFAULTS = new ArrayList<>();

    static {
        JsonSubTypes jsonSubTypes = AbstractUrlFilter.class.getAnnotation(JsonSubTypes.class);

        if (jsonSubTypes != null) {
            JsonSubTypes.Type[] types = jsonSubTypes.value();

            for (JsonSubTypes.Type type : types) {
                DEFAULTS.add(type.value());
            }
        }
    }

    @JsonIgnore
    public static List<AbstractUrlFilter> getDefaultUrlFilters() {
        List<AbstractUrlFilter> filters = new ArrayList<>();

        for (Class<?> filterClass : DEFAULTS) {
            try {
                AbstractUrlFilter filter = (AbstractUrlFilter)filterClass.getDeclaredConstructor().newInstance();

                filters.add(filter);
            } catch (Exception e) {
                log.error("Error instantiating class.", e);
            }
        }

        return filters;
    }

    @JsonProperty("Id")
    private String id = "";

    @JsonProperty("FilterName")
    private String filterName = "";

    @JsonProperty("UrlRegex")
    private String urlRegex = "";

    @JsonProperty("AudioOnly")
    private boolean audioOnly = false;

    @JsonProperty("VideoOnly")
    private boolean videoOnly = false;

    @JsonProperty("VideoNamePattern")
    private String videoNamePattern = "";

    @JsonProperty("AudioNamePattern")
    private String audioNamePattern = "";

    @JsonProperty("EmbedThumbnailAndMetadata")
    private boolean embedThumbnailAndMetadata = false;

    @JsonProperty("CanTranscodeVideo")
    private boolean canTranscodeVideo = true;

    /**
     * Represents a set of extra arguments for yt-dlp.
     * These arguments are categorized based on the type of download (e.g., VIDEO, AUDIO, SUBTITLES, etc.).
     * Arguments in the ALL category apply to all categories that depend on this filter.
     *
     * JSON schema:
     *
     * <pre>
     * "ExtraYtDlpArguments" : {
     *   "ALL": [
     *     "--ignore-config",
     *     "--proxy",
     *     "http://example.com:1234",
     *     "--skip-download"
     *   ],
     *   "VIDEO": [
     *     "--no-playlist"
     *   ],
     *   "AUDIO": [],
     *   "SUBTITLES" : [],
     *   "THUMBNAILS" : []
     * }
     * </pre>
     */
    @JsonProperty("ExtraYtDlpArguments")
    private Map<DownloadTypeEnum, List<String>> extraYtDlpArguments = new HashMap<>();

    /**
     * Represents a set of extra arguments for gallery-dl.
     * These arguments are categorized based on the type of download (e.g. GALLERY).
     * Arguments in the ALL category apply to all categories that depend on this filter.
     *
     * JSON schema:
     *
     * <pre>
     * "ExtraGalleryDlArguments" : {
     *   "ALL": [
     *     "--config-ignore",
     *     "--proxy",
     *     "http://example.com:1234"
     *   ],
     *   "GALLERY": [
     *     "--no-colors"
     *   ]
     * }
     * </pre>
     */
    @JsonProperty("ExtraGalleryDlArguments")
    private Map<DownloadTypeEnum, List<String>> extraGalleryDlArguments = new HashMap<>();

    /**
     * Represents a set of extra arguments for spotDL.
     * These arguments are categorized based on the type of download (e.g. SPOTIFY).
     * Arguments in the ALL category apply to all categories that depend on this filter.
     *
     * JSON schema:
     *
     * <pre>
     * "ExtraSpotDLArguments" : {
     *   "ALL": [
     *     "--proxy",
     *     "http://example.com:1234"
     *   ],
     *   "SPOTIFY": [
     *     "--example"
     *   ]
     * }
     * </pre>
     */
    @JsonProperty("ExtraSpotDLArguments")
    private Map<DownloadTypeEnum, List<String>> extraSpotDLArguments = new HashMap<>();

    @JsonProperty("QualitySettings")
    private QualitySettings qualitySettings = QualitySettings.builder().build();

    @JsonIgnore
    public QualitySettings getActiveQualitySettings(Settings config) {
        if (qualitySettings.isUseGlobalSettings()) {
            return config.getGlobalQualitySettings();
        }

        return qualitySettings;
    }

    public AbstractUrlFilter() {
        extraYtDlpArguments.put(DownloadTypeEnum.ALL, new ArrayList<>());
        for (DownloadTypeEnum downloadType : DownloadTypeEnum.getForDownloaderId(DownloaderIdEnum.YT_DLP)) {
            extraYtDlpArguments.put(downloadType, new ArrayList<>());
        }

        extraGalleryDlArguments.put(DownloadTypeEnum.ALL, new ArrayList<>());
        for (DownloadTypeEnum downloadType : DownloadTypeEnum.getForDownloaderId(DownloaderIdEnum.GALLERY_DL)) {
            extraGalleryDlArguments.put(downloadType, new ArrayList<>());
        }

        extraSpotDLArguments.put(DownloadTypeEnum.ALL, new ArrayList<>());
        for (DownloadTypeEnum downloadType : DownloadTypeEnum.getForDownloaderId(DownloaderIdEnum.SPOTDL)) {
            extraSpotDLArguments.put(downloadType, new ArrayList<>());
        }
    }

    @JsonIgnore
    @Override
    public String getDisplayName() {
        String name = getFilterName();
        if (name.isEmpty()) {
            log.error("Filter name was empty for class: {}", getClass());
        }

        return name;
    }

    @JsonIgnore
    private Pattern _cachedPattern;

    @JsonIgnore
    public boolean matches(String url) {
        if (urlRegex.isEmpty()) {
            return false;
        }

        if (_cachedPattern == null) {
            _cachedPattern = Pattern.compile(urlRegex);
        }

        return _cachedPattern.matcher(url).matches();
    }

    @JsonIgnore
    public ProcessArguments getArguments(AbstractDownloader downloader, DownloadTypeEnum typeEnum, DownloadManager manager, File savePath, String inputUrl) {
        ProcessArguments arguments = new ProcessArguments(
            buildArguments(downloader, typeEnum, manager, savePath, inputUrl));

        // TODO: Map<DonwloaderIdEnum, Map<DownloadTypeEnum, List<String>>> or a struct extending that.
        switch (downloader.getDownloaderId()) {
            case YT_DLP -> {
                if (extraYtDlpArguments.containsKey(typeEnum)) {
                    arguments.addAll(extraYtDlpArguments.get(typeEnum));
                }
            }
            case GALLERY_DL -> {
                if (extraGalleryDlArguments.containsKey(typeEnum)) {
                    arguments.addAll(extraGalleryDlArguments.get(typeEnum));
                }
            }
            case SPOTDL -> {
                if (extraSpotDLArguments.containsKey(typeEnum)) {
                    arguments.addAll(extraSpotDLArguments.get(typeEnum));
                }
            }
            default -> {
                log.warn("Unhandled downloader id {}", downloader.getDownloaderId());
            }
        }

        return arguments;
    }

    @JsonIgnore
    protected abstract ProcessArguments buildArguments(AbstractDownloader downloader, DownloadTypeEnum typeEnum, DownloadManager manager, File savePath, String inputUrl);

    @JsonIgnore
    public abstract boolean areCookiesRequired();

    @JsonIgnore
    public abstract boolean canAcceptUrl(String url, GDownloader main);

}
