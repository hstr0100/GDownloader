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
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ui.custom.CustomDynamicLabel;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.util.CancelHook;

import static net.brlns.gdownloader.ui.GUIManager.*;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PopupMessenger extends AbstractMessenger {

    private JWindow messageWindow;
    private JPanel messagePanel;

    private PopupMessenger() {
    }

    @Override
    protected void close() {
        assert SwingUtilities.isEventDispatchThread();

        if (messageWindow != null) {
            messageWindow.setVisible(false);
            messageWindow.dispose();

            messageWindow = null;
        }
    }

    @Override
    protected void internalDisplay(Message message) {
        assert SwingUtilities.isEventDispatchThread();

        messageWindow = new JWindow();
        messageWindow.setAlwaysOnTop(true);

        messageWindow.setSize(350, 110);

        Color titleColor;
        Color textColor;
        switch (message.getMessageType()) {
            case ERROR -> {
                titleColor = Color.RED;
                textColor = Color.GRAY;
            }
            case WARNING -> {
                titleColor = Color.YELLOW;
                textColor = Color.GRAY;
            }
            default -> {
                titleColor = color(LIGHT_TEXT);
                textColor = color(FOREGROUND);
            }
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(color(BACKGROUND));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel();
        titleLabel.setText(message.getTitle());
        titleLabel.setForeground(titleColor);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        CancelHook cancelHook = new CancelHook();

        titlePanel.add(createIconButton(
            loadIcon("/assets/x-mark.png", ICON, 12),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 12),
            "gui.close.tooltip",
            e -> cancelHook.set(true)
        ), BorderLayout.EAST);

        panel.add(titlePanel, BorderLayout.NORTH);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                cancelHook.set(true);
            }
        });

        messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

        CustomDynamicLabel messageLabel = new CustomDynamicLabel();
        messageLabel.setFullText(message.getMessage().split(System.lineSeparator()));
        messageLabel.setForeground(textColor);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messagePanel.add(messageLabel, BorderLayout.CENTER);

        panel.add(messagePanel, BorderLayout.CENTER);

        CustomProgressBar messageProgressBar = new CustomProgressBar();
        messageProgressBar.setValue(message.getDurationMillis());
        messageProgressBar.setStringPainted(false);
        messageProgressBar.setForeground(color(FOREGROUND));
        messageProgressBar.setBackground(color(BACKGROUND));
        //messageProgressBar.setBorderPainted(false);
        messageProgressBar.setPreferredSize(new Dimension(messageWindow.getWidth() - 10, 5));

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

        panel.add(messageProgressBar, BorderLayout.SOUTH);

        messageWindow.add(panel);

        updateMessageWindowSize();
        adjustMessageWindowPosition();

        messageWindow.setVisible(true);

        timer.start();
    }

    @Override
    protected boolean canDisplay() {
        return true;
    }

    private void updateMessageWindowSize() {
        assert SwingUtilities.isEventDispatchThread();

        int newHeight = calculateMessageWindowHeight();
        messageWindow.setSize(350, newHeight);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(messageWindow.getGraphicsConfiguration());
        int taskbarHeight = screenInsets.bottom;

        int windowX = screenSize.width - messageWindow.getWidth() - 10;
        int windowY = screenSize.height - messageWindow.getHeight() - taskbarHeight - 10;

        int minHeight = screenSize.height - messageWindow.getHeight() - taskbarHeight - 10;
        if (windowY < minHeight) {
            messageWindow.setSize(messageWindow.getWidth(), screenSize.height - minHeight);
            windowY = minHeight;
        }

        messageWindow.setLocation(windowX, windowY);
    }

    private void adjustMessageWindowPosition() {
        assert SwingUtilities.isEventDispatchThread();

        if (messageWindow != null) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            //Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(messageWindow.getGraphicsConfiguration());
            //int taskbarHeight = screenInsets.bottom;

            int newX = screenSize.width - messageWindow.getWidth() - 10;
            int newY = /*screenSize.height - messageWindow.getHeight() - taskbarHeight -*/ 10;

            messageWindow.setLocation(newX, newY);
        }
    }

    private int calculateMessageWindowHeight() {
        assert SwingUtilities.isEventDispatchThread();

        int totalHeight = 0;
        if (messagePanel != null) {
            for (Component comp : messagePanel.getComponents()) {
                totalHeight += comp.getPreferredSize().height;
            }
        } else {
            totalHeight = 10;
        }

        return Math.min(totalHeight + 110, 220);
    }

    private static final AbstractMessenger instance = new PopupMessenger();

    public static void show(Message message) {
        GDownloader main = GDownloader.getInstance();

        if (main.getConfig().isUseNativeSystemNotifications()
            && main.getSystemTrayManager().isInitialized()) {
            TrayIcon.MessageType nativeType = switch (message.getMessageType()) {
                case ERROR ->
                    TrayIcon.MessageType.ERROR;
                case WARNING ->
                    TrayIcon.MessageType.WARNING;
                default ->
                    TrayIcon.MessageType.INFO;
            };

            main.getSystemTrayManager().getTrayIcon().displayMessage(
                message.getTitle(), message.getMessage(), nativeType);
            return;
        }

        instance.display(message);
    }
}
