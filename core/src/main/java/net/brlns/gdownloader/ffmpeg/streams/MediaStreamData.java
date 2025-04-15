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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaStreamData {

    @JsonProperty("streams")
    private List<AbstractStream> streams = new ArrayList<>();

    @JsonIgnore
    public List<VideoStream> getVideoStreams() {
        return streams.stream()
            .filter(s -> s instanceof VideoStream && !((VideoStream)s).isThumbnail())
            .map(s -> (VideoStream)s)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<VideoStream> getThumbnailStreams() {
        return streams.stream()
            .filter(s -> s instanceof VideoStream && ((VideoStream)s).isThumbnail())
            .map(s -> (VideoStream)s)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<AudioStream> getAudioStreams() {
        return streams.stream()
            .filter(s -> s instanceof AudioStream)
            .map(s -> (AudioStream)s)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<SubtitleStream> getSubtitleStreams() {
        return streams.stream()
            .filter(s -> s instanceof SubtitleStream)
            .map(s -> (SubtitleStream)s)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<DataStream> getDataStreams() {
        return streams.stream()
            .filter(s -> s instanceof DataStream)
            .map(s -> (DataStream)s)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<UnknownStream> getUnknownStreams() {
        return streams.stream()
            .filter(s -> s instanceof UnknownStream)
            .map(s -> (UnknownStream)s)
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("Streams: v=%d a=%d t=%d s=%d d=%d u=%d",
            getVideoStreams().size(),
            getAudioStreams().size(),
            getThumbnailStreams().size(),
            getSubtitleStreams().size(),
            getDataStreams().size(),
            getUnknownStreams().size());
    }
}
