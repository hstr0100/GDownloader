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

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import net.brlns.gdownloader.settings.ProxySettings;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ProxyAuthenticator extends Authenticator {

    private final String host;
    private final int port;
    private final String username;
    private final char[] password;

    public ProxyAuthenticator(ProxySettings settings) {
        host = settings.getHost();
        port = settings.getPort();
        username = settings.getUsername();
        password = settings.getPassword().toCharArray();
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestingPort() != port || !host.equalsIgnoreCase(getRequestingHost())) {
            return null;
        }

        return new PasswordAuthentication(username, password);
    }
}
