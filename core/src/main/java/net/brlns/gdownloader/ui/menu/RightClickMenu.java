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
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.EventListener;
import net.brlns.gdownloader.event.IEventListener;
import net.brlns.gdownloader.event.impl.NativeMouseClickEvent;
import net.brlns.gdownloader.ui.custom.CustomMenuButton;

import static net.brlns.gdownloader.ui.GUIManager.runOnEDT;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class RightClickMenu {

    private static final AtomicInteger WINDOW_ID = new AtomicInteger(0);

    private final Map<Integer, JWindow> openSubmenus = new HashMap<>();
    private boolean alwaysOnTop = false;

    public RightClickMenu(boolean alwaysOnTop) {
        this.alwaysOnTop = alwaysOnTop;
    }

    public void showMenu(Component parentComponent, RightClickMenuEntries actions,
        Collection<RightClickMenuEntries> dependents, int sourceX, int sourceY) {
        showMenu(parentComponent, actions, dependents, sourceX, sourceY, new ArrayList<>());
    }

    public void showMenu(Component parentComponent, RightClickMenuEntries actions,
        Collection<RightClickMenuEntries> dependents, int sourceX, int sourceY, List<Integer> hierarchy) {
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

        JPanel popupPanel = createPopupPanel(actions, dependents, id, popupWindow, hierarchy);
        popupWindow.add(popupPanel, BorderLayout.CENTER);
        popupWindow.pack();

        setPopupLocation(popupWindow, parentComponent, sourceX, sourceY);

        MenuWindowAdapter windowAdapter = new MenuWindowAdapter(popupWindow);
        windowAdapter.register();
        popupWindow.addWindowListener(windowAdapter);

        popupWindow.setVisible(true);
    }

    private JPanel createPopupPanel(RightClickMenuEntries actions, Collection<RightClickMenuEntries> dependents,
        int currentId, JWindow popupWindow, List<Integer> hierarchy) {
        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new GridLayout(actions.size(), 1));
        popupPanel.setBackground(Color.DARK_GRAY);
        popupPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popupPanel.setOpaque(true);

        for (Map.Entry<String, IMenuEntry> entry : actions.entrySet()) {
            String key = entry.getKey();
            IMenuEntry value = entry.getValue();
            JButton button = new CustomMenuButton(key);

            button.addActionListener(e -> {
                switch (value) {
                    // Runs itself and any other entry that matches the same key
                    case RunnableMenuEntry runnable -> {
                        closeHierarchy(hierarchy);
                        runnable.getRunnable().run();

                        // This will fail by design if any of the maps have incompatible values.
                        dependents.forEach(dependent -> {
                            RunnableMenuEntry depEntry = (RunnableMenuEntry)dependent.get(key);
                            if (depEntry != null) {
                                depEntry.getRunnable().run();
                            }
                        });
                    }
                    // Ignores all dependents and run only the main caller
                    case SingleActionMenuEntry single -> {
                        closeHierarchy(hierarchy);
                        single.getRunnable().run();
                    }
                    // Calls on all dependents to gather their values and returns them as a list to the main caller
                    case MultiActionMenuEntry<?> multi -> {
                        closeHierarchy(hierarchy);

                        List<IMenuEntry> allEntries = new ArrayList<>();
                        allEntries.add(multi);

                        dependents.forEach(dependent -> {
                            IMenuEntry depEntry = dependent.get(key);
                            if (depEntry instanceof MultiActionMenuEntry<?>) {
                                allEntries.add(depEntry);
                            }
                        });

                        multi.processActions(allEntries);
                    }
                    // Submenu entry, recurse
                    case NestedMenuEntry nested -> {
                        List<RightClickMenuEntries> depNested = new ArrayList<>();
                        dependents.forEach(dependent -> {
                            depNested.add((NestedMenuEntry)dependent.get(key));
                        });

                        showMenu(button, nested, depNested, button.getWidth(),
                            nested.size() <= 1 ? 0 : -((nested.size() - 1) * button.getHeight()), hierarchy);
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
                        List<RightClickMenuEntries> depNested = new ArrayList<>();
                        dependents.forEach(dependent -> {
                            depNested.add((NestedMenuEntry)dependent.get(key));
                        });

                        showMenu(button, nested, depNested, button.getWidth(),
                            nested.size() <= 1 ? 0 : -((nested.size() - 1) * button.getHeight()), hierarchy);
                    }
                }
            });

            popupPanel.add(button);
        }

        return popupPanel;
    }

    private void setPopupLocation(JWindow popupWindow, Component parentComponent, int x, int y) {
        GraphicsConfiguration graphicsConfig = parentComponent.getGraphicsConfiguration();
        Rectangle screenBounds = graphicsConfig.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfig);

        Rectangle usableBounds = new Rectangle(
            screenBounds.x + screenInsets.left,
            screenBounds.y + screenInsets.top,
            screenBounds.width - screenInsets.left - screenInsets.right,
            screenBounds.height - screenInsets.top - screenInsets.bottom
        );

        Point locationOnScreen = parentComponent.getLocationOnScreen();
        int choosenX = locationOnScreen.x + x;
        int choosenY = locationOnScreen.y + y;

        Dimension popupSize = popupWindow.getSize();
        int popupX = Math.max(usableBounds.x, Math.min(choosenX, usableBounds.x + usableBounds.width - popupSize.width));
        int popupY = Math.max(usableBounds.y, Math.min(choosenY, usableBounds.y + usableBounds.height - popupSize.height));

        popupWindow.setLocation(popupX, popupY);
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

    private void closeHierarchy(List<Integer> hierarchy) {
        for (int id : hierarchy) {
            JWindow submenu = openSubmenus.get(id);
            if (submenu != null) {
                submenu.dispose();
                openSubmenus.remove(id);
            }
        }

        hierarchy.clear();
    }

    private static AWTEventListener createFallbackMouseListener(JWindow popupWindow) {
        return (AWTEvent event) -> {
            if (event instanceof MouseEvent mouseEvent) {
                if (mouseEvent.getID() != MouseEvent.MOUSE_PRESSED) {
                    return;
                }

                Point clickPoint = mouseEvent.getLocationOnScreen();
                Rectangle popupBounds = popupWindow.getBounds();
                boolean shouldDispose = false;

                if (!popupBounds.contains(clickPoint)) {
                    shouldDispose = true;
                } else {
                    Component component = SwingUtilities.getDeepestComponentAt(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());

                    if (component == null || !SwingUtilities.isDescendingFrom(component, popupWindow)) {
                        shouldDispose = true;
                    }
                }

                if (shouldDispose) {
                    popupWindow.setVisible(false);
                    popupWindow.dispose();
                }
            }
        };
    }

    private static class MenuWindowAdapter extends WindowAdapter implements IEventListener {

        private final JWindow popupWindow;
        private final AWTEventListener globalMouseListener;

        public MenuWindowAdapter(JWindow popupWindowIn) {
            popupWindow = popupWindowIn;
            globalMouseListener = createFallbackMouseListener(popupWindowIn);
        }

        public void register() {
            EventDispatcher.register(this);
            Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener, AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override
        public void windowClosed(WindowEvent e) {
            EventDispatcher.unregister(this);
            Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener);
        }

        @EventListener
        public void handle(NativeMouseClickEvent event) {
            runOnEDT(() -> {
                Rectangle bounds = popupWindow.getBounds();

                if (!bounds.contains(event.getPoint())) {
                    popupWindow.setVisible(false);
                    popupWindow.dispose();
                }
            });
        }
    }
}
