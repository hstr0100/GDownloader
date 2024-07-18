package net.brlns.gdownloader.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import net.brlns.gdownloader.settings.enums.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings{

    @JsonProperty("QualitySettings")
    private Map<WebFilterEnum, QualitySettings> qualitySettings = new HashMap<>();

    public Settings(){
        for(WebFilterEnum filter : WebFilterEnum.values()){
            QualitySettings quality;

            if(filter == WebFilterEnum.TWITCH){
                quality = QualitySettings.builder()
                    .selector(QualitySelectorEnum.WORST)
                    .minHeight(ResolutionEnum.RES_480)
                    .maxHeight(ResolutionEnum.RES_720)
                    .build();
            }else{
                quality = QualitySettings.builder().build();
            }

            qualitySettings.put(filter, quality);
        }
    }

    @JsonProperty("Language")
    private LanguageEnum language = LanguageEnum.ENGLISH;

    @JsonProperty("ReadCookies")
    private boolean readCookies = true;

    @JsonProperty("BrowserForCookies")
    private BrowserEnum browser = BrowserEnum.UNSET;

    @JsonProperty("DownloadsPath")
    private String downloadsPath = "";

    @JsonProperty("CaptureAnyLinks")
    private boolean captureAnyLinks = false;

    @JsonProperty("DownloadAudioOnly")
    private boolean downloadAudioOnly = false;

    //TODO: need to refresh ui for this one
    @JsonProperty("KeepWindowAlwaysOnTop")
    private boolean keepWindowAlwaysOnTop = false;

    @JsonProperty("MaximumSimultaneousDownloads")
    private int maxSimultaneousDownloads = 3;

    @JsonProperty("PlaylistDownloadOption")
    private PlayListOptionEnum playlistDownloadOption = PlayListOptionEnum.ALWAYS_ASK;

    @JsonProperty("DebugMode")
    private boolean debugMode = true;

    @JsonProperty("AutoStart")
    private boolean autoStart = false;

    @JsonProperty("ExitOnClose")
    private boolean exitOnClose = false;

    @JsonProperty("Play Sounds")
    private boolean playSounds = false;

}
