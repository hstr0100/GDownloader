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

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class SmoothScroller {

    public static void install(JScrollPane scrollPane) {
        for (MouseWheelListener listener : scrollPane.getMouseWheelListeners()) {
            scrollPane.removeMouseWheelListener(listener);
        }

        scrollPane.addMouseWheelListener(new InertiaWheelListener(scrollPane));
    }

    private static class InertiaWheelListener implements MouseWheelListener {

        private static final double PIXELS_PER_NOTCH = 100.0;
        private static final double MAX_VELOCITY_PX_PER_SEC = 8000.0;
        private static final double HALF_LIFE_MS = 50.0; // lower = snappier, higher = glidey
        private static final double STOP_VELOCITY_PX_PER_SEC = 30.0;
        private static final long BURST_WINDOW_MS = 100;
        private static final double MAX_BURST_FACTOR = 3.5;

        // px -> velocity so decay lands roughly on the requested distance
        private static final double IMPULSE_TO_VELOCITY = 1000.0 * Math.log(2) / HALF_LIFE_MS;

        private final JScrollPane scrollPane;
        private final Timer timer;

        private double velocity = 0.0;
        private double remainder = 0.0;
        private long lastEventTime = 0;
        private long lastTickTimeNanos = 0;

        private InertiaWheelListener(JScrollPane scrollPaneIn) {
            scrollPane = scrollPaneIn;

            timer = new Timer(10, e -> tick());
            timer.setCoalesce(true);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar == null || !bar.isVisible()) {
                return;
            }

            long now = System.currentTimeMillis();
            long dt = lastEventTime == 0 ? Long.MAX_VALUE : now - lastEventTime;
            lastEventTime = now;

            double burstFactor = 1.0;
            if (dt < BURST_WINDOW_MS) {
                burstFactor = Math.min(MAX_BURST_FACTOR, 1.0 + (BURST_WINDOW_MS - dt) / (double)BURST_WINDOW_MS);
            }

            double desiredPixels = e.getPreciseWheelRotation() * PIXELS_PER_NOTCH * burstFactor;
            double impulse = desiredPixels * IMPULSE_TO_VELOCITY;

            if (Math.signum(impulse) != Math.signum(velocity)) {
                velocity = impulse; // reversed or idle, don't fight the old glide
            } else {
                velocity += impulse;
            }

            velocity = Math.clamp(velocity, -MAX_VELOCITY_PX_PER_SEC, MAX_VELOCITY_PX_PER_SEC);

            if (!timer.isRunning()) {
                lastTickTimeNanos = System.nanoTime();

                timer.start();
            }
        }

        private void tick() {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar == null) {
                stop();

                return;
            }

            long now = System.nanoTime();
            double dtSeconds = (now - lastTickTimeNanos) / 1_000_000_000.0;
            lastTickTimeNanos = now;
            dtSeconds = Math.min(dtSeconds, 0.05); // clamp in case of a hiccup

            if (Math.abs(velocity) < STOP_VELOCITY_PX_PER_SEC) {
                stop();

                return;
            }

            double decay = Math.pow(0.5, dtSeconds * 1000.0 / HALF_LIFE_MS);
            velocity *= decay;

            double delta = velocity * dtSeconds + remainder;
            int intDelta = (int)delta;
            remainder = delta - intDelta;

            if (intDelta == 0) {
                return;
            }

            int min = bar.getMinimum();
            int max = bar.getMaximum() - bar.getVisibleAmount();
            int newValue = Math.clamp(bar.getValue() + intDelta, min, max);
            bar.setValue(newValue);

            if (newValue == min || newValue == max) {
                stop();
            }
        }

        private void stop() {
            velocity = 0.0;
            remainder = 0.0;

            timer.stop();
        }
    }
}
