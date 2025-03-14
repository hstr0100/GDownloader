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

import java.util.concurrent.*;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class PriorityVirtualThreadExecutor {

    private final ExecutorService virtualThreadExecutor;
    private final PriorityBlockingQueue<PriorityTask<?>> taskQueue;

    public PriorityVirtualThreadExecutor() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        taskQueue = new PriorityBlockingQueue<>();

        Thread dispatcher = new Thread(this::dispatchTasks);
        dispatcher.setName("PriorityTaskDispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    private void dispatchTasks() {
        while (true) {
            try {
                PriorityTask<?> task = taskQueue.take();
                virtualThreadExecutor.execute(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public <T> Future<T> submit(Callable<T> task) {
        return submitWithPriority(task, 0);
    }

    public Future<?> submit(Runnable task) {
        return submitWithPriority(task, null, 0);
    }

    public <T> Future<T> submitWithPriority(Callable<T> task, int priority) {
        PriorityTask<T> priorityTask = new PriorityTask<>(task, priority);

        taskQueue.add(priorityTask);

        return priorityTask;
    }

    public <T> Future<T> submitWithPriority(Runnable task, T result, int priority) {
        PriorityTask<T> priorityTask = new PriorityTask<>(task, result, priority);

        taskQueue.add(priorityTask);

        return priorityTask;
    }

    public Future<?> submitWithPriority(Runnable task, int priority) {
        return submitWithPriority(task, null, priority);
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }

    public void shutdownNow() {
        virtualThreadExecutor.shutdownNow();
    }

    private class PriorityTask<V> extends FutureTask<V> implements Comparable<PriorityTask<V>> {

        private final int priority;

        public PriorityTask(Callable<V> callable, int priorityIn) {
            super(() -> {
                try {
                    return callable.call();
                } catch (Throwable t) {// Assertions are not exceptions. If we don't catch them here, they're gone.
                    GDownloader.handleException(t);
                    throw t;
                }
            });

            priority = priorityIn;
        }

        public PriorityTask(Runnable runnable, V result, int priorityIn) {
            super(() -> {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    GDownloader.handleException(t);
                    throw t;
                }
            }, result);

            priority = priorityIn;
        }

        @Override
        public int compareTo(PriorityTask<V> o) {
            return Integer.compare(o.priority, this.priority);
        }
    }

}
