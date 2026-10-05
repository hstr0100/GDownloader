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
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PersistenceRepository<K, T> extends AbstractRepository {

    private final Class<T> entityClass;
    private final Field idField;

    public PersistenceRepository(EntityManagerFactory emfIn, Class<T> entityClassIn) {
        super(emfIn);

        entityClass = entityClassIn;
        idField = findIdField(entityClassIn);
    }

    private static Field findIdField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);

                    return field;
                }
            }
        }

        throw new IllegalStateException("No @Id field found on " + type.getName());
    }

    private Object identifierOf(T entity) {
        try {
            return idField.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read the id of " + entityClass.getName(), e);
        }
    }

    private List<Object> identifiersOf(List<T> entities) {
        return entities.stream()
            .map(this::identifierOf)
            .toList();
    }

    public List<T> getAll() {
        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
            TypedQuery<T> query = em.createQuery(jpql, entityClass);

            if (log.isTraceEnabled()) {
                log.trace("Get All: {}", query.getResultList());
            }

            return query.getResultList();
        } catch (Exception e) {
            log.error("Failed to obtain entities", e);
            return List.of();
        }
    }

    public boolean insertAll(List<T> entities) {
        if (log.isTraceEnabled()) {
            log.trace("Insert All: {}", entities);
        }

        return inTransaction(identifiersOf(entities), (em) -> {
            for (T entity : entities) {
                em.merge(entity);
            }
        });
    }

    public boolean upsertAll(List<T> entities) {
        if (entities.isEmpty()) {
            return true;
        }

        if (log.isTraceEnabled()) {
            log.trace("Upsert All: {}", entities);
        }

        return inTransaction(identifiersOf(entities), (em) -> {
            for (T entity : entities) {
                em.merge(entity);
            }
        });
    }

    public boolean upsert(T entity) {
        if (log.isTraceEnabled()) {
            log.trace("Upsert: {}", entity);
        }

        return inTransaction(Collections.singletonList(identifierOf(entity)), (em) -> {
            em.merge(entity);
        });
    }

    public boolean remove(K id) {
        if (log.isTraceEnabled()) {
            log.trace("Remove: {}", id);
        }

        return inTransactionCommitIf(Collections.singletonList(id), (em) -> {
            T entity = em.find(entityClass, id);
            if (entity == null) {
                return false;
            }

            em.remove(entity);

            return true;
        });
    }

    public boolean removeAll(Collection<K> ids) {
        if (ids.isEmpty()) {
            return true;
        }

        if (log.isTraceEnabled()) {
            log.trace("Remove All: {}", ids);
        }

        return inTransaction(ids, (em) -> {
            for (K id : ids) {
                T entity = em.find(entityClass, id);
                if (entity != null) {
                    em.remove(entity);
                }
            }
        });
    }

    public Optional<T> getById(K id) {
        if (log.isTraceEnabled()) {
            log.trace("Get: {}", id);
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
