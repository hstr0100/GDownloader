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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class LRUCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> backingMap;

    public LRUCache(int capacityIn) {
        capacity = capacityIn;

        backingMap = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        return backingMap.getOrDefault(key, null);
    }

    public void put(K key, V value) {
        backingMap.put(key, value);
    }

    @Override
    public String toString() {
        return backingMap.toString();
    }
}
