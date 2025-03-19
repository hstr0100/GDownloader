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

import jakarta.persistence.EntityManagerFactory;
import net.brlns.gdownloader.persistence.entity.QueueEntryEntity;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class QueueEntryRepository extends PersistenceRepository<Long, QueueEntryEntity> {

    public QueueEntryRepository(EntityManagerFactory emfIn) {
        super(emfIn, QueueEntryEntity.class);
    }

}
