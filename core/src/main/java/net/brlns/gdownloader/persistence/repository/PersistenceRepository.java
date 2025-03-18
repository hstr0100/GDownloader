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
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PersistenceRepository<K, T> extends AbstractRepository {

    private final Class<T> entityClass;

    public PersistenceRepository(EntityManagerFactory emfIn, Class<T> entityClassIn) {
        super(emfIn);

        entityClass = entityClassIn;
    }

    public List<T> getAll() {
        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
            TypedQuery<T> query = em.createQuery(jpql, entityClass);

            if (log.isDebugEnabled()) {
                log.info("Get All: {}", query.getResultList());
            }

            return query.getResultList();
        } catch (Exception e) {
            log.error("Failed to obtain entities", e);
            return List.of();
        }
    }

    public boolean insertAll(List<T> entities) {
        if (log.isDebugEnabled()) {
            log.info("Insert All: {}", entities);
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            for (T entity : entities) {
                em.merge(entity);
            }

            em.getTransaction().commit();
            return true;
        } catch (Exception e) {
            log.error("Failed to insert entities", e);
            return false;
        }
    }

    public boolean upsert(T entity) {
        if (log.isDebugEnabled()) {
            log.info("Upsert: {}", entity);
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            em.merge(entity);

            em.getTransaction().commit();
            return true;
        } catch (Exception e) {
            log.error("Failed to upsert entity", e);
            return false;
        }
    }

    public boolean remove(K id) {
        if (log.isDebugEnabled()) {
            log.info("Remove: {}", id);
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            T entity = em.find(entityClass, id);
            if (entity != null) {
                em.remove(entity);

                em.getTransaction().commit();

                return true;
            } else {
                em.getTransaction().rollback();

                return false;
            }
        } catch (Exception e) {
            log.error("Failed to remove entity", e);
            return false;
        }
    }

    public Optional<T> getById(K id) {
        if (log.isDebugEnabled()) {
            log.info("Get: {}", id);
        }

        try (EntityManager em = getEmf().createEntityManager()) {
            T entity = em.find(entityClass, id);

            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("Failed to obtain entity", e);
            return Optional.empty();
        }
    }
}
