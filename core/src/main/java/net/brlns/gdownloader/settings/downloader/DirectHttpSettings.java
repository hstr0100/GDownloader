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
public class DirectHttpSettings extends AbstractDownloaderSettings {

    @JsonProperty("Enabled")
    private boolean enabled = true;

    @JsonProperty("MediaTranscoding")
    private boolean mediaTranscoding = true;

    @JsonProperty("OrganizeFilesIntoFolders")
    private boolean organizeFilesIntoFolders = false;

    @JsonProperty("MaxDownloadChunks")
    private int maxDownloadChunks = 5;

    @JsonProperty("WebScannerEnabled")
    private boolean webScannerEnabled = true;

    @JsonProperty("WebScannerMaxDepth")
    private int webScannerMaxDepth = 0;

    @JsonProperty("WebScannerStrictHost")
    private boolean webScannerStrictHost = true;

    @JsonProperty("WebScannerAllowedExtensions")
    private String webScannerAllowedExtensions = "";

    @JsonProperty("WebScannerBlacklistedExtensions")
    private String webScannerBlacklistedExtensions = "";

    @JsonProperty("HostResolvers")
    private HostResolverSettings hostResolvers = new HostResolverSettings();

    @JsonProperty("MaxDownloadSpeedBytesPerSecond")
    private long maxDownloadSpeedBytesPerSecond = 0L;// 0 = unlimited

    @JsonProperty("MaxConnectionsPerHost")
    private int maxConnectionsPerHost = 10;

    @JsonProperty("MaxConcurrentCrawledDownloads")
    private int maxConcurrentCrawledDownloads = 1;

    @JsonProperty("MaxPageSizeBytes")
    private long maxPageSizeBytes = 100L * 1024 * 1024; // 100MB

    public int getMaxDownloadChunks() {
        return Math.clamp(maxDownloadChunks, 1, 15);
    }

    public int getWebScannerMaxDepth() {
        return Math.clamp(webScannerMaxDepth, 0, 10);
    }

    public long getMaxDownloadSpeedBytesPerSecond() {
        return Math.max(0L, maxDownloadSpeedBytesPerSecond);
    }

    public int getMaxConnectionsPerHost() {
        return Math.clamp(maxConnectionsPerHost, 1, 20);
    }

    public int getMaxConcurrentCrawledDownloads() {
        return Math.clamp(maxConcurrentCrawledDownloads, 1, 20);
    }

    public long getMaxPageSizeBytes() {
        return Math.clamp(maxPageSizeBytes,
            1L * 1024 * 1024, // 1MB
            500L * 1024 * 1024);  // 500MB
    }
}
