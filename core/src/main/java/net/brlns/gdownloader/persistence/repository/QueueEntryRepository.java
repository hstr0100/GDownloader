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
package net.brlns.gdownloader.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.brlns.gdownloader.persistence.entity.QueueEntryEntity;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class QueueEntryRepository extends PersistenceRepository<Long, QueueEntryEntity> {

    public QueueEntryRepository(EntityManagerFactory emfIn) {
        super(emfIn, QueueEntryEntity.class);
    }

    public List<String> loadThumbnailUrls(Long downloadId) {
        try (EntityManager em = getEmf().createEntityManager()) {
            QueueEntryEntity entity = em.find(QueueEntryEntity.class, downloadId);

            return entity != null ? new ArrayList<>(entity.getThumbnailUrls()) : new ArrayList<>();
        }
    }

    public List<String> loadLastCommandLine(Long downloadId) {
        try (EntityManager em = getEmf().createEntityManager()) {
            QueueEntryEntity entity = em.find(QueueEntryEntity.class, downloadId);

            return entity != null ? new ArrayList<>(entity.getLastCommandLine()) : new ArrayList<>();
        }
    }

    public List<String> loadErrorLog(Long downloadId) {
        try (EntityManager em = getEmf().createEntityManager()) {
            QueueEntryEntity entity = em.find(QueueEntryEntity.class, downloadId);

            return entity != null ? new ArrayList<>(entity.getErrorLog()) : new ArrayList<>();
        }
    }

    public List<String> loadDownloadLog(Long downloadId) {
        try (EntityManager em = getEmf().createEntityManager()) {
            QueueEntryEntity entity = em.find(QueueEntryEntity.class, downloadId);

            return entity != null ? new ArrayList<>(entity.getDownloadLog()) : new ArrayList<>();
        }
    }

    public Map<String, LocalDateTime> loadPlaylistItemUploadTimes(Long downloadId) {
        try (EntityManager em = getEmf().createEntityManager()) {
            QueueEntryEntity entity = em.find(QueueEntryEntity.class, downloadId);
            if (entity == null) {
                return new HashMap<>();
            }

            Map<String, LocalDateTime> result = new HashMap<>();
            for (Map.Entry<String, Long> entry : entity.getPlaylistItemUploadTimes().entrySet()) {
                result.put(entry.getKey(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.getValue()), ZoneId.systemDefault()));
            }

            return result;
        }
    }
}
