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

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CtrlCNativeClipboardListener extends AbstractClipboardListener {

    private final AtomicBoolean hasChanged = new AtomicBoolean(false);

    public CtrlCNativeClipboardListener() {
        GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                try {
                    if ((e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0
                        && e.getKeyCode() == NativeKeyEvent.VC_C) {
                        if (log.isDebugEnabled()) {
                            log.debug("Detected Ctrl+C clipboard change");
                        }

                        hasChanged.set(true);
                    }
                } catch (Exception ex) {
                    log.error("JNativeHook exception", ex);
                }
            }

            @Override
            public void nativeKeyReleased(NativeKeyEvent e) {
                // Not implemented
            }

            @Override
            public void nativeKeyTyped(NativeKeyEvent e) {
                // Not implemented
            }
        });
    }

    @Override
    public boolean detectClipboardChange() {
        if (hasChanged.get()) {
            hasChanged.set(false);

            return true;
        }

        return false;
    }
}
