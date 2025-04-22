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
package net.brlns.gdownloader.util.collection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class LRUCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> backingMap;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacityIn) {
        capacity = capacityIn;

        backingMap = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                // Write lock must be held when this is called by the backing map
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            return backingMap.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            backingMap.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        lock.writeLock().lock();
        try {
            V value = backingMap.get(key);
            if (value == null) {
                value = mappingFunction.apply(key);
                if (value != null) {
                    backingMap.put(key, value);
                }
            }

            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public V remove(K key) {
        lock.writeLock().lock();
        try {
            return backingMap.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            backingMap.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return backingMap.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
}
