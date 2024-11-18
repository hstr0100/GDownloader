/*
 * Copyright (C) 2024 hstr0100
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ExpiringSet<T> {

    private final Map<T, Long> map = new ConcurrentHashMap<>();

    private final long expirationTimeMillis;

    public ExpiringSet(TimeUnit unit, long expirationTimeIn) {
        expirationTimeMillis = unit.toMillis(expirationTimeIn);
    }

    public void add(T element) {
        removeExpiredEntries();

        map.put(element, System.currentTimeMillis());
    }

    public boolean contains(T element) {
        removeExpiredEntries();

        Long addedTime = map.get(element);
        if (addedTime == null) {
            return false;
        }

        if (System.currentTimeMillis() - addedTime < expirationTimeMillis) {
            return true;
        } else {
            map.remove(element);
            return false;
        }
    }

    public boolean remove(T element) {
        boolean result = map.remove(element) != null;

        removeExpiredEntries();

        return result;
    }

    public int size() {
        removeExpiredEntries();

        return map.size();
    }

    private void removeExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        map.keySet().removeIf(key -> currentTime - map.get(key) >= expirationTimeMillis);
    }
}
