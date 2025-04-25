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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.persistence.entity.CounterTypeEnum;
import net.brlns.gdownloader.persistence.repository.CounterRepository;
import net.brlns.gdownloader.persistence.repository.MediaInfoRepository;
import net.brlns.gdownloader.persistence.repository.QueueEntryRepository;
import org.eclipse.persistence.config.PersistenceUnitProperties;

/**
 * This class relies on HSQLDB and EclipseLink JPA.
 * This combination has been very reliable in my tests and has a relatively small footprint (<10MB).
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PersistenceManager {

    public static final ObjectMapper ENTITY_MAPPER = JsonMapper.builder()
        .annotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public PropertyName findNameForSerialization(Annotated a) {
                return null; // Ignore @JsonProperty as our db fields do not match the json DTOs
            }

            @Override
            public PropertyName findNameForDeserialization(Annotated a) {
                return null;
            }
        })
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private final GDownloader main;
    private final File databaseDirectory;

    private EntityManagerFactory emf;

    @Getter
    private boolean initialized = false;

    @Getter
    private CounterRepository counters;

    @Getter
    private QueueEntryRepository queueEntries;

    @Getter
    private MediaInfoRepository mediaInfos;

    @Getter
    private final boolean firstBoot;

    public PersistenceManager(GDownloader mainIn) {
        main = mainIn;
        databaseDirectory = new File(GDownloader.getWorkDirectory(), "db");
        firstBoot = !databaseDirectory.exists();
    }

    @PostConstruct
    public final boolean init() throws Exception {
        if (initialized) {
            return true;
        }

        try {
            if (!databaseDirectory.exists()) {
                databaseDirectory.mkdirs();
            }

            File databaseFile = new File(databaseDirectory, getDbFileName());

            Map<String, String> properties = new HashMap<>();
            if (main.getConfig().isRestoreSessionAfterRestart()) {
                properties.put(PersistenceUnitProperties.DDL_GENERATION,
                    PersistenceUnitProperties.CREATE_OR_EXTEND);
            } else {
                properties.put(PersistenceUnitProperties.DDL_GENERATION,
                    PersistenceUnitProperties.DROP_AND_CREATE);
            }

            properties.put(PersistenceUnitProperties.JDBC_URL, "jdbc:hsqldb:"
                + "file:" + databaseFile + ";"
                + "sql.syntax_pgs=true;"
                + "hsqldb.lob_compressed=true;"
                + "hsqldb.script_format=3");

            emf = Persistence.createEntityManagerFactory("hsqldbPU", properties);

            if (emf == null) {
                throw new RuntimeException("Cannot create database.");
            }

            counters = new CounterRepository(emf);
            queueEntries = new QueueEntryRepository(emf);
            mediaInfos = new MediaInfoRepository(emf);

            log.info("{} db is now open", databaseFile);
            initialized = true;
            return true;
        } catch (Exception e) {
            log.error("Cannot initialize database: {} persistance disabled.", e.getMessage());
        }

        return false;
    }

    @PreDestroy
    public void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    public EntityManagerFactory getEmf() {
        if (!initialized) {
            throw new RuntimeException("Tried to access db before initialization");
        }

        return emf;
    }

    private String getDbFileName() {
        return "persistence";
    }
}
