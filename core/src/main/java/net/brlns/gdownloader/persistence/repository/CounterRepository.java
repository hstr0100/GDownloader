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
import net.brlns.gdownloader.persistence.model.CounterModel;
import net.brlns.gdownloader.persistence.model.CounterTypeEnum;

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
            CounterModel counter = em.find(CounterModel.class, counterType.getName());
            if (counter == null) {
                counter = new CounterModel(counterType.getName(), 0);

                upsert(counter);
            }

            return counter.getValue();
        }
    }

    public void setCurrentValue(CounterTypeEnum counterType, long value) {
        long curr = getCurrentValue(CounterTypeEnum.DOWNLOAD_ID);

        if (value > curr) {// Value always go up
            CounterModel counter = new CounterModel(counterType.getName(), value);

            upsert(counter);
        }
    }

    public CounterModel upsert(CounterModel entity) {
        try (EntityManager em = getEmf().createEntityManager()) {
            em.getTransaction().begin();

            CounterModel managedEntity = em.merge(entity);

            em.flush();

            em.getTransaction().commit();

            return managedEntity;
        }
    }
}
