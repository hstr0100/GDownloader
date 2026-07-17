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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.persistence.entity.DownloadHistoryEntity;
import net.brlns.gdownloader.persistence.entity.DownloadHistorySummary;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadHistoryRepository extends PersistenceRepository<String, DownloadHistoryEntity> {

    private static final String SUMMARY_SELECT
        = "SELECT NEW net.brlns.gdownloader.persistence.entity.DownloadHistorySummary("
        + "e.url, e.originalUrl, e.title, e.hostDisplayName, e.downloaderId, e.downloadedAt) "
        + "FROM DownloadHistoryEntity e";

    private static final String ORDER_BY_DATE = " ORDER BY e.downloadedAt DESC, e.url ASC";

    private static final String FILTER_WHERE
        = " WHERE LOWER(e.title) LIKE :filter OR LOWER(e.url) LIKE :filter OR LOWER(e.hostDisplayName) LIKE :filter";

    private static final int BULK_DELETE_CHUNK_SIZE = 1000;

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

    public long getCount(String filter) {
        boolean hasFilter = filter != null && !filter.isBlank();

        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT COUNT(e) FROM DownloadHistoryEntity e" + (hasFilter ? FILTER_WHERE : "");

            TypedQuery<Long> query = em.createQuery(jpql, Long.class);
            if (hasFilter) {
                query.setParameter("filter", "%" + filter.toLowerCase(Locale.ROOT) + "%");
            }

            long result = query.getSingleResult();

            return result;
        } catch (Exception e) {
            log.error("Failed to count download history", e);

            return 0L;
        }
    }

    public List<DownloadHistorySummary> getSummaryPage(int offset, int limit, String filter) {
        boolean hasFilter = filter != null && !filter.isBlank();

        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = SUMMARY_SELECT + (hasFilter ? FILTER_WHERE : "") + ORDER_BY_DATE;

            TypedQuery<DownloadHistorySummary> query = em.createQuery(jpql, DownloadHistorySummary.class);
            if (hasFilter) {
                query.setParameter("filter", "%" + filter.toLowerCase(Locale.ROOT) + "%");
            }

            query.setFirstResult(Math.max(0, offset));
            query.setMaxResults(Math.max(0, limit));

            List<DownloadHistorySummary> result = query.getResultList();

            return result;
        } catch (Exception e) {
            log.error("Failed to page download history", e);

            return List.of();
        }
    }

    public List<String> getUrlsPage(int offset, int limit, String filter) {
        boolean hasFilter = filter != null && !filter.isBlank();

        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT e.url FROM DownloadHistoryEntity e" + (hasFilter ? FILTER_WHERE : "") + ORDER_BY_DATE;

            TypedQuery<String> query = em.createQuery(jpql, String.class);
            if (hasFilter) {
                query.setParameter("filter", "%" + filter.toLowerCase(Locale.ROOT) + "%");
            }

            query.setFirstResult(Math.max(0, offset));
            query.setMaxResults(Math.max(0, limit));

            List<String> result = query.getResultList();

            return result;
        } catch (Exception e) {
            log.error("Failed to page download history urls", e);

            return List.of();
        }
    }

    public List<String> getAllUrls(String filter) {
        boolean hasFilter = filter != null && !filter.isBlank();

        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT e.url FROM DownloadHistoryEntity e" + (hasFilter ? FILTER_WHERE : "") + ORDER_BY_DATE;

            TypedQuery<String> query = em.createQuery(jpql, String.class);
            if (hasFilter) {
                query.setParameter("filter", "%" + filter.toLowerCase(Locale.ROOT) + "%");
            }

            List<String> result = query.getResultList();

            return result;
        } catch (Exception e) {
            log.error("Failed to list download history urls", e);

            return List.of();
        }
    }

    public Map<String, DownloadHistoryEntity> getEntitiesByUrls(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Map.of();
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            TypedQuery<DownloadHistoryEntity> query = em.createQuery(
                "SELECT e FROM DownloadHistoryEntity e WHERE e.url IN :urls",
                DownloadHistoryEntity.class);

            query.setParameter("urls", urls);

            Map<String, DownloadHistoryEntity> result = query.getResultList().stream()
                .collect(Collectors.toMap(DownloadHistoryEntity::getUrl, e -> e, (a, b) -> a));

            return result;
        } catch (Exception e) {
            log.error("Failed to batch-resolve download history entries", e);

            return Map.of();
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

    public int removeUrls(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }

        List<String> urlList = new ArrayList<>(urls);
        int totalRemoved = 0;

        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            try {
                for (int i = 0; i < urlList.size(); i += BULK_DELETE_CHUNK_SIZE) {
                    List<String> chunk = urlList.subList(i, Math.min(i + BULK_DELETE_CHUNK_SIZE, urlList.size()));

                    totalRemoved += em.createQuery(
                        "DELETE FROM DownloadHistoryEntity e WHERE e.url IN :urls")
                        .setParameter("urls", chunk)
                        .executeUpdate();
                }

                em.getTransaction().commit();
            } catch (Exception e) {
                em.getTransaction().rollback();
                throw e;
            }

            invalidateCache();

            return totalRemoved;
        } catch (Exception e) {
            log.error("Failed to bulk remove download history entries", e);

            return 0;
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
