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

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.net.Authenticator;
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
import net.brlns.gdownloader.system.proxy.BandwidthThrottle;
import net.brlns.gdownloader.system.proxy.ProxyAuthenticator;
import net.brlns.gdownloader.system.proxy.ThrottlingProxyServer;
import net.brlns.gdownloader.system.proxy.UpstreamConnector;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class HttpManager implements AutoCloseable {

    private final GDownloader main;

    private final AtomicReference<HttpClient> globalHttpClient = new AtomicReference<>();

    private final BandwidthThrottle globalThrottle;
    private final UpstreamConnector upstreamConnector;
    private final ThrottlingProxyServer throttlingProxy;

    public HttpManager(GDownloader mainIn) {
        main = mainIn;

        globalThrottle = new BandwidthThrottle(
            () -> main.getConfig().getGlobalMaxDownloadSpeedBytesPerSecond());

        upstreamConnector = new UpstreamConnector(
            () -> main.getConfig().getProxySettings());

        throttlingProxy = new ThrottlingProxyServer(globalThrottle, upstreamConnector);

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

    public BandwidthThrottle getGlobalThrottle() {
        return globalThrottle;
    }

    @Nullable
    public String getThrottlingProxyUrl() {
        if (main.getConfig().getGlobalMaxDownloadSpeedBytesPerSecond() <= 0
            || !throttlingProxy.start()) {
            return null;
        }

        return throttlingProxy.getUrl();
    }

    @Nullable
    public String getDownloaderProxyUrl() {
        String throttlingProxyUrl = getThrottlingProxyUrl();

        return throttlingProxyUrl != null ? throttlingProxyUrl : getUpstreamProxyUrl();
    }

    @Nullable
    public String getUpstreamProxyUrl() {
        return main.getConfig().getProxySettings().createProxyUrl();
    }

    @Override
    public void close() {
        throttlingProxy.close();
    }

    private void rebuildClient(ProxySettings settings) {
        Proxy proxy = settings.createProxy();

        // For whatever reason, Java decided this is something that should be applied globally.
        Authenticator.setDefault(proxy.type() != Proxy.Type.DIRECT && settings.hasAuthentication()
            ? new ProxyAuthenticator(settings)
            : null);

        HttpClient.Builder builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
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
                builder.authenticator(new ProxyAuthenticator(settings));
            }
        } else {
            builder.proxy(HttpClient.Builder.NO_PROXY);
        }

        globalHttpClient.set(builder.build());
    }
}
