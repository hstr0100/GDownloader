/*
 * Copyright (C) 2024 hstr0100
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
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.persistence.entity.MediaInfoEntity;

import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title = "";

    @JsonProperty("playlist_title")
    private String playlistTitle = "";

    @JsonProperty("extractor")
    private String extractor = "";

    @JsonProperty("extractor_key")
    private String extractorKey = "";

    @JsonProperty("host_display_name")
    private String hostDisplayName = "";

    @JsonProperty("thumbnail")
    private String thumbnail = "";

    @JsonProperty("thumbnails")
    private List<Thumbnail> thumbnails = new ArrayList<>();

    @JsonProperty("description")
    private String description;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("channel_url")
    private String channelUrl;

    @JsonProperty("duration")
    private long duration;

    @JsonProperty("view_count")
    private long viewCount;

    @JsonProperty("upload_date")
    private String uploadDate;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("resolution")
    private String resolution;

    @JsonProperty("filesize_approx")
    private long filesizeApprox;

    @JsonProperty("fps")
    private int fps;

    @JsonIgnore
    private String base64EncodedThumbnail = "";

    @JsonIgnore
    public boolean isValid() {
        // Not much usefulness to this if these are missing
        return title != null && !title.isEmpty()
            || thumbnail != null && !thumbnail.isEmpty();
    }

    // TODO: implement
    @JsonIgnore
    @Nullable
    public LocalDate getUploadDateAsLocalDate() {
        if (notNullOrEmpty(uploadDate)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            return LocalDate.parse(uploadDate, formatter);
        }

        return null;
    }

    @JsonIgnore
    @Nullable
    public LocalDateTime getUploadDateAsLocalDateTime() {
        if (timestamp > 0) {
            Instant instant = Instant.ofEpochSecond(timestamp);
            return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        return null;
    }

    @JsonIgnore
    public Stream<String> supportedThumbnails() {
        Stream.Builder<String> builder = Stream.builder();

        if (thumbnail != null && thumbnail.matches("^https?://.*")) {
            builder.add(thumbnail);
        }

        thumbnails.stream()
            .filter(thumb -> thumb.getUrl() != null && thumb.getUrl().matches("^https?://.*(?<!\\.webp)$"))
            // Sorting in reverse to get the most basic and compatible thumbnails first
            .sorted(Comparator.comparingInt(Thumbnail::getPreference).reversed())
            .limit(2)
            .map(Thumbnail::getUrl)
            .forEach(builder::add);

        // Add WebP thumbnails last
        thumbnails.stream()
            .filter(thumb -> thumb.getUrl() != null && thumb.getUrl().matches("^https?://.*\\.webp$"))
            .sorted(Comparator.comparingInt(Thumbnail::getPreference).reversed())
            .map(Thumbnail::getUrl)
            .limit(2)
            .forEach(builder::add);

        return builder.build();
    }

    @JsonIgnore
    public Stream<String> bestThumbnails() {
        Stream.Builder<String> builder = Stream.builder();

        thumbnails.stream()
            .filter(thumb -> thumb.getUrl() != null && thumb.getUrl().matches("^https?://.*"))
            .sorted(Comparator.comparingInt(Thumbnail::getWidth).reversed())
            .map(Thumbnail::getUrl)
            .limit(5)
            .forEach(builder::add);

        if (thumbnail != null && thumbnail.matches("^https?://.*")) {
            builder.add(thumbnail);
        }

        return builder.build();
    }

    /**
     * Converts this MediaInfo to a MediaInfoEntity for persistence.
     */
    public MediaInfoEntity toEntity(long downloadId) {
        MediaInfoEntity entity = PersistenceManager.ENTITY_MAPPER
            .convertValue(this, MediaInfoEntity.class);

        entity.setDownloadId(downloadId);
        entity.setBase64EncodedThumbnail(base64EncodedThumbnail);

        return entity;
    }

    /**
     * Converts a MediaInfoEntity to a MediaInfo.
     */
    public static MediaInfo fromEntity(MediaInfoEntity entity) {
        MediaInfo info = PersistenceManager.ENTITY_MAPPER.convertValue(entity, MediaInfo.class);
        info.setBase64EncodedThumbnail(entity.getBase64EncodedThumbnail());

        return info;
    }
}
