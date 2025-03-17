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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "media_info")
public class MediaInfoModel implements Serializable {

    @Id
    @Column(name = "download_id")
    public long downloadId;

    @Column(name = "id", length = 256)
    public String id;

    @Column(name = "title", length = 2048)
    public String title;

    @Column(name = "thumbnail", columnDefinition = "MEDIUMTEXT")
    public String base64EncodedThumbnail;

    @Column(name = "description", columnDefinition = "MEDIUMTEXT")
    public String description;

    @Column(name = "channel_id", length = 256)
    public String channelId;

    @Column(name = "channel_url", length = 2048)
    public String channelUrl;

    @Column(name = "duration")
    public long duration;

    @Column(name = "view_count")
    public int viewCount;

    @Column(name = "upload_date", length = 48)
    public String uploadDate;

    @Column(name = "timestamp")
    public long timestamp;

    @Column(name = "width")
    public int width;

    @Column(name = "height")
    public int height;

    @Column(name = "resolution", length = 48)
    public String resolution;

    @Column(name = "filesize_approx")
    public long filesizeApprox;

    @Column(name = "fps")
    public int fps;
}
