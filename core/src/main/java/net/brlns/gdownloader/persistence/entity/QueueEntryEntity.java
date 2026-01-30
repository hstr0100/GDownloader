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
package net.brlns.gdownloader.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.downloader.enums.*;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.persistence.converter.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "queue_entries")
public class QueueEntryEntity implements Serializable {

    @Id
    @Column(name = "queue_entry_id")
    private long downloadId;// = 1l;

    @Column(name = "url", length = 2048)
    private String url;// = "https://www.youtube.com/watch?v=NgWkPTKDY_k&list=PLDOjCqYj3ys3TEe8HCR7_cYH7X7dU28_B&index=15";

    @Column(name = "original_url", length = 2048)
    private String originalUrl;

    @Column(name = "last_status_message", length = 2048)
    private String lastStatusMessage;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "media_info_id", referencedColumnName = "media_info_id", nullable = true)
    private MediaInfoEntity mediaInfo;// = null;

    @Convert(converter = UrlFilterConverter.class)
    @Column(name = "url_filter")
    private AbstractUrlFilter filter;

    @Column(name = "filter_id", length = 128)
    private String filterId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "downloader_blacklist", joinColumns = @JoinColumn(name = "download_id"))
    @Enumerated(EnumType.STRING)
    private ArrayList<DownloaderIdEnum> downloaderBlacklist = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "forced_downloader")
    private DownloaderIdEnum forcedDownloader;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_downloader")
    private DownloaderIdEnum currentDownloader;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_download_type")
    private DownloadTypeEnum currentDownloadType;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_queue_category")
    private QueueCategoryEnum currentQueueCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "download_status")
    private DownloadStatusEnum downloadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_download_priority")
    private DownloadPriorityEnum currentDownloadPriority;

    @Column(name = "current_download_sequence")
    private Long currentDownloadSequence;

    @Column(name = "download_stated")
    private boolean downloadStarted;

    @Column(name = "download_skipped")
    private boolean downloadSkipped;

    @Column(name = "retry_counter")
    private int retryCounter;

    @Column(name = "is_queried")
    private boolean queried;

    @Column(name = "tmp_directory_path", length = 4096)
    private String tmpDirectoryPath;

    @ElementCollection(fetch = FetchType.EAGER)
    @Lob
    @CollectionTable(name = "final_media_files", joinColumns = @JoinColumn(name = "download_id"))
    @Column(name = "FINALMEDIAFILEPATHS")
    @Deprecated
    private ArrayList<String> finalMediaFilePaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "media_files", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @Column(name = "media_file_paths", length = 4096)
    private ArrayList<String> mediaFilePaths = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "thumbnail_urls", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @OrderColumn
    @Column(name = "media_thumbnail_urls", length = 2048)
    private ArrayList<String> thumbnailUrls = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "command_lines", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @OrderColumn
    @Column(name = "last_command_line", length = 8192)
    private ArrayList<String> lastCommandLine = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "error_logs", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @OrderColumn
    @Column(name = "error_log", length = 8192)
    private ArrayList<String> errorLog = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "download_logs", joinColumns = @JoinColumn(name = "download_id"))
    @Lob
    @OrderColumn
    @Column(name = "download_log", length = 8192)
    // TODO: check if this is really necessary
    private ArrayList<String> downloadLog = new ArrayList<>();
}
