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
package net.brlns.gdownloader.system.proxy;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class BandwidthThrottle {

    private static final long MAX_SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private final Supplier<Long> bytesPerSecond;

    private long availableTokens;
    private long lastRefillNanos;

    public BandwidthThrottle(Supplier<Long> bytesPerSecondIn) {
        bytesPerSecond = bytesPerSecondIn;
        availableTokens = Math.max(0, bytesPerSecond.get());
        lastRefillNanos = System.nanoTime();
    }

    public int sliceFor(int maxBytes) {
        long limit = bytesPerSecond.get();
        if (limit <= 0) {
            return maxBytes;
        }

        return (int)Math.max(1024L, Math.min(maxBytes, limit / 10L));
    }

    public void acquire(int bytes, Supplier<Boolean> aliveCheck) {
        if (bytesPerSecond.get() <= 0) {
            return;
        }

        long deadlineNanos = System.nanoTime() + reserve(bytes);

        while (isAlive(aliveCheck) && bytesPerSecond.get() > 0) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return;
            }

            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, MAX_SLEEP_NANOS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return;
            }
        }
    }

    private static boolean isAlive(Supplier<Boolean> aliveCheck) {
        return aliveCheck == null || aliveCheck.get();
    }

    private synchronized long reserve(int bytes) {
        long limit = bytesPerSecond.get();
        if (limit <= 0) {
            return 0L;
        }

        long now = System.nanoTime();
        long refill = (long)((now - lastRefillNanos) / 1e9 * limit);
        if (refill > 0) {
            availableTokens = Math.min(limit, availableTokens + refill);
            lastRefillNanos = now;
        }

        availableTokens -= bytes;

        return availableTokens >= 0 ? 0L : (long)(-availableTokens / (double)limit * 1e9);
    }
}
