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
import net.brlns.gdownloader.persistence.entity.CounterEntity;
import net.brlns.gdownloader.persistence.entity.CounterTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CounterRepository extends AbstractRepository {

    public CounterRepository(EntityManagerFactory emfIn) {
        super(emfIn);
    }

    public long getCurrentValue(CounterTypeEnum counterType) {
        try (EntityManager em = getEmf().createEntityManager()) {
            CounterEntity counter = em.find(CounterEntity.class, counterType);
            if (counter == null) {
                counter = new CounterEntity(counterType, 0);

                upsert(counter);
            }

            return counter.getValue();
        } catch (Exception e) {
            log.error("Failed to obtain counter value for: {}", counterType, e);
            return 0l;
        }
    }

    public void setCurrentValue(CounterTypeEnum counterType, long value) {
        long curr = getCurrentValue(counterType);

        boolean overflowed = value < Long.MIN_VALUE / 2 && curr > Long.MAX_VALUE / 2;
        if (overflowed) {
            // Somehow, you downloaded so much stuff you actually overflowed a signed long, congrats!
            log.info("Counter {} overflowed {} < {}, wrapping around...",
                counterType, value, curr);
        }

        if (value > curr || overflowed) {
            CounterEntity counter = new CounterEntity(counterType, value);

            upsert(counter);
        }
    }

    public CounterEntity upsert(CounterEntity entity) {
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            CounterEntity managedEntity = em.merge(entity);

            em.flush();

            em.getTransaction().commit();

            return managedEntity;
        }
    }
}
