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
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.persistence.model.MediaInfoModel;
import net.brlns.gdownloader.persistence.model.QueueEntryModel;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class MediaInfoRepository extends PersistenceRepository<Long, MediaInfoModel> {

    public MediaInfoRepository(EntityManagerFactory emfIn) {
        super(emfIn, MediaInfoModel.class);
    }

    public void addMediaInfo(MediaInfoModel mediaInfo) {
        if (mediaInfo.getDownloadId() <= 0) {
            throw new IllegalArgumentException("downloadId cannot be empty");
        }

        if (log.isDebugEnabled()) {
            log.info("Add MediaInfo for id {} title: {}", mediaInfo.getDownloadId(), mediaInfo.getTitle());
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            QueueEntryModel queueEntry = em.find(QueueEntryModel.class, mediaInfo.getDownloadId());
            if (queueEntry != null) {
                MediaInfoModel existingInfo = em.find(MediaInfoModel.class, mediaInfo.getDownloadId());
                if (existingInfo == null) {
                    em.persist(mediaInfo);
                } else {
                    em.merge(mediaInfo);
                }

                queueEntry.setMediaInfo(mediaInfo);
                em.merge(queueEntry);
            }

            em.getTransaction().commit();
        }
    }
}
