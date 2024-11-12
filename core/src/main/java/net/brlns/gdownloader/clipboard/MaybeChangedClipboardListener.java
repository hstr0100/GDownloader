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

import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Unreliable fallback listener that does not rely on changing clipboard ownership.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class MaybeChangedClipboardListener implements IClipboardListener {

    private final AtomicBoolean maybeOcasionallyChangedIfLucky = new AtomicBoolean(false);

    public MaybeChangedClipboardListener() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .addFlavorListener((changed) -> {
                if (log.isDebugEnabled()) {
                    log.debug("Maybe detected a miracle");
                }

                maybeOcasionallyChangedIfLucky.set(true);
            });
    }

    @Override
    public boolean clipboardHasChanged() {
        if (maybeOcasionallyChangedIfLucky.get()) {
            maybeOcasionallyChangedIfLucky.set(false);

            return true;
        }

        return false;
    }
}
