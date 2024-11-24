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
package net.brlns.gdownloader.ui;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class EDTMonitor extends RepaintManager {

    @Override
    public synchronized void addInvalidComponent(JComponent component) {
        checkThread();

        super.addInvalidComponent(component);
    }

    @Override
    public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        checkThread();

        super.addDirtyRegion(c, x, y, w, h);
    }

    private void checkThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.error("EDT violation detected!");

            Thread.dumpStack();
        }
    }

    public static void install() {
        RepaintManager.setCurrentManager(new EDTMonitor());
    }
}
