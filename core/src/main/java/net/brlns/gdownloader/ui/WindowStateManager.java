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

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.WindowPlacement;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class WindowStateManager {

    private static final int PERSIST_DEBOUNCE_MS = 400;

    private static final int MIN_SANE_DIMENSION = 50;
    private static final int MAX_SANE_DIMENSION = 20000;

    private static final Map<Window, Tracker> TRACKERS = new WeakHashMap<>();

    public static void manage(GDownloader main, Window window, String windowId, Runnable defaultLayout) {
        assert SwingUtilities.isEventDispatchThread();

        boolean restored = restore(main, window, windowId);

        track(main, window, windowId, defaultLayout);

        if (!restored && defaultLayout != null) {
            defaultLayout.run();
        }
    }

    public static boolean restore(GDownloader main, Window window, String windowId) {
        assert SwingUtilities.isEventDispatchThread();

        if (!main.getConfig().isRememberWindowPlacement()) {
            return false;
        }

        WindowPlacement placement = main.getConfig().getWindowPlacements().get(windowId);
        if (placement == null
            || !isSaneDimension(placement.getWidth())
            || !isSaneDimension(placement.getHeight())) {
            return false;
        }

        Rectangle desired = new Rectangle(
            placement.getX(), placement.getY(),
            placement.getWidth(), placement.getHeight());

        window.setBounds(clampToVisibleScreen(desired));

        if (placement.isMaximized() && window instanceof Frame frame) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }

        log.debug("Restored window placement for {}: {}", windowId, placement);

        return true;
    }

    private static boolean isSaneDimension(int value) {
        return value >= MIN_SANE_DIMENSION && value <= MAX_SANE_DIMENSION;
    }

    public static void track(GDownloader main, Window window, String windowId) {
        track(main, window, windowId, null);
    }

    public static void track(GDownloader main, Window window, String windowId, Runnable defaultLayout) {
        assert SwingUtilities.isEventDispatchThread();

        if (TRACKERS.containsKey(window)) {
            return;
        }

        Tracker tracker = new Tracker(main, window, windowId, defaultLayout);
        TRACKERS.put(window, tracker);

        window.addComponentListener(tracker.componentListener);

        if (window instanceof Frame frame) {
            frame.addWindowStateListener(tracker.windowStateListener);
        }
    }

    public static void unmanage(Window window) {
        Tracker tracker = TRACKERS.remove(window);
        if (tracker != null) {
            tracker.debouncer.stop();
        }
    }

    public static void resetAll(GDownloader main) {
        assert SwingUtilities.isEventDispatchThread();

        main.getConfig().getWindowPlacements().clear();
        main.updateConfig();

        List<Tracker> snapshot = new ArrayList<>(TRACKERS.values());

        for (Tracker tracker : snapshot) {
            if (tracker.defaultLayout != null && tracker.window.isDisplayable()) {
                tracker.defaultLayout.run();
            }
        }
    }

    public static Rectangle clampToVisibleScreen(Rectangle desired) {
        if (desired.width <= 0 || desired.height <= 0) {
            return desired;
        }

        GraphicsEnvironment ge;
        try {
            ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        } catch (HeadlessException e) {
            return desired;
        }

        GraphicsConfiguration targetConfig = null;
        long bestOverlap = 0;

        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration config = gd.getDefaultConfiguration();

            Rectangle intersection = config.getBounds().intersection(desired);
            if (intersection.isEmpty()) {
                continue;
            }

            long overlap = (long)intersection.width * (long)intersection.height;
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                targetConfig = config;
            }
        }

        boolean recenter = targetConfig == null;

        if (targetConfig == null) {
            try {
                targetConfig = ge.getDefaultScreenDevice().getDefaultConfiguration();
            } catch (HeadlessException e) {
                return desired;
            }
        }

        Rectangle usableArea = usableAreaOf(targetConfig);

        int width = Math.min(desired.width, usableArea.width);
        int height = Math.min(desired.height, usableArea.height);

        int x;
        int y;
        if (recenter) {
            x = usableArea.x + Math.max(0, (usableArea.width - width) / 2);
            y = usableArea.y + Math.max(0, (usableArea.height - height) / 2);
        } else {
            x = Math.clamp(desired.x, usableArea.x, usableArea.x + usableArea.width - width);
            y = Math.clamp(desired.y, usableArea.y, usableArea.y + usableArea.height - height);
        }

        return new Rectangle(x, y, width, height);
    }

    private static Rectangle usableAreaOf(GraphicsConfiguration config) {
        Rectangle bounds = config.getBounds();

        try {
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);

            return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(1, bounds.width - insets.left - insets.right),
                Math.max(1, bounds.height - insets.top - insets.bottom));
        } catch (HeadlessException e) {
            return bounds;
        }
    }

    private static final class Tracker {

        private final GDownloader main;
        private final Window window;
        private final String windowId;
        private final Runnable defaultLayout;

        private final ScreenMetrics.Debouncer debouncer;

        private final ComponentAdapter componentListener;
        private final WindowStateListener windowStateListener;

        Tracker(GDownloader mainIn, Window windowIn, String windowIdIn, Runnable defaultLayoutIn) {
            main = mainIn;
            window = windowIn;
            windowId = windowIdIn;
            defaultLayout = defaultLayoutIn;

            debouncer = new ScreenMetrics.Debouncer(PERSIST_DEBOUNCE_MS, this::persist);

            componentListener = new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent e) {
                    debouncer.trigger();
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    debouncer.trigger();
                }
            };

            windowStateListener = e -> debouncer.trigger();
        }

        private void persist() {
            if (!main.getConfig().isRememberWindowPlacement()) {
                return;
            }

            if (!window.isShowing() && !window.isDisplayable()) {
                return;
            }

            boolean maximized = window instanceof Frame frame
                && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;

            WindowPlacement placement = main.getConfig().getWindowPlacements()
                .computeIfAbsent(windowId, k -> new WindowPlacement());

            if (!maximized) {
                Rectangle bounds = window.getBounds();

                placement.setX(bounds.x);
                placement.setY(bounds.y);
                placement.setWidth(bounds.width);
                placement.setHeight(bounds.height);
            }

            placement.setMaximized(maximized);

            main.updateConfig();
        }
    }
}
