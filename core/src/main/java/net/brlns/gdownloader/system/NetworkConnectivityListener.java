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
package net.brlns.gdownloader.system;

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
public class NetworkConnectivityListener implements AutoCloseable {

    private static final String[] TARGET_HOSTS = new String[] {
        "1.1.1.1", // Cloudflare (IPv4)
        "2606:4700:4700::1111", // Cloudflare (IPv6)
        "8.8.8.8", // Google (IPv4)
        "2001:4860:4860::8888" // Google (IPv6)
    };

    private static final String[] ALTERNATIVE_HOSTS = new String[] {
        "github.com", // Our primary home and distribution platform; likely to be available.
        "google.com", // One of the most popular websites worldwide.
        "www.bilibili.com", // Popular in China; fallback if GitHub and Google are unreachable.
        "yandex.ru" // Popular in Russia; fallback if GitHub and Google are unreachable.
    };

    private static final int DNS_PORT = 53;
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private static final int DNS_TIMEOUT_MS = 1000;
    private static final int ALTERNATIVE_TIMEOUT_MS = 2000;
    private static final int RETRY_DELAY_SECONDS = 6;

    private static final int BACKGROUND_CHECK_INTERVAL_SECONDS = 30;
    private static final int RESTORATION_CHECK_INTERVAL_SECONDS = 6;

    private final AtomicBoolean shutdown = new AtomicBoolean();

    private static boolean checkDnsConnectivity() {
        for (String host : TARGET_HOSTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, DNS_PORT), DNS_TIMEOUT_MS);
                return true;
            } catch (IOException e) {
                // Continue to the next host
            }
        }

        return false;
    }

    private static boolean checkAlternativeConnectivity() {
        for (String host : ALTERNATIVE_HOSTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, HTTPS_PORT), ALTERNATIVE_TIMEOUT_MS);
                return true;
            } catch (IOException e) {
                try (Socket httpSocket = new Socket()) {
                    httpSocket.connect(new InetSocketAddress(host, HTTP_PORT), ALTERNATIVE_TIMEOUT_MS);
                    return true;
                } catch (IOException httpEx) {
                    // Continue to next host
                }
            }
        }

        return false;
    }

    private static boolean isNetworkAvailable() {
        if (checkDnsConnectivity()) {
            return true;
        }

        return checkAlternativeConnectivity();
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
    @Override
    public void close() {
        shutdown.set(true);
    }
}
