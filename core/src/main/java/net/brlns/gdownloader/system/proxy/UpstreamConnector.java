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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.settings.ProxySettings;
import net.brlns.gdownloader.settings.enums.ProxyTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class UpstreamConnector {

    private static final String LINE_END = "\r\n";

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;

    private static final int SOCKS4_VERSION = 4;
    private static final int SOCKS4_COMMAND_CONNECT = 1;
    private static final int SOCKS4_REQUEST_GRANTED = 90;

    private final Supplier<ProxySettings> settingsProvider;

    public UpstreamConnector(Supplier<ProxySettings> settingsProviderIn) {
        settingsProvider = settingsProviderIn;
    }

    public Socket connect(String host, int port) throws IOException {
        ProxySettings settings = settingsProvider.get();

        ProxyTypeEnum type = settings.isEnabled() && settings.isValid()
            ? settings.getProxyType()
            : ProxyTypeEnum.NO_PROXY;

        if (type == ProxyTypeEnum.NO_PROXY) {
            return open(new InetSocketAddress(host, port));
        }

        Socket socket = type == ProxyTypeEnum.HTTPS
            ? openTls(settings)
            : open(new InetSocketAddress(settings.getHost(), settings.getPort()));

        try {
            socket.setSoTimeout(CONNECT_TIMEOUT_MILLIS);

            switch (type) {
                case HTTP, HTTPS ->
                    httpTunnel(socket, settings, host, port);
                case SOCKS4 ->
                    socks4(socket, settings, host, port);
                case SOCKS5 ->
                    socks5(socket, settings, host, port);
                default ->
                    throw new IOException("Unsupported proxy type " + type);
            }

            socket.setSoTimeout(0);

            return socket;
        } catch (IOException | RuntimeException e) {
            closeQuietly(socket);

            throw e;
        }
    }

    private static Socket open(InetSocketAddress address) throws IOException {
        Socket socket = new Socket(Proxy.NO_PROXY);

        try {
            socket.connect(address, CONNECT_TIMEOUT_MILLIS);

            return socket;
        } catch (IOException | RuntimeException e) {
            closeQuietly(socket);

            throw e;
        }
    }

    private static Socket openTls(ProxySettings settings) throws IOException {
        Socket plain = open(new InetSocketAddress(settings.getHost(), settings.getPort()));

        try {
            SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
            SSLSocket secure = (SSLSocket)factory.createSocket(plain, settings.getHost(), settings.getPort(), true);

            SSLParameters parameters = secure.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(parameters);

            secure.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
            secure.startHandshake();

            return secure;
        } catch (IOException | RuntimeException e) {
            closeQuietly(plain);

            throw e;
        }
    }

    private static void socks5(Socket socket, ProxySettings settings, String host, int port) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        OutputStream out = socket.getOutputStream();

        boolean authenticate = settings.hasAuthentication();

        out.write(authenticate
            ? Socks5.frame(Socks5.VERSION, 2, Socks5.METHOD_NONE, Socks5.METHOD_USER_PASS)
            : Socks5.frame(Socks5.VERSION, 1, Socks5.METHOD_NONE));

        if (in.readUnsignedByte() != Socks5.VERSION) {
            throw new IOException("Upstream is not a SOCKS5 proxy");
        }

        int method = in.readUnsignedByte();
        if (method == Socks5.METHOD_USER_PASS && authenticate) {
            out.write(Socks5.loginRequest(
                settings.getUsername().getBytes(StandardCharsets.UTF_8),
                settings.getPassword().getBytes(StandardCharsets.UTF_8)));

            in.skipNBytes(1);
            if (in.readUnsignedByte() != 0) {
                throw new IOException("Upstream SOCKS5 proxy rejected the credentials");
            }
        } else if (method != Socks5.METHOD_NONE) {
            throw new IOException("Upstream SOCKS5 proxy requires an unsupported authentication method");
        }

        out.write(Socks5.connectRequest(host, port));

        if (in.readUnsignedByte() != Socks5.VERSION) {
            throw new IOException("Malformed SOCKS5 reply");
        }

        int reply = in.readUnsignedByte();
        in.skipNBytes(1);

        int addressType = in.readUnsignedByte();
        Socks5.readHost(in, addressType);
        in.skipNBytes(2);

        if (reply != Socks5.REPLY_SUCCEEDED) {
            throw new IOException("Upstream SOCKS5 proxy refused the connection, code " + reply);
        }
    }

    private static void socks4(Socket socket, ProxySettings settings, String host, int port) throws IOException {
        if (!(InetAddress.getByName(host) instanceof Inet4Address address)) {
            throw new IOException("SOCKS4 only supports IPv4 destinations");
        }

        String userId = Objects.requireNonNullElse(settings.getUsername(), "");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream request = new DataOutputStream(buffer);

        request.writeByte(SOCKS4_VERSION);
        request.writeByte(SOCKS4_COMMAND_CONNECT);
        request.writeShort(port);
        request.write(address.getAddress());
        request.write(userId.getBytes(StandardCharsets.UTF_8));
        request.writeByte(0);

        socket.getOutputStream().write(buffer.toByteArray());

        DataInputStream in = new DataInputStream(socket.getInputStream());
        in.skipNBytes(1);
        int status = in.readUnsignedByte();
        in.skipNBytes(6);

        if (status != SOCKS4_REQUEST_GRANTED) {
            throw new IOException("Upstream SOCKS4 proxy refused the connection, code " + status);
        }
    }

    private static void httpTunnel(Socket socket, ProxySettings settings, String host, int port) throws IOException {
        String authority = host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;

        StringBuilder request = new StringBuilder()
            .append("CONNECT ")
            .append(authority)
            .append(" HTTP/1.1")
            .append(LINE_END)
            .append("Host: ")
            .append(authority)
            .append(LINE_END);

        if (settings.hasAuthentication()) {
            String credentials = settings.getUsername() + ":" + settings.getPassword();

            request.append("Proxy-Authorization: Basic ")
                .append(Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .append(LINE_END);
        }

        request.append(LINE_END);

        OutputStream out = socket.getOutputStream();
        out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        InputStream in = socket.getInputStream();
        List<String> head = HttpLines.readHead(in);

        String[] statusLine = head.get(0).split(" ", 3);
        int status = statusLine.length > 1 ? Integer.parseInt(statusLine[1]) : 0;

        if (status < 200 || status > 299) {
            throw new IOException("Upstream proxy refused the tunnel: " + head.get(0));
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            log.debug("Failed to close upstream socket: {}", e.getMessage());
        }
    }
}
