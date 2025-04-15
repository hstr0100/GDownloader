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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AudioStream extends AbstractStream {

    public static final String ID = "audio";

    @JsonProperty("sample_rate")
    private String sampleRate;

    @JsonProperty("channels")
    private int channels;

    @JsonProperty("channel_layout")
    private String channelLayout;

    @JsonProperty("bit_rate")
    private String bitrate;

    public int getBitrateKbps() {
        if (!notNullOrEmpty(bitrate)) {
            try {
                long bitRate = Long.parseLong(bitrate);

                return (int)Math.round(bitRate / 1000.0);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }
}
