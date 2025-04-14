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
package net.brlns.gdownloader.process;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.CancelHook;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class ProcessMonitor {

    private final ExecutorService processMonitor;

    private final LinkedBlockingQueue<TrackedProcess> trackedProcesses
        = new LinkedBlockingQueue<>();

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition notEmpty = lock.newCondition();

    private final AtomicBoolean closed = new AtomicBoolean();

    @PreDestroy
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        lock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }

        List<TrackedProcess> processesToStop = new ArrayList<>();
        trackedProcesses.drainTo(processesToStop);

        List<Process> runningProcesses = processesToStop.stream()
            .map(TrackedProcess::getProcess)
            .filter(Process::isAlive)
            .collect(Collectors.toList());

        for (Process process : runningProcesses) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Stopping process #{} due to shutdown", process.pid());
                }

                tryStopProcess(process);
            } catch (Exception e) {
                log.error("Failed to stop process during shutdown", e);
            }
        }

        processMonitor.shutdownNow();

        try {
            if (!processMonitor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Process monitor did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.warn("Interrupted while waiting for process monitor to terminate", e);
        }
    }

    public ProcessMonitor() {
        processMonitor = Executors.newSingleThreadExecutor();
        processMonitor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    while (trackedProcesses.isEmpty() && !closed.get()) {
                        try {
                            notEmpty.await(500, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (closed.get() && trackedProcesses.isEmpty()) {
                        return;
                    }
                } finally {
                    lock.unlock();
                }

                Iterator<TrackedProcess> iterator = trackedProcesses.iterator();
                while (iterator.hasNext()) {
                    TrackedProcess trackedProcess = iterator.next();

                    Process process = trackedProcess.getProcess();

                    if (!process.isAlive()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Process #{} has exited", process.pid());
                        }

                        iterator.remove();
                        continue;
                    }

                    if (closed.get() || trackedProcess.getCancelHook().get()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Process Monitor is stopping #{}", process.pid());
                        }

                        try {
                            tryStopProcess(process);
                        } catch (InterruptedException e) {
                            log.error("Interrupted", e);
                        } catch (Exception e) {
                            log.error("Failed to stop process", e);
                        }
                    }
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public Process startProcess(List<String> arguments) throws IOException {
        return startProcess(arguments, new CancelHook());
    }

    public Process startProcess(List<String> arguments,
        CancelHook cancelHook) throws IOException {
        Process process = new ProcessBuilder(arguments)
            .redirectErrorStream(true)
            .start();

        trackProcess(process, cancelHook);
        return process;
    }

    public Process startSilentProcess(List<String> arguments) throws IOException {
        return startSilentProcess(arguments, new CancelHook());
    }

    public Process startSilentProcess(List<String> arguments,
        CancelHook cancelHook) throws IOException {
        Process process = new ProcessBuilder(arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();

        trackProcess(process, cancelHook);
        return process;
    }

    private void trackProcess(Process process, CancelHook cancelHook) {
        if (log.isTraceEnabled()) {
            log.trace("Tracking process #{}", process.pid());
        }

        trackedProcesses.offer(new TrackedProcess(process, cancelHook));

        lock.lock();
        try {
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    private void tryStopProcess(Process process) throws InterruptedException {
        if (process.isAlive()) {
            long quitTimer = System.currentTimeMillis();

            // First try to politely ask the process to excuse itself.
            process.destroy();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("Process #{} did not terminate in time, forcefully stopping it", process.pid());
                // Time's up. I guess asking nicely wasn't in the cards.
                process.destroyForcibly();
            }

            if (log.isDebugEnabled()) {
                log.debug("Took {}ms to stop the process #{}",
                    (System.currentTimeMillis() - quitTimer), process.pid());
            }
        }
    }
}
