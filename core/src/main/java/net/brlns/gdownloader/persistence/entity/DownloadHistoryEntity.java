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
package net.brlns.gdownloader.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "download_history")
public class DownloadHistoryEntity implements Serializable {

    @Id
    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "original_url", length = 2048)
    private String originalUrl;

    @Column(name = "title", length = 2048)
    private String title;

    @Column(name = "host_display_name", length = 2048)
    private String hostDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "downloader_id")
    private DownloaderIdEnum downloaderId;

    @Column(name = "thumbnail", columnDefinition = "LONGVARCHAR")
    private String base64EncodedThumbnail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "download_history_file_paths", joinColumns = @JoinColumn(name = "history_url"))
    @Lob
    @OrderColumn
    @Column(name = "file_path", length = 4096)
    private List<String> filePaths = new ArrayList<>();

    @Column(name = "downloaded_at")
    private long downloadedAt;
}
