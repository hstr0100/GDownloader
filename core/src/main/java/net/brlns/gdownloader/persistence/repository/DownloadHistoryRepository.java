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
package net.brlns.gdownloader.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.persistence.entity.DownloadHistoryEntity;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadHistoryRepository extends PersistenceRepository<String, DownloadHistoryEntity> {

    private final Set<String> knownUrlCache = ConcurrentHashMap.newKeySet();
    private final Object cacheLock = new Object();
    private final AtomicBoolean cacheLoaded = new AtomicBoolean();

    public DownloadHistoryRepository(EntityManagerFactory emfIn) {
        super(emfIn, DownloadHistoryEntity.class);
    }

    private void ensureCacheLoaded() {
        if (cacheLoaded.get()) {
            return;
        }

        synchronized (cacheLock) {
            if (cacheLoaded.get()) {
                return;
            }

            try (EntityManager em = getEmf().createEntityManager()) {
                TypedQuery<Object[]> query = em.createQuery(
                    "SELECT e.url, e.originalUrl FROM DownloadHistoryEntity e", Object[].class);

                for (Object[] row : query.getResultList()) {
                    if (row[0] != null) {
                        knownUrlCache.add((String)row[0]);
                    }

                    if (row[1] != null) {
                        knownUrlCache.add((String)row[1]);
                    }
                }

                cacheLoaded.set(true);
            } catch (Exception e) {
                log.error("Failed to preload download history cache", e);
            }
        }
    }

    private void invalidateCache() {
        synchronized (cacheLock) {
            knownUrlCache.clear();
            cacheLoaded.set(false);
        }
    }

    public List<DownloadHistoryEntity> getAllOrderedByDate() {
        try (EntityManager em = getEmf().createEntityManager()) {
            TypedQuery<DownloadHistoryEntity> query = em.createQuery(
                "SELECT e FROM DownloadHistoryEntity e ORDER BY e.downloadedAt DESC",
                DownloadHistoryEntity.class);

            return query.getResultList();
        } catch (Exception e) {
            log.error("Failed to obtain download history", e);

            return List.of();
        }
    }

    public boolean isUrlKnown(String url, String originalUrl) {
        ensureCacheLoaded();

        return (url != null && knownUrlCache.contains(url))
            || (originalUrl != null && knownUrlCache.contains(originalUrl));
    }

    @Override
    public boolean upsert(DownloadHistoryEntity entity) {
        boolean result = super.upsert(entity);

        if (result) {
            // No need to force a reload, we already know exactly what was added.
            ensureCacheLoaded();

            if (entity.getUrl() != null) {
                knownUrlCache.add(entity.getUrl());
            }

            if (entity.getOriginalUrl() != null) {
                knownUrlCache.add(entity.getOriginalUrl());
            }
        }

        return result;
    }

    @Override
    public boolean remove(String url) {
        boolean result = super.remove(url);

        if (result) {
            invalidateCache();
        }

        return result;
    }

    public Optional<DownloadHistoryEntity> findByUrl(String url) {
        try (EntityManager em = getEmf().createEntityManager()) {
            TypedQuery<DownloadHistoryEntity> query = em.createQuery(
                "SELECT e FROM DownloadHistoryEntity e WHERE e.url = :url OR e.originalUrl = :url",
                DownloadHistoryEntity.class);

            query.setParameter("url", url);
            query.setMaxResults(1);

            return query.getResultList().stream().findFirst();
        } catch (Exception e) {
            log.error("Failed to look up download history entry for: {}", url, e);

            return Optional.empty();
        }
    }

    public boolean clearAll() {
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM DownloadHistoryEntity").executeUpdate();
            em.getTransaction().commit();

            invalidateCache();

            return true;
        } catch (Exception e) {
            log.error("Failed to clear download history", e);

            return false;
        }
    }
}
