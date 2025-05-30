/*
 * Copyright (C) 2025 hstr0100
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
package net.brlns.gdownloader.ui.message;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.util.CancelHook;

import static net.brlns.gdownloader.ui.GUIManager.*;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.message.MessageTypeEnum.WARNING;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class ToastMessenger extends AbstractMessenger {

    private static final int ARC_SIZE = 20;
    private static final int MARGIN = 10;

    private final CancelHook cancelHook = new CancelHook();
    private final AtomicBoolean windowVisible = new AtomicBoolean(true);

    private final JFrame targetWindow;

    private final ComponentAdapter componentAdapter;

    private JDialog messageDialog;
    private JPanel messagePanel;

    private ToastMessenger(JFrame targetWindowIn) {
        targetWindow = targetWindowIn;

        componentAdapter = new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                // FINALLY it shows its face. *cracks knuckles*
                // NOW you can be positively NUKED out of here.
                windowVisible.set(true);
                cancelHook.set(true);
                close();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                windowVisible.set(false);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                updateLocation(targetWindow);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                updateLocation(targetWindow);
            }
        };
    }

    @Override
    protected void close() {
        assert SwingUtilities.isEventDispatchThread();

        if (!targetWindow.isVisible() || !windowVisible.get()) {
            // If targetWindow isn't visible, this dialog becomes an immortal ghost window. the X button becomes a cruel joke.
            // You can try to close it all you like, but it has already transcended your feeble UI logic.
            return;
        }

        if (messageDialog != null) {
            targetWindow.removeComponentListener(componentAdapter);

            messageDialog.setVisible(false);
            messageDialog.dispose();

            messageDialog = null;

            cancelHook.set(false);
        }
    }

    @Override
    protected void internalDisplay(Message message) {
        assert SwingUtilities.isEventDispatchThread();

        messageDialog = new JDialog(targetWindow, Dialog.ModalityType.MODELESS);
        messageDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        messageDialog.setUndecorated(true);

        messagePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g.create();

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2d.setColor(color(TOAST_BACKGROUND));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), ARC_SIZE, ARC_SIZE);

                g2d.dispose();
            }
        };
        messagePanel.setOpaque(false);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, 0, MARGIN));

        String iconPath;
        UIColors iconColor;
        switch (message.getMessageType()) {
            case ERROR -> {
                iconPath = "/assets/toast-error.png";
                iconColor = TOAST_ERROR;
            }
            case WARNING -> {
                iconPath = "/assets/toast-warning.png";
                iconColor = TOAST_WARNING;
            }
            default -> {
                iconPath = "/assets/toast-info.png";
                iconColor = TOAST_INFO;
            }
        }

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(loadIcon(iconPath, iconColor, 32));
        contentPanel.add(iconLabel, BorderLayout.WEST);

        JLabel messageLabel = new JLabel();
        messageLabel.setText(message.getMessage());
        messageLabel.setForeground(color(FOREGROUND));
        messageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        messagePanel.add(contentPanel, BorderLayout.CENTER);

        messagePanel.add(createIconButton(
            loadIcon("/assets/x-mark.png", ICON, 12),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 12),
            "gui.close.tooltip",
            e -> cancelHook.set(true)
        ), BorderLayout.EAST);

        messagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                cancelHook.set(true);
            }
        });

        JPanel progressContainer = new JPanel(new BorderLayout());
        progressContainer.setOpaque(false);
        progressContainer.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

        CustomProgressBar messageProgressBar = new CustomProgressBar();
        messageProgressBar.setValue(message.getDurationMillis());
        messageProgressBar.setStringPainted(false);
        messageProgressBar.setForeground(color(iconColor));
        messageProgressBar.setBackground(color(BACKGROUND));
        messageProgressBar.setBorder(BorderFactory.createEmptyBorder());
        messageProgressBar.setPreferredSize(new Dimension(messageDialog.getWidth() - 10, 1));

        progressContainer.add(messageProgressBar, BorderLayout.CENTER);
        messagePanel.add(progressContainer, BorderLayout.SOUTH);

        Timer timer = new Timer(50, new ActionListener() {
            int elapsed = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                elapsed += 50;

                int progress = 100 - (elapsed * 100) / message.getDurationMillis();
                messageProgressBar.setValue(progress);

                if (elapsed >= message.getDurationMillis() || cancelHook.get()) {
                    ((Timer)e.getSource()).stop();
                    displayNextMessage();
                }
            }
        });

        messageDialog.setContentPane(messagePanel);
        messageDialog.pack();

        updateLocation(targetWindow);

        targetWindow.addComponentListener(componentAdapter);

        messageDialog.setShape(new RoundRectangle2D.Double(0, 0,
            messageDialog.getWidth(), messageDialog.getHeight(), ARC_SIZE, ARC_SIZE));

        try {
            messageDialog.setOpacity(0.9f);
        } catch (UnsupportedOperationException e) {
            // Some DE's do not support opacity, ignore.
        }

        messageDialog.setVisible(true);

        timer.start();
    }

    @Override
    protected boolean canDisplay() {
        // windowVisible relies on the window being previously visible then hidden.
        // The isVisible() check is needed when starting headless.
        return windowVisible.get() && targetWindow.isVisible();
    }

    private void updateLocation(JFrame appWindow) {
        Point frameLoc = appWindow.getLocation();
        int x = frameLoc.x + (appWindow.getWidth() - messageDialog.getWidth()) / 2;
        int y = frameLoc.y + appWindow.getHeight() - messageDialog.getHeight() - 20;

        messageDialog.setLocation(x, y);
    }

    private static final Map<JFrame, AbstractMessenger> _instances
        = Collections.synchronizedMap(new WeakHashMap<>());

    public static void show(Message message) {
        show(GDownloader.getInstance().getGuiManager().getAppWindow(), message);
    }

    public static void show(JFrame parent, Message message) {
        AbstractMessenger instance = _instances.get(parent);
        if (instance == null) {// This operation is quick, should be fine with virtual threads.
            AbstractMessenger newInstance = new ToastMessenger(parent);
            instance = _instances.computeIfAbsent(parent, key -> newInstance);
        }

        instance.display(message);
    }
}
