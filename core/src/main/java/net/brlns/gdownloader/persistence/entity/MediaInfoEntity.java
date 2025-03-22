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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "media_info")
public class MediaInfoEntity implements Serializable {

    @Id
    @Column(name = "media_info_id")
    private Long downloadId;

    @Column(name = "id", length = 2048)// There are some ginormous ids in the wild
    private String id;

    @Column(name = "title", length = 2048)
    private String title;

    @Column(name = "thumbnail", columnDefinition = "LONGVARCHAR")
    private String base64EncodedThumbnail;

    @Column(name = "description", columnDefinition = "LONGVARCHAR")
    private String description;

    @Column(name = "channel_id", length = 2048)
    private String channelId;

    @Column(name = "channel_url", length = 2048)
    private String channelUrl;

    @Column(name = "duration")
    private long duration;

    @Column(name = "view_count")
    private int viewCount;

    @Column(name = "upload_date", length = 48)
    private String uploadDate;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "width")
    private int width;

    @Column(name = "height")
    private int height;

    @Column(name = "resolution", length = 48)
    private String resolution;

    @Column(name = "filesize_approx")
    private long filesizeApprox;

    @Column(name = "fps")
    private int fps;

    @Override
    public String toString() {
        return "MediaInfoEntity{"
            + "downloadId=" + downloadId
            + ", id='" + id + '\''
            + ", title='" + title + '\''
            + ", channelId='" + channelId + '\''
            + ", channelUrl='" + channelUrl + '\''
            + ", duration=" + duration
            + ", viewCount=" + viewCount
            + ", uploadDate='" + uploadDate + '\''
            + ", timestamp=" + timestamp
            + ", width=" + width
            + ", height=" + height
            + ", resolution='" + resolution + '\''
            + ", filesizeApprox=" + filesizeApprox
            + ", fps=" + fps
            + '}';
    }

}
