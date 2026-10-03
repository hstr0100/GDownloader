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
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractRepository {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MILLIS = 5;
    private static final int BACKOFF_MAX_SHIFT = 6;

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

        WRITING.set(Boolean.TRUE);

        try {
            for (int attempt = 1;; attempt++) {
                try {
                    boolean committed = runOnce(action);

                    return committed;
                } catch (Exception e) {
                    if (attempt < MAX_ATTEMPTS && isTransient(e) && backOff(attempt)) {
                        log.debug("Transient database conflict, retrying write ({}/{})", attempt, MAX_ATTEMPTS);

                        continue;
                    }

                    log.error("Failed to write entities", e);

                    return false;
                }
            }
        } finally {
            WRITING.remove();
        }
    }

    private boolean runOnce(Predicate<EntityManager> action) {
        try (EntityManager em = getEmf().createEntityManager()) {
            try {
                em.getTransaction().begin();

                if (!action.test(em)) {
                    em.getTransaction().rollback();

                    return false;
                }

                em.getTransaction().commit();

                return true;
            } catch (RuntimeException e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }

                throw e;
            }
        }
    }

    private static boolean isTransient(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        int depth = 0;
        for (Throwable t = error; t != null && seen.add(t) && depth++ < 12; t = t.getCause()) {
            if (!(t instanceof SQLException sql)) {
                continue;
            }

            int chained = 0;
            for (SQLException current = sql; current != null && chained++ < 8; current = current.getNextException()) {
                String state = current.getSQLState();

                if (state != null && (state.startsWith("40") || state.equals("23505"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean backOff(int attempt) {
        long ceiling = BACKOFF_BASE_MILLIS << Math.min(attempt, BACKOFF_MAX_SHIFT);

        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(ceiling) + 1);

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return false;
        }
    }
}
