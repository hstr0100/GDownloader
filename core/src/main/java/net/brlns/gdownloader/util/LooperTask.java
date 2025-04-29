/*
 * Copyright (C) 2025 @hstr0100
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
package net.brlns.gdownloader.util;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@RequiredArgsConstructor
public class LooperTask implements Runnable {

    private final AtomicBoolean failedOnce = new AtomicBoolean();

    private final Runnable action;

    @Override
    public void run() {
        spin(action);
    }

    private void spin(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            if (failedOnce.compareAndSet(false, true)) {
                log.error("Looper task has failed at least once: {}", e.getMessage());

                if (log.isDebugEnabled()) {
                    log.error("Exception: ", e);
                }
            }
        }
    }
}
