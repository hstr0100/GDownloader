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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ConcurrentLinkedHashSet<T> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final Set<T> set = new LinkedHashSet<>();

    public boolean add(T element) {
        lock.writeLock().lock();
        try {
            return set.add(element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(T element) {
        lock.writeLock().lock();
        try {
            return set.remove(element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean contains(T element) {
        lock.readLock().lock();
        try {
            return set.contains(element);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return set.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> snapshotAsList() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(set);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            set.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return set.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

}
