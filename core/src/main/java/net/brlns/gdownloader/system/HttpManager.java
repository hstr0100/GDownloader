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
package net.brlns.gdownloader.system;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.ProxySettings;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class HttpManager {

    private final GDownloader main;

    private final AtomicReference<HttpClient> globalHttpClient = new AtomicReference<>();

    public HttpManager(GDownloader mainIn) {
        main = mainIn;

        ProxySettings initialSettings = main.getConfig().getProxySettings();
        rebuildClient(initialSettings);
    }

    public void updateProxySettings(ProxySettings settings) {
        // Different method in case we ever need to debounce this.
        rebuildClient(settings);
    }

    public HttpClient getClient() {
        return globalHttpClient.get();
    }

    private void rebuildClient(ProxySettings settings) {
        Proxy proxy = settings.createProxy();

        HttpClient.Builder builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2);

        if (proxy.type() != Proxy.Type.DIRECT) {
            builder.proxy(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(proxy);
                }

                @Override
                public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {

                }
            });

            if (settings.hasAuthentication()) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                            settings.getUsername(),
                            settings.getPassword().toCharArray()
                        );
                    }
                });
            }
        } else {
            builder.proxy(HttpClient.Builder.NO_PROXY);
        }

        globalHttpClient.set(builder.build());
    }
}
