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

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ConcurrentLinkedHashSet<T> implements Iterable<T> {

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

    public boolean replaceAll(Collection<T> elements) {
        lock.writeLock().lock();
        try {
            set.clear();
            return set.addAll(elements);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addAll(Collection<T> elements) {
        lock.writeLock().lock();
        try {
            return set.addAll(elements);
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

    public ArrayList<T> snapshotAsList() {
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

    @Override
    public Iterator<T> iterator() {
        lock.readLock().lock();
        try {
            return new Iterator<>() {
                private final Iterator<T> iterator = new ArrayList<>(set).iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    return iterator.next();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Remove is not supported");
                }
            };
        } finally {
            lock.readLock().unlock();
        }
    }

}
