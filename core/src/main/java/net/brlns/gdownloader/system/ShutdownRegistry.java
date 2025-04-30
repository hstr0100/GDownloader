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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
            log.error("Closing all registered closeable resources...");

            List<AutoCloseable> currentResources = new ArrayList<>(resourcesToClose);
            if (currentResources.isEmpty()) {
                log.info("No resources registered for shutdown.");
                return;
            }

            Map<AutoCloseable, Set<Class<?>>> mustCloseBefore = new HashMap<>();
            for (AutoCloseable resource : currentResources) {
                CloseBefore annotation = resource.getClass().getAnnotation(CloseBefore.class);

                if (annotation != null) {
                    Set<Class<?>> beforeClasses = new HashSet<>(Arrays.asList(annotation.before()));
                    mustCloseBefore.put(resource, beforeClasses);
                }
            }

            // Topological Sort (Kahn's algorithm)
            Map<AutoCloseable, List<AutoCloseable>> adj = new HashMap<>();
            Map<AutoCloseable, Integer> inDegree = new HashMap<>();

            for (AutoCloseable resource : currentResources) {
                adj.put(resource, new ArrayList<>());
                inDegree.put(resource, 0);
            }

            for (AutoCloseable u : currentResources) {
                Set<Class<?>> beforeClasses = mustCloseBefore.get(u);

                if (beforeClasses != null) {
                    for (Class<?> depClass : beforeClasses) {
                        for (AutoCloseable v : currentResources) {
                            if (depClass.isInstance(v)) {
                                adj.get(u).add(v);
                                inDegree.put(v, inDegree.get(v) + 1);
                            }
                        }
                    }
                }
            }

            Queue<AutoCloseable> queue = new LinkedList<>();

            for (AutoCloseable resource : currentResources) {
                if (inDegree.get(resource) == 0) {
                    queue.offer(resource);
                }
            }

            List<AutoCloseable> sortedOrder = new ArrayList<>();
            while (!queue.isEmpty()) {
                AutoCloseable u = queue.poll();
                sortedOrder.add(u);

                for (AutoCloseable v : adj.get(u)) {
                    inDegree.put(v, inDegree.get(v) - 1);

                    if (inDegree.get(v) == 0) {
                        queue.offer(v);
                    }
                }
            }

            if (sortedOrder.size() != currentResources.size()) {
                Set<AutoCloseable> cyclicResources = new HashSet<>(currentResources);
                cyclicResources.removeAll(sortedOrder);

                log.error("Cyclic dependency detected. Cannot close: {}", cyclicResources);
            }

            for (AutoCloseable resource : sortedOrder) {
                try {
                    log.info("Closing resource: {}", resource.getClass().getSimpleName());
                    resource.close();
                } catch (Exception e) {
                    log.error("Failed to close resource: {}", resource.getClass().getSimpleName(), e);
                }
            }

            resourcesToClose.clear();
            log.info("Closed all registered closeable resources.");
        }
    }

    /**
     * Annotation indicating that an AutoCloseable should be closed
     * before the specified classes are closed.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface CloseBefore {

        Class<?>[] before();
    }
}
