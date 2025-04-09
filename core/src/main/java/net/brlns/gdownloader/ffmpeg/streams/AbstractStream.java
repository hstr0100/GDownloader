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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "codec_type",
    defaultImpl = UnknownStream.class,
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AudioStream.class, name = AudioStream.ID),
    @JsonSubTypes.Type(value = DataStream.class, name = DataStream.ID),
    @JsonSubTypes.Type(value = SubtitleStream.class, name = SubtitleStream.ID),
    @JsonSubTypes.Type(value = VideoStream.class, name = VideoStream.ID)
})
public abstract class AbstractStream {

    @JsonProperty("codec_type")
    private String codecType;

    @JsonProperty("index")
    private int index;

    @JsonProperty("codec_name")
    private String codecName;

    @JsonProperty("tags")
    private Map<String, String> tags = new HashMap<>();

    @JsonIgnore
    @Nullable
    public String getLanguage() {
        return tags.get("language");
    }
}
