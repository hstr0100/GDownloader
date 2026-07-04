/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.downloader.structs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormatInfo {

    @JsonProperty("format_id")
    private String formatId;
    @JsonProperty("ext")
    private String ext;
    @JsonProperty("resolution")
    private String resolution;
    @JsonProperty("format_note")
    private String formatNote;
    @JsonProperty("vcodec")
    private String vcodec;
    @JsonProperty("acodec")
    private String acodec;
    @JsonProperty("fps")
    private Double fps;
    @JsonProperty("tbr")
    private Double tbr;
    @JsonProperty("vbr")
    private Double vbr;
    @JsonProperty("abr")
    private Double abr;
    @JsonProperty("filesize")
    private Long filesize;
    @JsonProperty("filesize_approx")
    private Long filesizeApprox;
    @JsonProperty("format")
    private String format;

    @JsonIgnore
    public boolean isVideoOnly() {
        return notNullOrEmpty(vcodec) && !"none".equals(vcodec)
            && (nullOrEmpty(acodec) || "none".equals(acodec));
    }

    @JsonIgnore
    public boolean isAudioOnly() {
        return notNullOrEmpty(acodec) && !"none".equals(acodec)
            && (nullOrEmpty(vcodec) || "none".equals(vcodec));
    }

    @JsonIgnore
    public boolean isStoryboard() {
        return "mhtml".equals(ext);
    }
}
