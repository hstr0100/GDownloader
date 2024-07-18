package net.brlns.gdownloader.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.settings.enums.AudioBitrateEnum;
import net.brlns.gdownloader.settings.enums.FPSEnum;
import net.brlns.gdownloader.settings.enums.QualitySelectorEnum;
import net.brlns.gdownloader.settings.enums.ResolutionEnum;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QualitySettings{

    @Builder.Default
    @JsonProperty("QualitySelector")
    private QualitySelectorEnum selector = QualitySelectorEnum.BEST_VIDEO;

    @Builder.Default
    @JsonProperty("Container")
    private VideoContainerEnum container = VideoContainerEnum.MP4;

    @Builder.Default
    @JsonProperty("MinHeight")
    private ResolutionEnum minHeight = ResolutionEnum.RES_144;

    @Builder.Default
    @JsonProperty("MaxHeight")
    private ResolutionEnum maxHeight = ResolutionEnum.RES_1080;

    @Builder.Default
    @JsonProperty("FPS")
    private FPSEnum fps = FPSEnum.FPS_30;

    @Builder.Default
    @JsonProperty("AudioBitrate")
    private AudioBitrateEnum audioBitrate = AudioBitrateEnum.BITRATE_320;

    @JsonIgnore
    public String getQualitySettings(){
        return "(" + selector.getValue() + "[height>=" + minHeight.getValue() + "][height<=" + maxHeight.getValue() + "][ext=" + container.getValue() + "][fps=" + fps.getValue() + "]+bestaudio/"
            + selector.getValue() + "[height>=" + minHeight.getValue() + "][height<=" + maxHeight.getValue() + "][ext=" + container.getValue() + "]+bestaudio/"
            + selector.getValue() + "[ext=" + container.getValue() + "]+bestaudio/"
            + selector.getValue() + "+bestaudio/best)";
    }

    //TODO
    @JsonIgnore
    public String getTranscodingOptions(){
        return "res:" + maxHeight.getValue() + ",fps,ext,codec:vp9.2";
    }
}
