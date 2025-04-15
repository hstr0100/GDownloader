/*
 * Copyright (C) 2025 hstr0100
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
package net.brlns.gdownloader.ffmpeg.streams;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoStream extends AbstractStream {

    public static final String ID = "video";

    @JsonProperty("disposition")
    private Map<String, Integer> disposition = new HashMap<>();

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("pix_fmt")
    private String pixelFormat;

    @JsonProperty("bit_rate")
    private String bitrate;

    @JsonProperty("nb_frames")
    private String numberOfFrames;

    @JsonIgnore
    public boolean isThumbnail() {
        boolean hasAttachmentDisposition = getDisposition().containsKey("attached_pic")
            && getDisposition().get("attached_pic") == 1;

        return getCodecName().equals("png")
            || getCodecName().equals("jpg")
            || getCodecName().equals("jpeg")
            || getCodecName().equals("mjpeg") && hasAttachmentDisposition
            || getCodecName().equals("webp");
    }
}
