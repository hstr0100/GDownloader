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
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotDLSettings extends AbstractDownloaderSettings {

    @JsonProperty("Enabled")
    private boolean enabled = !GDownloader.isWindows();

    @JsonProperty("PreferSystemExecutable")
    private boolean preferSystemExecutable = false;

    @JsonProperty("RespectConfigFile")
    private boolean respectConfigFile = true;

    /**
     * These arguments are intended for quick, ad-hoc flags.For more granular control and per-download-type arguments,
     * see {@link net.brlns.gdownloader.filters.AbstractUrlFilter}
     */
    @JsonProperty("ExtraCommandLineArguments")
    private String extraCommandLineArguments = "";
}
