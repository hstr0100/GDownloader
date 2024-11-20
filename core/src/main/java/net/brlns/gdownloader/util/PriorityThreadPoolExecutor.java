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

import java.util.concurrent.*;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class PriorityThreadPoolExecutor extends ThreadPoolExecutor {

    public PriorityThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new PriorityBlockingQueue<>());
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return submitWithPriority(task, 0);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submitWithPriority(task, null, 0);
    }

    public <T> Future<T> submitWithPriority(Callable<T> task, int priority) {
        PriorityTask<T> priorityTask = new PriorityTask<>(task, priority);

        execute(priorityTask);

        return priorityTask;
    }

    public <T> Future<T> submitWithPriority(Runnable task, T result, int priority) {
        PriorityTask<T> priorityTask = new PriorityTask<>(task, result, priority);

        execute(priorityTask);

        return priorityTask;
    }

    public Future<?> submitWithPriority(Runnable task, int priority) {
        return submitWithPriority(task, null, priority);
    }

    public void resize(int newCorePoolSize, int newMaxPoolSize) {
        int currentMaxPoolSize = getMaximumPoolSize();

        int tempSize = Math.min(currentMaxPoolSize, newMaxPoolSize);

        setCorePoolSize(tempSize);
        setMaximumPoolSize(tempSize);

        setMaximumPoolSize(newMaxPoolSize);
        setCorePoolSize(newCorePoolSize);
    }

    private class PriorityTask<V> extends FutureTask<V> implements Comparable<PriorityTask<V>> {

        private final int priority;

        public PriorityTask(Callable<V> callable, int priority) {
            super(() -> {
                try {
                    return callable.call();
                } catch (Exception e) {
                    GDownloader.handleException(e);
                    throw e;
                }
            });

            this.priority = priority;
        }

        public PriorityTask(Runnable runnable, V result, int priority) {
            super(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    GDownloader.handleException(e);
                    throw e;
                }
            }, result);

            this.priority = priority;
        }

        @Override
        public int compareTo(PriorityTask<V> o) {
            return Integer.compare(o.priority, this.priority);
        }
    }

}
