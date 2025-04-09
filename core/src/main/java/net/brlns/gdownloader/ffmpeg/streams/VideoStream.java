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

import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

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

    @JsonProperty("duration")
    private String duration;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("time_base")
    private String timeBase;

    @JsonProperty("duration_ts")
    private Long durationTs;

    @JsonProperty("bit_rate")
    private String bitrate;

    @JsonProperty("nb_frames")
    private String numberOfFrames;

    @JsonIgnore
    public double getDuration() {
        if (notNullOrEmpty(duration)) {
            try {
                return Double.parseDouble(duration);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }

    // TODO test
    @JsonIgnore
    public Long getDurationTimestanp() {
        if (notNullOrEmpty(timeBase) && durationTs != null) {
            try {
                String[] parts = timeBase.split("/");
                int numerator = Integer.parseInt(parts[0]);
                int denominator = Integer.parseInt(parts[1]);

                double seconds = (double)durationTs * numerator / denominator;
                long milliseconds = Math.round(seconds * 1000);

                return milliseconds;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1l;
    }

    @JsonIgnore
    public int getFrameCount() {
        if (notNullOrEmpty(numberOfFrames)) {
            try {
                return Integer.parseInt(numberOfFrames);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }

    @JsonIgnore
    public boolean isThumbnail() {
        return getCodecName().equals("png")
            || getCodecName().equals("jpg")
            || getCodecName().equals("jpeg")
            || getCodecName().equals("mjpeg")// TODO: check if frame count matches 1
            || getCodecName().equals("webp");
    }
}
