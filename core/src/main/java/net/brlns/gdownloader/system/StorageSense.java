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
package net.brlns.gdownloader.system;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.StorageStatusEvent;
import net.brlns.gdownloader.util.CancelHook;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class StorageSense implements AutoCloseable {

    private static final String[] OUT_OF_SPACE_MARKERS = new String[] {
        "no space left",
        "not enough space",
        "not enough disk space",
        "insufficient disk space",
        "insufficient space",
        "disk full",
        "disk quota exceeded",
        "errno 28",
        "enospc",
        "não há espaço suficiente",
        "espaço insuficiente",
        "sem espaço",
        "no hay suficiente espacio",
        "espacio insuficiente",
        "磁盘空间不足",
        "空间不足"
    };

    private static final long MIB = 1024L * 1024L;
    private static final long LOW_SPACE_BYTES = 512L * MIB;
    private static final long RESUME_MARGIN_BYTES = 128L * MIB;

    private static final long MONITOR_INTERVAL_MILLIS = 500;

    private final Object checkLock = new Object();

    private final AtomicBoolean monitorStarted = new AtomicBoolean();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private volatile Set<String> halted = Set.of();

    private volatile Supplier<Collection<File>> watchSupplier = List::of;
    private volatile CancelHook disableHook = new CancelHook();

    public static boolean looksLikeOutOfSpace(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String lowered = text.toLowerCase(Locale.ROOT);

        for (String marker : OUT_OF_SPACE_MARKERS) {
            if (lowered.contains(marker)) {
                return true;
            }
        }

        return false;
    }

    public static boolean looksLikeOutOfSpace(@Nullable Throwable throwable) {
        Throwable current = throwable;

        for (int depth = 0; current != null && depth < 8; depth++) {
            if (looksLikeOutOfSpace(current.getMessage())) {
                return true;
            }

            current = current.getCause() == current ? null : current.getCause();
        }

        return false;
    }

    public void setWatchSupplier(Supplier<Collection<File>> supplier) {
        watchSupplier = supplier;
    }

    public void setDisableHook(CancelHook hook) {
        disableHook = hook;
    }

    public boolean isHalted() {
        return !halted.isEmpty();
    }

    public List<String> getHaltedVolumes() {
        return List.copyOf(halted);
    }

    public boolean check() {
        Set<String> current = new LinkedHashSet<>();
        boolean changed;

        synchronized (checkLock) {
            Set<String> previous = halted;

            if (!disableHook.get()) {
                for (Map.Entry<String, FileStore> volume : resolveVolumes(watchSupplier.get()).entrySet()) {
                    long required = LOW_SPACE_BYTES
                        + (previous.contains(volume.getKey()) ? RESUME_MARGIN_BYTES : 0L);

                    if (usableSpace(volume.getValue()) < required) {
                        current.add(volume.getKey());
                    }
                }
            }

            changed = !current.equals(previous);

            if (changed) {
                halted = Collections.unmodifiableSet(current);

                log.warn("Storage halted volumes: {}", current);
            }
        }

        if (changed) {
            EventDispatcher.dispatch(StorageStatusEvent.builder()
                .low(!current.isEmpty())
                .volumes(List.copyOf(current))
                .timestamp(System.currentTimeMillis())
                .build());
        }

        return !current.isEmpty();
    }

    public void startBackgroundMonitor() {
        if (!monitorStarted.compareAndSet(false, true)) {
            return;
        }

        Thread monitorThread = new Thread(() -> {
            while (!shutdown.get()) {
                try {
                    check();

                    TimeUnit.MILLISECONDS.sleep(MONITOR_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Storage monitor error", e);
                }
            }
        }, "StorageSenseThread");

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private long usableSpace(FileStore store) {
        try {
            return store.getUsableSpace();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private Map<String, FileStore> resolveVolumes(Collection<File> directories) {
        Map<String, FileStore> volumes = new LinkedHashMap<>();

        for (File directory : directories) {
            try {
                Path existing = directory.toPath().toAbsolutePath().normalize();

                while (existing != null && !Files.exists(existing)) {
                    existing = existing.getParent();
                }

                if (existing != null) {
                    FileStore store = Files.getFileStore(existing);

                    volumes.putIfAbsent(store.toString(), store);
                }
            } catch (IOException | SecurityException e) {
                log.debug("Unable to resolve storage volume for {}: {}", directory, e.getMessage());
            }
        }

        return volumes;
    }

    @PreDestroy
    @Override
    public void close() {
        shutdown.set(true);
    }
}
