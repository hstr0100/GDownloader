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
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractRepository {

    private static final int WRITE_LOCK_STRIPES = 256;

    private static final Map<EntityManagerFactory, ReentrantLock[]> WRITE_LOCKS = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> WRITING = new ThreadLocal<>();

    @Getter
    private final EntityManagerFactory emf;

    public AbstractRepository(EntityManagerFactory emfIn) {
        emf = emfIn;
    }

    protected boolean inTransaction(Collection<?> keys, Consumer<EntityManager> action) {
        return inTransactionCommitIf(keys, (em) -> {
            action.accept(em);

            return true;
        });
    }

    protected boolean inTransactionCommitIf(Collection<?> keys, Predicate<EntityManager> action) {
        if (WRITING.get() != null) {
            throw new IllegalStateException("Nested write transactions are not allowed");
        }

        ReentrantLock[] stripes = WRITE_LOCKS.computeIfAbsent(emf, key -> createStripes());

        int[] indexes = keys.stream()
            .mapToInt(key -> Math.floorMod(Objects.hashCode(key), WRITE_LOCK_STRIPES))
            .distinct()
            .sorted()
            .toArray();

        int acquired = 0;

        try {
            for (; acquired < indexes.length; acquired++) {
                stripes[indexes[acquired]].lock();
            }

            WRITING.set(Boolean.TRUE);

            try (EntityManager em = getEmf().createEntityManager()) {
                try {
                    em.getTransaction().begin();

                    if (!action.test(em)) {
                        em.getTransaction().rollback();

                        return false;
                    }

                    em.getTransaction().commit();

                    return true;
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }

                    throw e;
                }
            } catch (Exception e) {
                log.error("Failed to write entities", e);

                return false;
            }
        } finally {
            WRITING.remove();

            while (acquired > 0) {
                stripes[indexes[--acquired]].unlock();
            }
        }
    }

    private static ReentrantLock[] createStripes() {
        ReentrantLock[] stripes = new ReentrantLock[WRITE_LOCK_STRIPES];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new ReentrantLock();
        }

        return stripes;
    }
}
