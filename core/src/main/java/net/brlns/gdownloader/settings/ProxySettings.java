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
package net.brlns.gdownloader.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import lombok.Data;
import net.brlns.gdownloader.settings.enums.ProxyTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxySettings {

    @JsonProperty("Enabled")
    private boolean enabled = false;

    @JsonProperty("Type")
    private ProxyTypeEnum proxyType = ProxyTypeEnum.NO_PROXY;

    @JsonProperty("Host")
    private String host = "";

    @JsonProperty("Port")
    private int port = 0;

    @JsonProperty("Username")
    private String username = "";

    @JsonProperty("Password")
    private String password = "";

    @JsonIgnore
    public Proxy createProxy() {
        if (!isValid() || !enabled) {
            return Proxy.NO_PROXY;
        }

        Proxy proxy = new Proxy(proxyType.getType(), new InetSocketAddress(host, port));

        if (isNonEmptyString(username) && isNonEmptyString(password)) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }

        return proxy;
    }

    @JsonIgnore
    @Nullable
    public String createProxyUrl() {
        if (!isValid() || !enabled) {
            return null;
        }

        StringBuilder proxyUrl = new StringBuilder();

        proxyUrl.append(proxyType.getProtocol()).append("://");

        if (isNonEmptyString(username) && isNonEmptyString(password)) {
            proxyUrl.append(username).append(":").append(password).append("@");
        }

        proxyUrl.append(host).append(":").append(port);

        return proxyUrl.toString();
    }

    @JsonIgnore
    public boolean isValid() {
        return proxyType != null && proxyType != ProxyTypeEnum.NO_PROXY
            && isNonEmptyString(host) && (host.contains(".") || host.contains(":"))//TODO validate host
            && port > 0 && port < Short.MAX_VALUE;
    }

    @JsonIgnore
    private boolean isNonEmptyString(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
