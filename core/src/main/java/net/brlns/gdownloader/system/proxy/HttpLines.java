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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class HttpLines {

    private static final int MAX_LINE_LENGTH = 16 * 1024;
    private static final int MAX_HEADER_COUNT = 200;

    public static List<String> readHead(InputStream in) throws IOException {
        List<String> head = new ArrayList<>();
        head.add(readLine(in));

        String header;
        while (!(header = readLine(in)).isEmpty()) {
            if (head.size() > MAX_HEADER_COUNT) {
                throw new IOException("Too many headers");
            }

            head.add(header);
        }

        return head;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();

        int next;
        while ((next = in.read()) != -1 && next != '\n') {
            if (next != '\r') {
                line.append((char)next);
            }

            if (line.length() > MAX_LINE_LENGTH) {
                throw new IOException("Line too long");
            }
        }

        if (next == -1 && line.isEmpty()) {
            throw new IOException("Connection closed before the message was complete");
        }

        return line.toString();
    }
}
