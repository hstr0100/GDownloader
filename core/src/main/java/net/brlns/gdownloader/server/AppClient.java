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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.server.result.AbstractResult;
import net.brlns.gdownloader.server.result.ResultEnum;
import net.brlns.gdownloader.server.result.StatusResult;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class AppClient {

    private static final int TIMEOUT_MS = 3000;

    private final GDownloader main;

    public AppClient(GDownloader mainIn) {
        main = mainIn;
    }

    public AbstractResult sendCommand(String command) {
        try (
            Socket socket = new Socket()) {

            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), AppServer.PORT), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                if (log.isDebugEnabled()) {
                    log.info("Sending client command: {}", command);
                }

                out.println(command);

                String resultJson = in.readLine();

                if (log.isDebugEnabled()) {
                    log.info("Server response: {}", resultJson);
                }

                AbstractResult result = GDownloader.OBJECT_MAPPER.readValue(resultJson, AbstractResult.class);
                if (result == null) {
                    throw new IllegalArgumentException("Unmapped result: " + resultJson);
                }

                if (log.isDebugEnabled()) {
                    log.info("Deserialized server response: {}", result);
                }

                return result;
            }
        } catch (ConnectException e) {
            log.warn("Could not find a running GDownloader instance.");

            return new StatusResult(ResultEnum.NOT_RUNNING);
        } catch (SocketTimeoutException e) {
            log.warn("Connection timed out");

            return new StatusResult(ResultEnum.TIMEOUT);
        } catch (IOException e) {
            log.warn("Communication error", e);

            return new StatusResult(ResultEnum.IO_ERROR);
        }
    }

    public boolean tryWakeSingleInstance() {
        AbstractResult result = sendCommand("wake-up");
        if (result instanceof StatusResult resultIn) {
            if (resultIn.getResult() == ResultEnum.SUCCESS) {
                return true;
            }
        }

        return false;
    }

    // TODO: shutdown
}
