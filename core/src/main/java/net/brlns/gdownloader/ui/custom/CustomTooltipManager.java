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
package net.brlns.gdownloader.ui.custom;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import lombok.extern.slf4j.Slf4j;

/**
 * We completely ditched Swing's native tooltips because they were behaving erratically.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class CustomTooltipManager {

    private static final int INITIAL_DELAY_MS = 200;

    private static final int CURSOR_CLEARANCE = 16;

    private static CustomTooltipManager instance;

    public static synchronized void install() {
        if (instance == null) {
            instance = new CustomTooltipManager();
        }
    }

    private final Set<JComponent> detachedFromSwing
        = Collections.newSetFromMap(new WeakHashMap<>());

    private final Timer showTimer;

    private JComponent pendingOwner;
    private String pendingText;
    private Point lastMouseScreenPoint;

    private JWindow overlayWindow;
    private CustomTooltipOverlay overlayContent;
    private Window currentOwnerWindow;

    private CustomTooltipManager() {
        showTimer = new Timer(INITIAL_DELAY_MS, e -> showPending());
        showTimer.setRepeats(false);

        Toolkit.getDefaultToolkit().addAWTEventListener(this::handleEvent,
            AWTEvent.MOUSE_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK
            | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    private void handleEvent(AWTEvent event) {
        if (!(event instanceof MouseEvent me)) {
            return;
        }

        if (!(me.getSource() instanceof JComponent comp)) {
            return;
        }

        if (me.getID() == MouseEvent.MOUSE_ENTERED || me.getID() == MouseEvent.MOUSE_MOVED) {
            lastMouseScreenPoint = me.getLocationOnScreen();
        }

        switch (me.getID()) {
            case MouseEvent.MOUSE_ENTERED -> {
                String text = comp.getToolTipText(me);
                if (text == null || text.isBlank()) {
                    return;
                }

                detachFromSwingToolTipManager(comp);

                pendingOwner = comp;
                pendingText = text;
                showTimer.restart();
            }
            case MouseEvent.MOUSE_EXITED -> {
                if (comp == pendingOwner) {
                    showTimer.stop();
                    pendingOwner = null;
                }

                hide();
            }
            case MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL ->
                hide();
            default -> {
            }
        }
    }

    private void detachFromSwingToolTipManager(JComponent comp) {
        if (detachedFromSwing.add(comp)) {
            ToolTipManager.sharedInstance().unregisterComponent(comp);
        }
    }

    private void ensureWindow(Window owner) {
        if (overlayWindow != null && currentOwnerWindow == owner) {
            return;
        }

        if (overlayWindow != null) {
            overlayWindow.dispose();
        }

        overlayWindow = new JWindow();
        overlayWindow.setType(Window.Type.POPUP);
        overlayWindow.setFocusableWindowState(false);
        overlayWindow.setAlwaysOnTop(true);

        overlayContent = new CustomTooltipOverlay();
        overlayWindow.setContentPane(overlayContent);

        currentOwnerWindow = owner;
    }

    private void showPending() {
        JComponent owner = pendingOwner;
        String text = pendingText;
        if (owner == null || !owner.isShowing() || text == null || text.isBlank()) {
            return;
        }

        Window ownerWindow = SwingUtilities.getWindowAncestor(owner);
        if (ownerWindow == null) {
            return;
        }

        ensureWindow(ownerWindow);
        overlayContent.setText(text);

        Point cursor = lastMouseScreenPoint != null
            ? new Point(lastMouseScreenPoint)
            : MouseInfo.getPointerInfo().getLocation();

        Dimension size = overlayContent.getPreferredSize();
        Rectangle screenBounds = getCurrentScreenBounds(cursor);

        int x = cursor.x + CURSOR_CLEARANCE;
        int y = cursor.y + CURSOR_CLEARANCE;

        if (x + size.width > screenBounds.x + screenBounds.width) {
            x = cursor.x - size.width - CURSOR_CLEARANCE;
        }

        if (y + size.height > screenBounds.y + screenBounds.height) {
            y = cursor.y - size.height - CURSOR_CLEARANCE;
        }

        x = Math.max(screenBounds.x, x);
        y = Math.max(screenBounds.y, y);

        overlayWindow.setBounds(x, y, size.width, size.height);

        overlayWindow.setShape(new RoundRectangle2D.Double(
            0, 0, size.width, size.height,
            CustomTooltipOverlay.ARC, CustomTooltipOverlay.ARC));

        overlayWindow.setVisible(true);
    }

    private Rectangle getCurrentScreenBounds(Point screenPoint) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            if (bounds.contains(screenPoint)) {
                return bounds;
            }
        }

        try {
            return ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } catch (HeadlessException e) {
            return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
    }

    private void hide() {
        if (overlayWindow != null) {
            overlayWindow.setVisible(false);
        }
    }
}
