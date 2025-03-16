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
import jakarta.persistence.TypedQuery;
import java.io.File;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.persistence.AbstractDatabase;

@Slf4j
public class PersistenceRepository<K, T> extends AbstractDatabase {

    private final Class<T> entityClass;

    public PersistenceRepository(File filePathIn, Class<T> entityClassIn) {
        super(filePathIn);

        entityClass = entityClassIn;
    }

    @Override
    protected String getDbFileName() {
        return "persistence.db";
    }

    public List<T> getAll() {
        try (EntityManager em = getEmf().createEntityManager()) {
            String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
            TypedQuery<T> query = em.createQuery(jpql, entityClass);

            log.info("Get {}:", query.getResultList());
            return query.getResultList();
        }
    }

    public void insertAll(List<T> entities) {
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            for (T entity : entities) {
                em.merge(entity);
            }

            em.flush();

            em.getTransaction().commit();
        }
    }

    public T upsert(T entity) {
        log.info("Upsert {}:", entity);
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            em.merge(entity);
            em.flush();

            em.getTransaction().commit();
        }

        return entity;
    }

    public boolean remove(K id) {
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            T entity = em.find(entityClass, id);
            if (entity != null) {
                em.remove(entity);
                em.flush();

                em.getTransaction().commit();

                return true;
            } else {
                em.getTransaction().rollback();

                return false;
            }
        }
    }

    public Optional<T> getById(K id) {
        try (EntityManager em = getEmf().createEntityManager()) {
            T entity = em.find(entityClass, id);

            return Optional.ofNullable(entity);
        }
    }
}
