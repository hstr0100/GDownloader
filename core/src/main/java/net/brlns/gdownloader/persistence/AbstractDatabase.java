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

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Nitrite v4.3.1-SNAPSHOT with Jackson (v4.3.0 is broken with JPMS) was one of
 * the most unreliable pieces of software I have used in recent memory.
 *
 * Instead, this class now relies on ObjectDB. because at some point,
 * you just stop trying to fix the sinking ship and grab a lifeboat.
 *
 * ObjectDB is not FOSS though and that doesn't quite jive with me, so for
 * the final release we might use a different database back-end
 *
 * TODO: Currently all write operations are fire-and-forget, we can make them async
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractDatabase {

    @Getter
    private final File filePath;

    private EntityManagerFactory emf;

    @Getter
    private boolean initialized = false;

    public AbstractDatabase(File filePathIn) {
        filePath = filePathIn;
    }

    @PostConstruct
    public final boolean init() throws Exception {
        if (initialized) {
            return true;
        }

        try {
            filePath.mkdirs();

            File dbPath = new File(filePath, getDbFileName());

            Map<String, String> properties = new HashMap<>();

            properties.put("objectdb.recovery", "true");
            properties.put("objectdb.recovery.sync", "true");

            emf = Persistence.createEntityManagerFactory("objectdb:" + dbPath, properties);

            if (emf == null) {
                throw new RuntimeException("Cannot create database.");
            }

            log.info("{} db is now open", dbPath);
        } catch (Exception e) {
            log.error("Cannot initialize database: {} persistance disabled.", e.getMessage());
            return false;
        }

        initialized = true;
        return true;
    }

    public void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    protected EntityManagerFactory getEmf() {
        if (!initialized) {
            throw new RuntimeException("Tried to access db before initialization");
        }

        return emf;
    }

    protected abstract String getDbFileName();

}
