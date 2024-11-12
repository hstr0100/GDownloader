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
package net.brlns.gdownloader.clipboard;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public abstract class AbstractClipboardListener implements IClipboardListener {

    protected final AtomicLong skipUntil = new AtomicLong(0);

    @Override
    public void skipFor(TimeUnit unit, long value) {
        long skipDurationMillis = TimeUnit.MILLISECONDS.convert(value, unit);
        long expirationTime = System.currentTimeMillis() + skipDurationMillis;

        skipUntil.set(expirationTime);
    }

    @Override
    public boolean clipboardHasChanged() {
        // Early call to let the listener update its internal state regardless of the result.
        boolean hasChanged = detectClipboardChange();

        // We ignore changes during startup or right after toggling clipboard monitor.
        long currentTime = System.currentTimeMillis();
        if (currentTime < skipUntil.get()) {
            return false;
        }

        return hasChanged;
    }

    protected abstract boolean detectClipboardChange();
}
