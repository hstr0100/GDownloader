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
package net.brlns.gdownloader.ui.menu;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import net.brlns.gdownloader.ui.custom.CustomMenuButton;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class RightClickMenu {

    private static final AtomicInteger WINDOW_ID = new AtomicInteger(0);

    private final Map<Integer, JWindow> openSubmenus = new HashMap<>();
    private boolean alwaysOnTop = false;

    public RightClickMenu(boolean alwaysOnTop) {
        this.alwaysOnTop = alwaysOnTop;
    }

    public void showMenu(Component parentComponent, Map<String, IMenuEntry> actions, int x, int y) {
        showMenu(parentComponent, actions, x, y, new ArrayList<>());
    }

    public void showMenu(Component parentComponent, Map<String, IMenuEntry> actions, int x, int y, List<Integer> hierarchy) {
        assert SwingUtilities.isEventDispatchThread();

        if (actions.isEmpty()) {
            return;
        }

        JWindow popupWindow = new JWindow();
        popupWindow.setLayout(new BorderLayout());
        popupWindow.setAlwaysOnTop(alwaysOnTop);

        int id = WINDOW_ID.incrementAndGet();
        openSubmenus.put(id, popupWindow);
        hierarchy.add(id);

        JPanel popupPanel = createPopupPanel(actions, id, popupWindow, hierarchy);
        popupWindow.add(popupPanel, BorderLayout.CENTER);
        popupWindow.pack();

        setPopupLocation(popupWindow, parentComponent, x, y);

        AWTEventListener globalMouseListener = createGlobalMouseListener(popupWindow);
        Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener, AWTEvent.MOUSE_EVENT_MASK);

        popupWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener);
            }
        });

        popupWindow.setVisible(true);
    }

    private JPanel createPopupPanel(Map<String, IMenuEntry> actions, int currentId, JWindow popupWindow, List<Integer> hierarchy) {
        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new GridLayout(actions.size(), 1));
        popupPanel.setBackground(Color.DARK_GRAY);
        popupPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popupPanel.setOpaque(true);

        for (Map.Entry<String, IMenuEntry> entry : actions.entrySet()) {
            JButton button = new CustomMenuButton(entry.getKey());
            IMenuEntry value = entry.getValue();

            button.addActionListener(e -> {
                switch (value) {
                    case RunnableMenuEntry runnable -> {
                        runnable.getRunnable().run();
                        closeHierarchy(hierarchy);
                    }
                    case NestedMenuEntry nested -> {
                        showMenu(button, nested, button.getWidth(), -button.getHeight(), hierarchy);
                    }
                    default ->
                        throw new IllegalArgumentException("Unhandled menu entry");
                }
            });

            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    closeOtherSubmenus(currentId, popupWindow, hierarchy);

                    if (value instanceof NestedMenuEntry nested) {
                        showMenu(button, nested, button.getWidth(), -button.getHeight(), hierarchy);
                    }
                }
            });

            popupPanel.add(button);
        }

        return popupPanel;
    }

    private void setPopupLocation(JWindow popupWindow, Component parentComponent, int x, int y) {
        Point locationOnScreen = parentComponent.getLocationOnScreen();
        popupWindow.setLocation(locationOnScreen.x + x, locationOnScreen.y + y);
    }

    private void closeOtherSubmenus(int currentWindowId, JWindow currentWindow, List<Integer> hierarchy) {
        Iterator<Map.Entry<Integer, JWindow>> iterator = openSubmenus.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, JWindow> entry = iterator.next();
            int id = entry.getKey();
            JWindow submenu = entry.getValue();

            if (submenu != currentWindow && (!hierarchy.contains(id) || currentWindowId < id)) {
                submenu.dispose();
                iterator.remove();
            }
        }
    }

    private AWTEventListener createGlobalMouseListener(JWindow popupWindow) {
        return (AWTEvent event) -> {
            if (event.getID() == MouseEvent.MOUSE_CLICKED) {
                MouseEvent me = (MouseEvent)event;
                Component component = SwingUtilities.getDeepestComponentAt(me.getComponent(), me.getX(), me.getY());

                if (component == null || !SwingUtilities.isDescendingFrom(component, popupWindow)) {
                    popupWindow.dispose();
                }
            }
        };
    }

    private void closeHierarchy(List<Integer> hierarchy) {
        for (Integer id : hierarchy) {
            JWindow submenu = openSubmenus.get(id);
            if (submenu != null) {
                submenu.dispose();
                openSubmenus.remove(id);
            }
        }

        hierarchy.clear();
    }
}
