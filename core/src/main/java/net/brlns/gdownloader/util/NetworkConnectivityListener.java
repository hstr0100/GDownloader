/*
 * Copyright (C) 2025 @hstr0100
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

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.ConnectivityStatusEvent;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class NetworkConnectivityListener {

    private static final String[] TARGET_HOSTS = new String[] {"1.1.1.1", "8.8.8.8"};

    private static final int TARGET_PORT = 53;
    private static final int CONNECTION_TIMEOUT_MS = 2000;
    private static final int RETRY_DELAY_SECONDS = 5;

    private static final int BACKGROUND_CHECK_INTERVAL_SECONDS = 30;
    private static final int RESTORATION_CHECK_INTERVAL_SECONDS = 5;

    private final AtomicBoolean shutdown = new AtomicBoolean();

    private static boolean isNetworkAvailable() {
        for (String host : TARGET_HOSTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, TARGET_PORT), CONNECTION_TIMEOUT_MS);

                return true;
            } catch (IOException e) {
                // Continue to the next host
            }
        }

        return false;
    }

    public static void waitForConnectivity() {
        log.info("Checking for network connectivity...");

        while (!isNetworkAvailable()) {
            log.warn("Network not available. Retrying in {} seconds...", RETRY_DELAY_SECONDS);

            try {
                TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for connectivity.");
                return;
            }
        }

        log.info("Network is online.");
    }

    public void startBackgroundConnectivityCheck() {
        log.info("Starting background network connectivity checker...");

        Thread listenerThread = new Thread(() -> {
            boolean wasConnected = isNetworkAvailable();
            log.info("Network initial state: {}", wasConnected ? "ONLINE" : "OFFLINE");

            while (!shutdown.get()) {
                boolean isConnected = isNetworkAvailable();

                if (wasConnected && !isConnected) {
                    log.warn("Network connectivity lost.");
                    EventDispatcher.dispatch(ConnectivityStatusEvent.builder()
                        .active(isConnected)
                        .timestamp(System.currentTimeMillis())
                        .build());

                    wasConnected = false;
                } else if (!wasConnected && isConnected) {
                    log.info("Network connectivity restored.");
                    EventDispatcher.dispatch(ConnectivityStatusEvent.builder()
                        .active(isConnected)
                        .timestamp(System.currentTimeMillis())
                        .build());

                    wasConnected = true;
                }

                long sleepDurationSeconds = wasConnected
                    ? BACKGROUND_CHECK_INTERVAL_SECONDS
                    : RESTORATION_CHECK_INTERVAL_SECONDS;

                try {
                    TimeUnit.SECONDS.sleep(sleepDurationSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while checking connectivity.");
                    break;
                }
            }
        }, "NetworkConnectivityListenerThread");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @PreDestroy
    public void close() {
        shutdown.set(true);
    }
}
