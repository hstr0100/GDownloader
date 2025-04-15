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
package net.brlns.gdownloader.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO
@Slf4j
@RequiredArgsConstructor
public class DownloadIntervalometer {

    private final ConcurrentHashMap<String, AtomicReference<HostData>> hostMap = new ConcurrentHashMap<>();

    private final int minValue;
    private final int maxValue;

    public int getAndCompute(String urlIn) {
        try {
            URL url = new URI(urlIn).toURL();
            String host = url.getHost();
            if (host == null) {
                return 0;
            }

            long currentTime = System.currentTimeMillis();
            AtomicReference<Integer> resultInterval = new AtomicReference<>(0);

            hostMap.compute(host, (key, existingValue) -> {
                if (existingValue == null) {
                    int newInterval = RandomUtils.randomInt(minValue, maxValue);
                    return new AtomicReference<>(new HostData(currentTime, newInterval));
                }

                HostData currentData = existingValue.get();
                long elapsedTime = currentTime - currentData.getLastAccessTime();
                long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime);

                if (elapsedSeconds >= currentData.getInterval()) {
                    int newInterval = RandomUtils.randomInt(minValue, maxValue);
                    return new AtomicReference<>(new HostData(currentTime, newInterval));
                } else {
                    int remainingTime = currentData.getInterval() - (int)elapsedSeconds;
                    resultInterval.set(remainingTime);

                    return new AtomicReference<>(new HostData(currentTime, currentData.getInterval()));
                }
            });

            return resultInterval.get();
        } catch (URISyntaxException | MalformedURLException e) {
            log.error("Failed to parse URL for intervalometer: {}: {}", urlIn, e.getMessage());
        }

        return 0;
    }

    public void park(int timeout, CancelHook cancelHook) throws InterruptedException {
        if (timeout <= 0 || timeout > 120) {
            return;
        }

        long endTime = System.currentTimeMillis() + timeout * 1000L;
        while (System.currentTimeMillis() < endTime) {
            TimeUnit.MILLISECONDS.sleep(100);

            if (cancelHook.get()) {
                return;
            }
        }
    }

    public int get(String urlIn) {
        try {
            URL url = new URI(urlIn).toURL();
            String host = url.getHost();
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
        } catch (URISyntaxException | MalformedURLException e) {
            log.error("Failed to parse URL for intervalometer: {}: {}", urlIn, e.getMessage());
        }

        return 0;
    }

    @Data
    private static class HostData {

        private final long lastAccessTime;
        private final int interval;
    }
}
