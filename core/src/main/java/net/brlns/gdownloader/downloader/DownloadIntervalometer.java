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
package net.brlns.gdownloader.downloader;

import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.RandomUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@RequiredArgsConstructor
public class DownloadIntervalometer {

    private static final double BACKOFF_MULTIPLIER = 2.0;

    private static final int MAX_BACKOFF_TIER = 6; // 2^6 = 64x base interval

    private static final int MAX_INTERVAL_SECONDS = 1800; // 30 mins

    private final ConcurrentHashMap<String, AtomicReference<HostData>> hostMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> penaltyTiers = new ConcurrentHashMap<>();

    private final int minValue;
    private final int maxValue;

    public int getAndCompute(String urlIn) {
        String host = extractHost(urlIn);
        if (host == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        AtomicReference<Integer> resultInterval = new AtomicReference<>(0);

        hostMap.compute(host, (key, existingValue) -> {
            if (existingValue == null) {
                int newInterval = computeInterval(host);
                return new AtomicReference<>(new HostData(currentTime, newInterval));
            }

            HostData currentData = existingValue.get();
            long elapsedTime = currentTime - currentData.getLastAccessTime();
            long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime);

            if (elapsedSeconds >= currentData.getInterval()) {
                int newInterval = computeInterval(host);
                return new AtomicReference<>(new HostData(currentTime, newInterval));
            } else {
                int remainingTime = currentData.getInterval() - (int)elapsedSeconds;
                resultInterval.set(remainingTime);
                return new AtomicReference<>(new HostData(currentTime, currentData.getInterval()));
            }
        });

        return resultInterval.get();
    }

    public void notifyRateLimited(String urlIn) {
        String host = extractHost(urlIn);
        if (host == null) {
            return;
        }

        int tier = penaltyTiers.computeIfAbsent(host, key -> new AtomicInteger(0))
            .updateAndGet(currentTier -> Math.min(currentTier + 1, MAX_BACKOFF_TIER));

        int backoffInterval = computeInterval(host, tier);
        hostMap.put(host, new AtomicReference<>(new HostData(System.currentTimeMillis(), backoffInterval)));

        log.warn("{} reported a rate limit, backing off to tier {}/{} (~{}s before the next attempt)",
            host, tier, MAX_BACKOFF_TIER, backoffInterval);
    }

    public void notifySuccess(String urlIn) {
        String host = extractHost(urlIn);
        if (host == null) {
            return;
        }

        AtomicInteger tierRef = penaltyTiers.get(host);
        if (tierRef == null) {
            return;
        }

        int newTier = tierRef.updateAndGet(currentTier -> Math.max(currentTier - 1, 0));
        if (newTier == 0) {
            penaltyTiers.remove(host, tierRef);
        }
    }

    public int getCurrentBackoffTier(String urlIn) {
        String host = extractHost(urlIn);
        if (host == null) {
            return 0;
        }

        AtomicInteger tierRef = penaltyTiers.get(host);
        return tierRef == null ? 0 : tierRef.get();
    }

    private int computeInterval(String host) {
        AtomicInteger tierRef = penaltyTiers.get(host);
        int tier = tierRef == null ? 0 : tierRef.get();

        return computeInterval(host, tier);
    }

    private int computeInterval(String host, int tier) {
        int baseInterval = RandomUtils.randomInt(minValue, maxValue);
        if (tier <= 0) {
            return baseInterval;
        }

        double multiplier = Math.pow(BACKOFF_MULTIPLIER, tier);
        long scaledInterval = Math.round(baseInterval * multiplier);

        return (int)Math.min(scaledInterval, MAX_INTERVAL_SECONDS);
    }

    public void park(int timeout, CancelHook cancelHook) throws InterruptedException {
        if (timeout <= 0) {
            return;
        }

        int clampedTimeout = Math.min(timeout, MAX_INTERVAL_SECONDS);

        long endTime = System.currentTimeMillis() + clampedTimeout * 1000L;
        while (System.currentTimeMillis() < endTime) {
            TimeUnit.MILLISECONDS.sleep(100);

            if (cancelHook.get()) {
                return;
            }
        }
    }

    public int get(String urlIn) {
        String host = extractHost(urlIn);
        if (host == null) {
            return 0;
        }

        AtomicReference<HostData> hostDataRef = hostMap.get(host);
        if (hostDataRef == null) {
            return 0;
        }

        HostData data = hostDataRef.get();
        long elapsedTime = System.currentTimeMillis() - data.getLastAccessTime();
        long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime);

        if (elapsedSeconds >= data.getInterval()) {
            return 0;
        }

        return (int)(data.getInterval() - elapsedSeconds);
    }

    @Nullable
    private static String extractHost(String urlIn) {
        try {
            URL url = new URI(urlIn).toURL();
            String host = url.getHost();

            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException | MalformedURLException e) {
            log.error("Failed to parse URL for intervalometer: {}: {}", urlIn, e.getMessage());

            return null;
        }
    }

    @Data
    private static class HostData {

        private final long lastAccessTime;
        private final int interval;
    }
}
