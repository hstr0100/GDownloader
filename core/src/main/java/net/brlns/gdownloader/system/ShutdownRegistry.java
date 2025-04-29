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
package net.brlns.gdownloader.system;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class ShutdownRegistry {

    private static final List<AutoCloseable> resourcesToClose = new CopyOnWriteArrayList<>();

    private static final AtomicBoolean closed = new AtomicBoolean();

    private ShutdownRegistry() {
    }

    public static <T extends AutoCloseable> T closeable(@NonNull T instance) {
        register(instance);
        return instance;
    }

    public static void register(@NonNull AutoCloseable resource) {
        resourcesToClose.add(resource);
    }

    public static void unregister(@NonNull AutoCloseable resource) {
        resourcesToClose.remove(resource);
    }

    public static void closeAllResources() {
        if (closed.compareAndSet(false, true)) {
            log.error("Closing all registered shutdown hooks...");
            for (AutoCloseable resource : resourcesToClose) {
                try {
                    resource.close();
                } catch (Exception e) {
                    log.error("Failed to close: {}", resource.getClass().getName(), e);
                }
            }

            resourcesToClose.clear();
            log.info("Closed all registered resources.");
        }
    }
}
