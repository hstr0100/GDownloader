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
package net.brlns.gdownloader.downloader.hosts;

import jakarta.annotation.Nullable;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;
import net.brlns.gdownloader.settings.downloader.HostResolverSettings;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Value
@Builder
public class HostResolverContext {

    private final HttpClient httpClient;

    private final HostResolverSettings settings;

    private final Duration requestTimeout;

    @Nullable
    private final String password;

    private final Supplier<Boolean> cancelHook;

    @Nullable
    private final StatusListener statusListener;

    public boolean isCancelled() {
        return cancelHook != null && cancelHook.get();
    }

    public void notifyStatus(String translationKey, Object... args) {
        if (statusListener != null) {
            statusListener.onStatus(translationKey, args);
        }
    }

    @FunctionalInterface
    public interface StatusListener {

        void onStatus(String translationKey, Object... args);
    }
}
