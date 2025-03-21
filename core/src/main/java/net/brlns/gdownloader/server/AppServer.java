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
package net.brlns.gdownloader.server;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.server.result.AbstractResult;
import net.brlns.gdownloader.server.result.ResultEnum;
import net.brlns.gdownloader.server.result.StatusResult;

/**
 * Server class that handles wake and shutdown requests
 * from other GDownloader instances.
 *
 * Since GDownloader holds a lock on the persistence database,
 * it cannot run in multi-instance mode at this moment.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class AppServer {

    public static final int PROTOCOL_VERSION = 1;
    public static final int PORT = 49159;

    private final GDownloader main;

    private final AtomicBoolean running = new AtomicBoolean();

    private ServerSocket serverSocket;
    private final ExecutorService executor;

    private final Map<String, Function<String, AbstractResult>> commandHandlers = new HashMap<>();

    public AppServer(GDownloader mainIn) {
        main = mainIn;

        registerCommand("wake-up", this::handleWakeUp);
        registerCommand("shutdown", this::handleShutdown);

        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void registerCommand(String command, Function<String, AbstractResult> handler) {
        commandHandlers.put(command, handler);
    }

    @PostConstruct
    public boolean init() {
        if (tryBind()) {
            start();

            return true;
        }

        return false;
    }

    private boolean tryBind() {
        try {
            serverSocket = new ServerSocket(PORT, 10, InetAddress.getLoopbackAddress());
            log.info("Listening on localhost port {}", PORT);

            running.set(true);
            return true;
        } catch (Exception e) {
            log.error("Port {} is already in use. Another instance may be running.", PORT);

            if (log.isDebugEnabled()) {
                log.error("Exception:", e);
            }

            return false;
        }
    }

    private void start() {
        Thread serverThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (running.get()) {
                        log.error("Socket error");
                    }
                } catch (IOException e) {
                    log.error("I/O error accepting connection");
                }
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(Socket clientSocket) {
        try (
            clientSocket;
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            clientSocket.setSoTimeout(5000);

            String command = in.readLine();
            if (command != null) {
                if (log.isDebugEnabled()) {
                    log.info("Received command: {}", command);
                }

                AbstractResult response = handleCommand(command);

                String jsonResponse = GDownloader.OBJECT_MAPPER.writeValueAsString(response);

                if (log.isDebugEnabled()) {
                    log.info("Responding with: {}", jsonResponse);
                }

                out.println(jsonResponse);
            }
        } catch (SocketTimeoutException e) {
            log.warn("Client connection timed out");
        } catch (IOException e) {
            log.warn("Error handling client", e);
        }
    }

    private AbstractResult handleCommand(String commandLine) {
        String command;
        String params = "";

        int colonPos = commandLine.indexOf('=');
        if (colonPos > 0) {
            command = commandLine.substring(0, colonPos);
            params = commandLine.substring(colonPos + 1);
        } else {
            command = commandLine;
        }

        Function<String, AbstractResult> handler = commandHandlers.get(command);
        if (handler != null) {
            return handler.apply(params);
        } else {
            log.error("Unknown command: {}", command);
        }

        return new StatusResult(ResultEnum.UNHANDLED);
    }

    private AbstractResult handleWakeUp(String params) {
        log.info("Wake-up command received {} params: {}", params);
        main.initUi();

        return new StatusResult(ResultEnum.SUCCESS);
    }

    private AbstractResult handleShutdown(String params) {
        log.info("Shutdown command received {} params: {}", params);
        main.shutdown();

        return new StatusResult(ResultEnum.SUCCESS);
    }

    public void close() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
