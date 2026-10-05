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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Minimal SOCKS5 helpers shared by the local proxy and the upstream connector.
 * Exists so we don't have to touch the JDK's global Authenticator.setDefault(...) nonsense.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
public final class Socks5 {

    public static final int VERSION = 5;
    public static final int AUTH_VERSION = 1;

    public static final int COMMAND_CONNECT = 1;

    public static final int METHOD_NONE = 0;
    public static final int METHOD_USER_PASS = 2;
    public static final int METHOD_REJECTED = 0xFF;

    public static final int ADDRESS_IPV4 = 1;
    public static final int ADDRESS_DOMAIN = 3;
    public static final int ADDRESS_IPV6 = 4;

    public static final int REPLY_SUCCEEDED = 0;
    public static final int REPLY_GENERAL_FAILURE = 1;
    public static final int REPLY_HOST_UNREACHABLE = 4;
    public static final int REPLY_CONNECTION_REFUSED = 5;
    public static final int REPLY_COMMAND_NOT_SUPPORTED = 7;
    public static final int REPLY_ADDRESS_NOT_SUPPORTED = 8;

    private static final int MAX_FIELD_LENGTH = 255;

    private Socks5() {
    }

    public static byte[] frame(int... values) {
        byte[] bytes = new byte[values.length];

        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte)values[i];
        }

        return bytes;
    }

    public static byte[] readBytes(DataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        in.readFully(bytes);

        return bytes;
    }

    public static byte[] readField(DataInputStream in) throws IOException {
        return readBytes(in, in.readUnsignedByte());
    }

    @Nullable
    public static String readHost(DataInputStream in, int addressType) throws IOException {
        return switch (addressType) {
            case ADDRESS_IPV4 ->
                InetAddress.getByAddress(readBytes(in, 4)).getHostAddress();
            case ADDRESS_IPV6 ->
                InetAddress.getByAddress(readBytes(in, 16)).getHostAddress();
            case ADDRESS_DOMAIN ->
                new String(readField(in), StandardCharsets.UTF_8);
            default ->
                null;
        };
    }

    public static void reply(OutputStream out, int code) throws IOException {
        out.write(frame(VERSION, code, 0, ADDRESS_IPV4, 0, 0, 0, 0, 0, 0));
    }

    public static byte[] connectRequest(String host, int port) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);

        out.writeByte(VERSION);
        out.writeByte(COMMAND_CONNECT);
        out.writeByte(0);

        InetAddress literal = parseLiteral(host);
        if (literal != null) {
            out.writeByte(literal instanceof Inet4Address ? ADDRESS_IPV4 : ADDRESS_IPV6);
            out.write(literal.getAddress());
        } else {
            byte[] domain = host.getBytes(StandardCharsets.UTF_8);
            if (domain.length > MAX_FIELD_LENGTH) {
                throw new IOException("Hostname too long");
            }

            out.writeByte(ADDRESS_DOMAIN);
            out.writeByte(domain.length);
            out.write(domain);
        }

        out.writeShort(port);

        return buffer.toByteArray();
    }

    public static byte[] loginRequest(byte[] username, byte[] password) throws IOException {
        if (username.length > MAX_FIELD_LENGTH || password.length > MAX_FIELD_LENGTH) {
            throw new IOException("SOCKS5 credentials too long");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        buffer.write(AUTH_VERSION);
        buffer.write(username.length);
        buffer.writeBytes(username);
        buffer.write(password.length);
        buffer.writeBytes(password);

        return buffer.toByteArray();
    }

    @Nullable
    private static InetAddress parseLiteral(String host) {
        try {
            return InetAddress.ofLiteral(host);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
