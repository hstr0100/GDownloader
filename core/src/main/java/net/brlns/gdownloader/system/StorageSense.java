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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long COMMIT_WINDOW_MILLIS = 2000;
    private static final long MAX_BLOCK_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long VOLUME_CACHE_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final Map<String, Blocked> blocked = new ConcurrentHashMap<>();
    private final Map<String, Commit> commits = new ConcurrentHashMap<>();
    private final Map<Path, CachedVolume> volumeCache = new ConcurrentHashMap<>();

    private final AtomicBoolean monitorStarted = new AtomicBoolean();
    private final AtomicBoolean shutdown = new AtomicBoolean();

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

    private boolean isEnabled() {
        return !disableHook.get();
    }

    public boolean hasBlockedVolumes() {
        return isEnabled() && !blocked.isEmpty();
    }

    public List<String> getBlockedLabels() {
        return blocked.values().stream().map(entry -> entry.volume().label()).toList();
    }

    public boolean isBlocked(Collection<File> footprint) {
        if (!hasBlockedVolumes()) {
            return false;
        }

        return resolveAll(footprint).keySet().stream().anyMatch(blocked::containsKey);
    }

    public boolean tryAdmit(Collection<File> footprint, long expectedBytes) {
        if (!isEnabled()) {
            return true;
        }

        Map<String, Volume> volumes = resolveAll(footprint);

        if (volumes.keySet().stream().anyMatch(blocked::containsKey)) {
            return false;
        }

        Map<String, Probe> probes = probe(volumes);
        List<Probe> low = probes.values().stream().filter(Probe::isLow).toList();

        if (!low.isEmpty()) {
            blockAndPublish(low);

            return false;
        }

        if (expectedBytes <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();

        for (Probe probe : probes.values()) {
            Commit commit = commits.get(probe.volume().id());
            long committed = commit != null && now - commit.at() < COMMIT_WINDOW_MILLIS ? commit.bytes() : 0L;

            if (probe.usable() - committed < LOW_SPACE_BYTES + expectedBytes) {
                return false;
            }
        }

        volumes.keySet().forEach(id -> commits.compute(id, (key, commit) -> new Commit(
            commit != null && now - commit.at() < COMMIT_WINDOW_MILLIS ? commit.bytes() + expectedBytes : expectedBytes,
            now)));

        return true;
    }

    public boolean reportFailure(Collection<File> footprint, boolean storageSignal) {
        if (!storageSignal || !isEnabled()) {
            return false;
        }

        Map<String, Probe> probes = probe(resolveAll(footprint));

        if (probes.isEmpty()) {
            return false;
        }

        List<Probe> culprits = probes.values().stream().filter(Probe::isLow).toList();

        blockAndPublish(culprits.isEmpty() ? probes.values() : culprits);

        return true;
    }

    public void startBackgroundMonitor() {
        if (!monitorStarted.compareAndSet(false, true)) {
            return;
        }

        Thread monitorThread = new Thread(() -> {
            while (!shutdown.get()) {
                try {
                    if (isEnabled()) {
                        Map<String, Probe> probes = probe(resolveAll(watchSupplier.get()));

                        blockAndPublish(probes.values().stream().filter(Probe::isLow).toList());
                        releaseRecoveredVolumes();
                    } else if (!blocked.isEmpty()) {
                        blocked.clear();
                        publish(List.of());
                    }

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

    private void releaseRecoveredVolumes() {
        long now = System.currentTimeMillis();
        boolean released = false;

        for (Blocked entry : List.copyOf(blocked.values())) {
            long required = Math.max(LOW_SPACE_BYTES, entry.freeAtBlock())
                + RESUME_MARGIN_BYTES;

            if (usableSpace(entry.volume().store()) >= required
                || now - entry.blockedAt() >= MAX_BLOCK_MILLIS) {
                blocked.remove(entry.volume().id());
                released = true;

                log.info("Releasing storage volume {}", entry.volume().label());
            }
        }

        if (released) {
            publish(List.of());
        }
    }

    private void blockAndPublish(Collection<Probe> probes) {
        List<String> newlyBlocked = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Probe probe : probes) {
            Volume volume = probe.volume();
            Blocked entry = new Blocked(volume, Math.min(probe.usable(), 1L << 50), now);

            if (blocked.putIfAbsent(volume.id(), entry) == null) {
                newlyBlocked.add(volume.label());

                log.warn("Storage volume {} is out of space", volume.label());
            }
        }

        if (!newlyBlocked.isEmpty()) {
            publish(newlyBlocked);
        }
    }

    private void publish(List<String> newlyBlocked) {
        List<String> labels = getBlockedLabels();

        EventDispatcher.dispatch(StorageStatusEvent.builder()
            .low(!labels.isEmpty())
            .volumes(labels)
            .newlyBlocked(List.copyOf(newlyBlocked))
            .timestamp(System.currentTimeMillis())
            .build());
    }

    private Map<String, Probe> probe(Map<String, Volume> volumes) {
        Map<String, Probe> probes = new LinkedHashMap<>();

        volumes.forEach((id, volume) -> probes.put(id, new Probe(volume, usableSpace(volume.store()))));

        return probes;
    }

    private long usableSpace(FileStore store) {
        try {
            return store.getUsableSpace();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private Map<String, Volume> resolveAll(Collection<File> directories) {
        Map<String, Volume> volumes = new LinkedHashMap<>();

        for (File directory : directories) {
            Volume volume = resolveVolume(directory);

            if (volume != null) {
                volumes.putIfAbsent(volume.id(), volume);
            }
        }

        return volumes;
    }

    @Nullable
    private Volume resolveVolume(File directory) {
        Path path = directory.toPath().toAbsolutePath().normalize();
        long now = System.currentTimeMillis();

        CachedVolume cached = volumeCache.get(path);
        if (cached != null && now - cached.resolvedAt() < VOLUME_CACHE_MILLIS) {
            return cached.volume();
        }

        try {
            Path existing = path;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }

            if (existing != null) {
                FileStore store = Files.getFileStore(existing);
                Volume volume = new Volume(store.toString(), path.toString(), store);

                volumeCache.put(path, new CachedVolume(volume, now));

                return volume;
            }
        } catch (IOException | SecurityException e) {
            log.debug("Unable to resolve storage volume for {}: {}", path, e.getMessage());
        }

        volumeCache.remove(path);

        return null;
    }

    @PreDestroy
    @Override
    public void close() {
        shutdown.set(true);
    }

    private record Volume(String id, String label, FileStore store) {

    }

    private record CachedVolume(Volume volume, long resolvedAt) {

    }

    private record Commit(long bytes, long at) {

    }

    private record Blocked(Volume volume, long freeAtBlock, long blockedAt) {

    }

    private record Probe(Volume volume, long usable) {

        private boolean isLow() {
            return usable < LOW_SPACE_BYTES;
        }
    }
}
