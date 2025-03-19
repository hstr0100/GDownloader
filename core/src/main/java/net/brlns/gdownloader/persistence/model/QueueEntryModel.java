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
package net.brlns.gdownloader.persistence.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.downloader.enums.*;
import net.brlns.gdownloader.persistence.converter.*;
import net.brlns.gdownloader.settings.filters.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "queue_entries")
public class QueueEntryModel implements Serializable {

    // Chaos ensues if these fields are not public
    @Id
    @Column(name = "queue_entry_id")
    public long downloadId;// = 1l;

    @Column(name = "url", length = 2048)
    public String url;// = "https://www.youtube.com/watch?v=NgWkPTKDY_k&list=PLDOjCqYj3ys3TEe8HCR7_cYH7X7dU28_B&index=15";

    @Column(name = "original_url", length = 2048)
    public String originalUrl;

    @Column(name = "last_status_message", length = 2048)
    public String lastStatusMessage;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "media_info_id", referencedColumnName = "media_info_id", nullable = true)
    public MediaInfoModel mediaInfo;// = null;

    @Convert(converter = UrlFilterConverter.class)
    @Column(name = "url_filter")
    public AbstractUrlFilter filter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "downloader_blacklist", joinColumns = @JoinColumn(name = "download_id"))
    @Enumerated(EnumType.STRING)
    public ArrayList<DownloaderIdEnum> downloaderBlacklist = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "forced_downloader")
    public DownloaderIdEnum forcedDownloader;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_downloader")
    public DownloaderIdEnum currentDownloader;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_queue_category")
    public QueueCategoryEnum currentQueueCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "download_status")
    public DownloadStatusEnum downloadStatus;

    @Column(name = "download_stated")
    public boolean downloadStarted;

    @Column(name = "download_running")
    public boolean running;

    @Column(name = "retry_counter")
    public int retryCounter;

    @Column(name = "is_queried")
    public boolean queried;

    @Column(name = "tmp_directory_path", length = 2048)
    public String tmpDirectoryPath;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "final_media_files", joinColumns = @JoinColumn(name = "download_id"))
    public ArrayList<String> finalMediaFilePaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "error_logs", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @Column(name = "error_log")
    public ArrayList<String> errorLog = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "download_logs", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @Column(name = "download_log")
    // TODO: check if this is really necessary
    public ArrayList<String> downloadLog = new ArrayList<>();
}
