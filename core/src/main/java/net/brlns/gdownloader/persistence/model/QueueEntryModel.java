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
@Table(name = "queue_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = "download_id")
)
public class QueueEntryModel implements Serializable {

    // Chaos ensues if these fields are not public
    @Id
    @Column(name = "download_id")
    public long downloadId;// = 1l;

    @Column(name = "url", length = 2048)
    public String url;// = "https://www.youtube.com/watch?v=NgWkPTKDY_k&list=PLDOjCqYj3ys3TEe8HCR7_cYH7X7dU28_B&index=15";

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "download_id", referencedColumnName = "download_id", nullable = true)
    public MediaInfoModel mediaInfo;// = null;

    public QueueEntryModel(String url) {
        this.url = url;
    }
}
