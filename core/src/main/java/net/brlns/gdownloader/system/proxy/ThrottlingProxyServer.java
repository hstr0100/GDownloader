/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.system.proxy;

import jakarta.annotation.Nullable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class ThrottlingProxyServer implements AutoCloseable {

    private static final String LOOPBACK = "127.0.0.1";
    private static final String PROXY_USERNAME = "gdownloader";
    private static final byte[] PROXY_USERNAME_BYTES = PROXY_USERNAME.getBytes(StandardCharsets.UTF_8);

    private static final int BACKLOG = 64;
    private static final int MAX_CONNECTIONS = 512;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 10000;
    private static final int IDLE_TIMEOUT_MILLIS = 300000;
    private static final long IDLE_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(IDLE_TIMEOUT_MILLIS);
    private static final int ACCEPT_RETRY_MILLIS = 100;

    private final BandwidthThrottle throttle;
    private final UpstreamConnector connector;
    private final Semaphore connectionSlots = new Semaphore(MAX_CONNECTIONS);
    private final AtomicBoolean running = new AtomicBoolean();

    private boolean closed;

    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();
    private final AtomicReference<String> url = new AtomicReference<>();
    private final AtomicReference<byte[]> expectedPassword = new AtomicReference<>();

    public ThrottlingProxyServer(BandwidthThrottle throttleIn, UpstreamConnector connectorIn) {
        throttle = throttleIn;
        connector = connectorIn;
    }

    public synchronized boolean start() {
        if (closed) {
            return false;
        }

        if (running.get()) {
            return true;
        }

        ServerSocket server;
        try {
            server = new ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK));
        } catch (IOException e) {
            log.error("Could not start the throttling proxy: {}",
                e.getMessage());

            return false;
        }

        // We don't need anything fancy here.
        String password = UUID.randomUUID().toString().replace("-", "");
        expectedPassword.set(password.getBytes(StandardCharsets.UTF_8));

        url.set("socks5h://"
            + PROXY_USERNAME + ":" + password + "@"
            + LOOPBACK + ":" + server.getLocalPort());

        serverSocket.set(server);

        running.set(true);

        Thread.ofVirtual()
            .name("throttling-proxy-acceptor")
            .start(() -> acceptLoop(server));

        log.info("Throttling proxy listening on {}:{}", LOOPBACK, server.getLocalPort());

        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getUrl() {
        return url.get();
    }

    @Override
    public synchronized void close() {
        closed = true;

        if (running.compareAndSet(true, false)) {
            closeQuietly(serverSocket.get());
        }
    }

    private void acceptLoop(ServerSocket server) {
        try {
            while (running.get()) {
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    if (server.isClosed()) {
                        return;
                    }

                    log.warn("Throttling proxy failed to accept a connection: {}", e.getMessage());

                    if (!pauseBeforeRetry()) {
                        return;
                    }

                    continue;
                }

                if (!connectionSlots.tryAcquire()) {
                    log.warn("Throttling proxy connection limit reached, rejecting.");
                    closeQuietly(client);

                    continue;
                }

                Thread.ofVirtual()
                    .name("throttling-proxy-connection")
                    .start(() -> {
                        try {
                            handle(client);
                        } finally {
                            connectionSlots.release();
                        }
                    });
            }
        } finally {
            running.set(false);

            closeQuietly(server);
        }
    }

    private static boolean pauseBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(ACCEPT_RETRY_MILLIS);

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return false;
        }
    }

    private void handle(Socket client) {
        try (client) {
            client.setKeepAlive(true);
            client.setTcpNoDelay(true);
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);

            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();

            if (!negotiate(in, out)) {
                return;
            }

            Destination destination = readRequest(in, out);
            if (destination == null) {
                return;
            }

            client.setSoTimeout(IDLE_TIMEOUT_MILLIS);

            tunnel(client, destination);
        } catch (IOException | RuntimeException e) {
            log.debug("Throttling proxy connection ended: {}", e.getMessage());
        }
    }

    private boolean negotiate(DataInputStream in, OutputStream out) throws IOException {
        if (in.readUnsignedByte() != Socks5.VERSION) {
            return false;
        }

        if (!offersUserPass(Socks5.readField(in))) {
            out.write(Socks5.frame(Socks5.VERSION, Socks5.METHOD_REJECTED));

            return false;
        }

        out.write(Socks5.frame(Socks5.VERSION, Socks5.METHOD_USER_PASS));

        if (in.readUnsignedByte() != Socks5.AUTH_VERSION) {
            return false;
        }

        byte[] username = Socks5.readField(in);
        byte[] password = Socks5.readField(in);

        boolean authorized = MessageDigest.isEqual(username, PROXY_USERNAME_BYTES)
            & MessageDigest.isEqual(password, expectedPassword.get());

        out.write(Socks5.frame(Socks5.AUTH_VERSION, authorized ? 0 : 1));

        return authorized;
    }

    private static boolean offersUserPass(byte[] methods) {
        for (byte method : methods) {
            if (method == Socks5.METHOD_USER_PASS) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static Destination readRequest(DataInputStream in, OutputStream out) throws IOException {
        int version = in.readUnsignedByte();
        int command = in.readUnsignedByte();
        in.skipNBytes(1);
        int addressType = in.readUnsignedByte();

        if (version != Socks5.VERSION) {
            return null;
        }

        String host = Socks5.readHost(in, addressType);
        if (host == null) {
            Socks5.reply(out, Socks5.REPLY_ADDRESS_NOT_SUPPORTED);

            return null;
        }

        int port = in.readUnsignedShort();

        if (command != Socks5.COMMAND_CONNECT) {
            Socks5.reply(out, Socks5.REPLY_COMMAND_NOT_SUPPORTED);

            return null;
        }

        if (host.isEmpty() || port == 0) {
            Socks5.reply(out, Socks5.REPLY_GENERAL_FAILURE);

            return null;
        }

        return new Destination(host, port);
    }

    private void tunnel(Socket client, Destination destination) throws IOException {
        OutputStream out = client.getOutputStream();

        Socket target;
        try {
            target = connector.connect(destination.host(), destination.port());
        } catch (IOException | RuntimeException e) {
            log.debug("Could not reach {}:{}: {}", destination.host(), destination.port(), e.getMessage());
            Socks5.reply(out, replyFor(e));

            return;
        }

        try (target) {
            target.setKeepAlive(true);
            target.setTcpNoDelay(true);
            target.setSoTimeout(IDLE_TIMEOUT_MILLIS);

            Socks5.reply(out, Socks5.REPLY_SUCCEEDED);

            relay(client, target);
        }
    }

    private static int replyFor(Exception e) {
        Throwable current = e;

        // This admitedly odd-looking loop protects against stack
        // overflows if deeply nested exceptions occur.
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return Socks5.REPLY_HOST_UNREACHABLE;
            }

            if (current instanceof ConnectException) {
                return Socks5.REPLY_CONNECTION_REFUSED;
            }

            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }

        return Socks5.REPLY_GENERAL_FAILURE;
    }

    private void relay(Socket client, Socket target) {
        AtomicLong lastActivity = new AtomicLong(System.nanoTime());

        Thread uploader = Thread.ofVirtual()
            .name("throttling-proxy-upload")
            .start(() -> pump(client, target, null, lastActivity));

        pump(target, client, throttle, lastActivity);

        try {
            uploader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pump(Socket source, Socket destination,
        @Nullable BandwidthThrottle limiter, AtomicLong lastActivity) {
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            InputStream in = source.getInputStream();
            OutputStream out = destination.getOutputStream();

            while (true) {
                int read;
                try {
                    read = in.read(buffer, 0, limiter != null
                        ? limiter.sliceFor(buffer.length) : buffer.length);
                } catch (SocketTimeoutException e) {
                    if (System.nanoTime() - lastActivity.get() >= IDLE_TIMEOUT_NANOS) {
                        throw e;
                    }

                    continue;
                }

                if (read == -1) {
                    break;
                }

                lastActivity.set(System.nanoTime());

                if (limiter != null) {
                    limiter.acquire(read, running::get);
                }

                out.write(buffer, 0, read);
                lastActivity.set(System.nanoTime());
            }

            destination.shutdownOutput();
        } catch (IOException | UnsupportedOperationException e) {
            closeQuietly(destination);
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception e) {
            log.debug("Failed to close proxy resource: {}", e.getMessage());
        }
    }

    private record Destination(String host, int port) {

    }
}
