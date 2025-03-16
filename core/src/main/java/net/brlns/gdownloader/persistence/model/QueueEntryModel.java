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
 * TODO: persist media info
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Entity
@Table(name = "queue_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = "download_id")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntryModel implements Serializable {

    @Id
    @Column(name = "download_id")
    public long downloadId; // Chaos ensues if these fields are not public

    @Column(name = "url", nullable = false)
    public String url;

}
