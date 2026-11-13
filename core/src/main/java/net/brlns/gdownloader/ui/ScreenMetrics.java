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
package net.brlns.gdownloader.ui;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ScreenMetrics {

    private static final long REFRESH_INTERVAL_MS = 2000;

    private static final AtomicReference<Rectangle> cachedScreenBounds = new AtomicReference<>();
    private static final AtomicLong lastRefresh = new AtomicLong();

    private ScreenMetrics() {
    }

    public static Rectangle getPrimaryScreenBounds() {
        long now = System.currentTimeMillis();
        Rectangle bounds = cachedScreenBounds.get();

        if (bounds == null || now - lastRefresh.get() > REFRESH_INTERVAL_MS) {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            bounds = gs.getDefaultConfiguration().getBounds();

            cachedScreenBounds.set(bounds);
            lastRefresh.set(now);
        }

        return bounds;
    }

    public static void invalidate() {
        cachedScreenBounds.set(null);
    }

    public static Rectangle getUsableArea(GraphicsConfiguration config) {
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);

        return new Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom);
    }

    public static Dimension getSmallestUsableScreenSize() {
        int width = Integer.MAX_VALUE;
        int height = Integer.MAX_VALUE;

        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle usable = getUsableArea(device.getDefaultConfiguration());

            width = Math.min(width, usable.width);
            height = Math.min(height, usable.height);
        }

        return new Dimension(width, height);
    }

    public static double getLargestFittingScale(int width, int height) {
        double bestFit = 1.0;

        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            double currentScale = config.getDefaultTransform().getScaleX();
            Rectangle usable = getUsableArea(config);

            double fit = Math.min(
                usable.width * currentScale / width,
                usable.height * currentScale / height);

            bestFit = Math.max(bestFit, fit);
        }

        return bestFit;
    }

    public static final class Debouncer {

        private final Timer timer;

        public Debouncer(int delayMs, Runnable action) {
            timer = new Timer(delayMs, e -> action.run());
            timer.setRepeats(false);
        }

        public void trigger() {
            if (SwingUtilities.isEventDispatchThread()) {
                timer.restart();
            } else {
                SwingUtilities.invokeLater(timer::restart);
            }
        }

        public void stop() {
            timer.stop();
        }
    }
}
