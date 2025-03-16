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
package net.brlns.gdownloader.persistence;

import java.io.File;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.persistence.model.QueueEntryModel;
import net.brlns.gdownloader.persistence.repository.CounterRepository;
import net.brlns.gdownloader.persistence.repository.PersistenceRepository;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PersistenceManager {

    private final GDownloader main;
    private final File dbDir;

    @Getter
    private final CounterRepository counterRepository;

    @Getter
    private final PersistenceRepository<Long, QueueEntryModel> queueEntryRepository;

    public PersistenceManager(GDownloader mainIn) {
        main = mainIn;
        dbDir = new File(GDownloader.getWorkDirectory(), "db");

        counterRepository = new CounterRepository(dbDir);
        queueEntryRepository = new PersistenceRepository<>(dbDir, QueueEntryModel.class);
    }

    public void close() {
        counterRepository.close();
        queueEntryRepository.close();
    }
}
