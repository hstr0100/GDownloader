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
package net.brlns.gdownloader.settings.downloader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CyberdropDLSettings extends AbstractDownloaderSettings {

    @JsonProperty("Enabled")
    private boolean enabled = false;

    @JsonProperty("PreferSystemExecutable")
    private boolean preferSystemExecutable = false;

    @JsonProperty("FileDeduplication")
    private boolean fileDeduplication = true;

    @JsonProperty("MediaTranscoding")
    private boolean mediaTranscoding = false;

    @JsonProperty("FlattenOutput")
    private boolean flattenOutput = false;

    @JsonProperty("RespectConfigFile")
    private boolean respectConfigFile = true;

    @JsonProperty("ExtraCommandLineArguments")
    private String extraCommandLineArguments = "";

}
