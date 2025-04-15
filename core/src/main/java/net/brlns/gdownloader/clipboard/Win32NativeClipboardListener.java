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

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class Win32NativeClipboardListener extends AbstractClipboardListener {

    private final AtomicInteger lastClipboardSequenceNumber = new AtomicInteger(-1);

    @Override
    protected boolean detectClipboardChange() {
        try {
            int currentSequenceNumber = User32.INSTANCE.GetClipboardSequenceNumber();

            int previousValue = lastClipboardSequenceNumber.get();
            if (currentSequenceNumber != 0 && (previousValue == -1 || currentSequenceNumber != previousValue)) {
                if (log.isDebugEnabled()) {
                    log.debug("Detected Win32 clipboard change, seq {}", currentSequenceNumber);
                }

                lastClipboardSequenceNumber.set(currentSequenceNumber);
                return true;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Std call has failed: ", e);
            }
        }

        return false;
    }

    private interface User32 extends StdCallLibrary {

        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetClipboardSequenceNumber();
    }
}
