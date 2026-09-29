/*
 * Copyright (C) 2026 hstr0100
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

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.XXHash64;
import tools.jackson.core.JacksonException;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class EntityFingerprint {

    private static final AtomicBoolean warnedOnce = new AtomicBoolean();

    public static long of(Object snapshot) {
        try {
            byte[] json = GDownloader.OBJECT_MAPPER.writeValueAsBytes(snapshot);

            XXHash64 xxHash = new XXHash64(0);
            xxHash.update(json, 0, json.length);

            return xxHash.digest();
        } catch (JacksonException e) {
            if (!warnedOnce.compareAndSet(false, true)) {
                log.warn("Failed to compute a fingerprint for {}, dirty-checking"
                    + " will be disabled for it", snapshot != null ? snapshot.getClass() : null, e);
            }

            return snapshot != null ? snapshot.hashCode() : 0L;
        }
    }
}
