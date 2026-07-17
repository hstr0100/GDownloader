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

import java.time.Duration;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: implement
public class RetryLaterException extends HostResolverException {

    private final Duration retryAfter;

    public RetryLaterException(Duration retryAfterIn, String message) {
        super(message, true);

        retryAfter = retryAfterIn;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
