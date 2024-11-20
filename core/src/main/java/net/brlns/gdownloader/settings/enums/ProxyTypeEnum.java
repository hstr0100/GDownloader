/*
 * Copyright (C) 2024 hstr0100
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
package net.brlns.gdownloader.settings.enums;

import java.net.Proxy;
import lombok.Getter;

import static net.brlns.gdownloader.lang.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum ProxyTypeEnum implements ISettingsEnum {
    NO_PROXY(Proxy.Type.DIRECT, "", 0),
    HTTP(Proxy.Type.HTTP, "http", 8080),
    HTTPS(Proxy.Type.HTTP, "https", 443),
    SOCKS4(Proxy.Type.SOCKS, "socks4", 1080),
    SOCKS5(Proxy.Type.SOCKS, "socks5", 1080);
    // TODO: DIRECT, detect NIC

    private final Proxy.Type type;
    private final String protocol;
    private final int defaultPort;

    private ProxyTypeEnum(Proxy.Type typeIn, String protocolIn, int defaultPortIn) {
        type = typeIn;
        protocol = protocolIn;
        defaultPort = defaultPortIn;
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return this == NO_PROXY ? l10n("settings.proxy.no_proxy") : protocol.toLowerCase();
    }
}
