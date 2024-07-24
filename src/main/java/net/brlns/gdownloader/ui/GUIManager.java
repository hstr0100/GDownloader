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

import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.YtDlpDownloader;
import net.brlns.gdownloader.ui.custom.CustomButton;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomToolTip;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.Language.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
//TODO add custom tooltip to all buttons
@Slf4j
public final class GUIManager{

    static{
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(5000);
        ToolTipManager.sharedInstance().setEnabled(true);
    }

    private JWindow messageWindow;
    private JPanel messagePanel;

    private JFrame appWindow;
    private JPanel queuePanel;
    private JScrollPane queueScrollPane;

    private final Map<Integer, MediaCard> mediaCards = new ConcurrentHashMap<>();

    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger mediaCardId = new AtomicInteger();

    private boolean isShowingMessage = false;

    private final GDownloader main;

    private final SettingsPanel settingsPanel;

    //TODO
    @Getter
    private final double uiScale;

    public GUIManager(GDownloader mainIn){
        main = mainIn;

        uiScale = Math.clamp(mainIn.getConfig().getUiScale(), 0.5, 3.0);

        settingsPanel = new SettingsPanel(main, this);

        UIManager.put("ToolTip.background", color(TOOLTIP_BACKGROUND));
        UIManager.put("ToolTip.foreground", color(TOOLTIP_FOREGROUND));
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(5, 5, 5, 5));

        UIManager.put("ComboBox.background", new ColorUIResource(color(COMBO_BOX_BACKGROUND)));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(color(COMBO_BOX_SELECTION_BACKGROUND)));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(color(COMBO_BOX_SELECTION_FOREGROUND)));
        UIManager.put("ComboBox.borderPaintsFocus", Boolean.FALSE);
    }

    public void displaySettingsPanel(){
        settingsPanel.createAndShowGUI();
    }

    public void wakeUp(){
        setUpAppWindow();

        SwingUtilities.invokeLater(() -> {
            appWindow.setVisible(true);

            if((appWindow.getExtendedState() & Frame.ICONIFIED) == 1){
                appWindow.setExtendedState(JFrame.ICONIFIED);
                appWindow.setExtendedState(JFrame.NORMAL);
            }
        });
    }

    public void requestFocus(){
        SwingUtilities.invokeLater(() -> {
            appWindow.requestFocus();

            if((appWindow.getExtendedState() & Frame.ICONIFIED) != 1){
                if(!appWindow.isVisible()){
                    appWindow.setVisible(true);
                }

                appWindow.setExtendedState(JFrame.NORMAL);
                appWindow.toFront();
            }
        });
    }

    public String getCurrentAppIconPath(){
        return ThemeProvider.getTheme().getAppIconPath();
    }

    public String getCurrentTrayIconPath(){
        return ThemeProvider.getTheme().getTrayIconPath();
    }

    public Image getAppIcon() throws IOException{
        Image icon = ImageIO.read(getClass().getResource(getCurrentAppIconPath()));

        return icon;
    }

    private void constructToolBar(JPanel mainPanel){
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        topPanel.setBackground(color(BACKGROUND));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        statusPanel.setOpaque(false);

        {
            JLabel statusLabel = new JLabel("");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
            statusLabel.setForeground(color(LIGHT_TEXT));
            statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
            statusLabel.setVerticalAlignment(SwingConstants.CENTER);

            Consumer<YtDlpDownloader> consumer = (YtDlpDownloader downloadManager) -> {
                statusLabel.setText(
                    "<html>"
                    + downloadManager.getDownloadsRunning() + " " + get("gui.statusbar.running")
                    + "<br>"
                    + downloadManager.getCompletedDownloads() + " " + get("gui.statusbar.completed")
                    + "</html>"
                );
            };

            consumer.accept(main.getDownloadManager());

            main.getDownloadManager().registerListener(consumer);

            statusPanel.add(statusLabel);
        }

        {
            JLabel statusLabel = new JLabel("");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
            statusLabel.setForeground(color(LIGHT_TEXT));
            statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
            statusLabel.setVerticalAlignment(SwingConstants.CENTER);

            Consumer<YtDlpDownloader> consumer = (YtDlpDownloader downloadManager) -> {
                statusLabel.setText(
                    "<html>"
                    + downloadManager.getQueueSize() + " " + get("gui.statusbar.queued")
                    + "<br>"
                    + downloadManager.getFailedDownloads() + " " + get("gui.statusbar.failed")
                    + "</html>"
                );
            };

            consumer.accept(main.getDownloadManager());

            main.getDownloadManager().registerListener(consumer);

            statusPanel.add(statusLabel);
        }

        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.gridx = 0;
        gbcLabel.gridy = 0;
        gbcLabel.anchor = GridBagConstraints.WEST;
        gbcLabel.insets = new Insets(0, 10, 0, 20);
        topPanel.add(statusPanel, gbcLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);

        JButton retryButton = createButton(
            loadIcon("/assets/redo.png", ICON),
            loadIcon("/assets/redo.png", ICON_HOVER),
            "gui.retry_failed_downloads.tooltip",
            e -> main.getDownloadManager().retryFailedDownloads()
        );
        retryButton.setVisible(false);
        buttonPanel.add(retryButton);

        main.getDownloadManager().registerListener((YtDlpDownloader downloadManager) -> {
            retryButton.setVisible(downloadManager.getFailedDownloads() != 0);
        });

        buttonPanel.add(createToggleButton(
            loadIcon("/assets/copy-link.png", ICON_ACTIVE),
            loadIcon("/assets/copy-link.png", ICON_HOVER),
            loadIcon("/assets/copy-link.png", ICON),
            loadIcon("/assets/copy-link.png", ICON_HOVER),
            "gui.start_clipboard_monitor.tooltip",
            "gui.stop_clipboard_monitor.tooltip",
            main.getConfig()::isMonitorClipboardForLinks,
            () -> {
                main.getConfig().setMonitorClipboardForLinks(!main.getConfig().isMonitorClipboardForLinks());

                updateQueuePanelMessage();
                main.updateConfig();

                main.resetClipboard();
            }
        ));

        main.getDownloadManager().registerListener((YtDlpDownloader downloadManager) -> {
            updateQueuePanelMessage();
        });

        buttonPanel.add(createToggleButton(
            loadIcon("/assets/mp3.png", ICON_ACTIVE),
            loadIcon("/assets/mp3.png", ICON_HOVER),
            loadIcon("/assets/mp3.png", ICON),
            loadIcon("/assets/mp3.png", ICON_HOVER),
            "gui.download_audio.tooltip",
            "gui.dont_download_audio.tooltip",
            main.getConfig()::isDownloadAudio,
            () -> {
                main.getConfig().setDownloadAudio(!main.getConfig().isDownloadAudio());
                System.out.println(main.getConfig().isDownloadAudio());
                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleButton(
            loadIcon("/assets/mp4.png", ICON_ACTIVE),
            loadIcon("/assets/mp4.png", ICON_HOVER),
            loadIcon("/assets/mp4.png", ICON),
            loadIcon("/assets/mp4.png", ICON_HOVER),
            "gui.download_video.tooltip",
            "gui.dont_download_video.tooltip",
            main.getConfig()::isDownloadVideo,
            () -> {
                main.getConfig().setDownloadVideo(!main.getConfig().isDownloadVideo());
                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleDownloadsButton(
            loadIcon("/assets/pause.png", ICON),
            loadIcon("/assets/pause.png", ICON_HOVER),
            loadIcon("/assets/play.png", ICON),
            loadIcon("/assets/play.png", ICON_HOVER),
            "gui.start_downloads.tooltip",
            "gui.stop_downloads.tooltip",
            main.getDownloadManager()::isRunning,
            () -> {
                if(main.getDownloadManager().isRunning()){
                    main.getDownloadManager().stopDownloads();
                }else{
                    main.getDownloadManager().startDownloads();
                }
            }
        ));

        buttonPanel.add(createButton(
            loadIcon("/assets/erase.png", ICON),
            loadIcon("/assets/erase.png", ICON_HOVER),
            "gui.clear_download_queue.tooltip",
            e -> main.getDownloadManager().clearQueue()
        ));

        buttonPanel.add(createButton(
            loadIcon("/assets/settings.png", ICON),
            loadIcon("/assets/settings.png", ICON_HOVER),
            "settings.sidebar_title",
            e -> displaySettingsPanel()
        ));

        GridBagConstraints gbcButtons = new GridBagConstraints();
        gbcButtons.gridx = 1;
        gbcButtons.gridy = 0;
        gbcButtons.anchor = GridBagConstraints.EAST;
        gbcButtons.weightx = 1.0;
        topPanel.add(buttonPanel, gbcButtons);

        mainPanel.add(topPanel, BorderLayout.SOUTH);
    }

    private JButton createToggleDownloadsButton(
        ImageIcon activeIcon, ImageIcon activeIconHover,
        ImageIcon inactiveIcon, ImageIcon inactiveIconHover,
        String activeTooltip, String inactiveTooltip,
        Supplier<Boolean> watch, Runnable toggler){

        JButton button = createToggleButton(activeIcon, activeIconHover, inactiveIcon, inactiveIconHover, activeTooltip, inactiveTooltip, watch, toggler);

        main.getDownloadManager().registerListener((YtDlpDownloader downloadManager) -> {
            if(downloadManager.isRunning()){
                button.setIcon(activeIcon);
                button.setToolTipText(get(activeTooltip));
            }else{
                button.setIcon(inactiveIcon);
                button.setToolTipText(get(inactiveTooltip));
            }
        });

        return button;
    }

    private void updateQueuePanelMessage(){
        JPanel panel = getEmptyQueuePanel();

        Component firstComponent = panel.getComponent(0);

        if(!main.getDownloadManager().isBlocked()){
            if(!(firstComponent instanceof JLabel)){
                panel.removeAll();

                JLabel label = new JLabel("", SwingConstants.CENTER);
                label.setForeground(color(FOREGROUND));
                panel.add(label, BorderLayout.CENTER);
            }

            JLabel label = (JLabel)panel.getComponent(0);

            if(main.getConfig().isMonitorClipboardForLinks()){
                label.setText(get("gui.empty_queue"));
            }else{
                label.setText(get("gui.empty_queue.enable_clipboard"));
            }
        }else{
            if(!(firstComponent instanceof JPanel)){
                panel.removeAll();
            }

            JPanel innerPanel = new JPanel(new GridBagLayout());
            innerPanel.setOpaque(false);
            innerPanel.setBackground(color(BACKGROUND));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(5, 0, 5, 0);

            JLabel upperLabel = new JLabel("", SwingConstants.CENTER);
            upperLabel.setForeground(color(FOREGROUND));
            upperLabel.setText(get("gui.checking_updates"));

            JLabel bottomLabel = new JLabel("", SwingConstants.CENTER);
            bottomLabel.setForeground(color(FOREGROUND));
            bottomLabel.setText(get("gui.checking_updates.please_wait"));

            innerPanel.add(upperLabel, gbc);
            innerPanel.add(bottomLabel, gbc);

            panel.add(innerPanel, BorderLayout.CENTER);
        }

        panel.revalidate();
        panel.repaint();
    }

    private JButton createToggleButton(
        ImageIcon activeIcon, ImageIcon activeIconHover,
        ImageIcon inactiveIcon, ImageIcon inactiveIconHover,
        String activeTooltip, String inactiveTooltip,
        Supplier<Boolean> watch, Runnable toggler){

        JButton button = new JButton(watch.get() ? activeIcon : inactiveIcon);
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                if(watch.get()){
                    button.setIcon(activeIconHover);
                    button.setToolTipText(get(inactiveTooltip));
                }else{
                    button.setIcon(inactiveIconHover);
                    button.setToolTipText(get(activeTooltip));
                }
            }

            @Override
            public void mouseExited(MouseEvent e){
                if(watch.get()){
                    button.setIcon(activeIcon);
                    button.setToolTipText(get(inactiveTooltip));
                }else{
                    button.setIcon(inactiveIcon);
                    button.setToolTipText(get(activeTooltip));
                }
            }
        });

        button.addActionListener(e -> {
            toggler.run();

            if(watch.get()){
                button.setIcon(activeIcon);
                button.setToolTipText(get(inactiveTooltip));
            }else{
                button.setIcon(inactiveIcon);
                button.setToolTipText(get(activeTooltip));
            }
        });

        CustomToolTip ui = new CustomToolTip();
        ui.setComponent(button);
        ui.setToolTipText(get(watch.get() ? inactiveTooltip : activeTooltip));

        return button;
    }

    public JButton createButton(ImageIcon icon, ImageIcon hoverIcon, String tooltipText, java.awt.event.ActionListener actionListener){
        JButton button = new JButton(icon);
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                button.setIcon(hoverIcon);
                button.setToolTipText(get(tooltipText));
            }

            @Override
            public void mouseExited(MouseEvent e){
                button.setIcon(icon);
                button.setToolTipText(get(tooltipText));
            }
        });

        button.addActionListener(actionListener);

        CustomToolTip ui = new CustomToolTip();
        ui.setComponent(button);
        ui.setToolTipText(get(tooltipText));

        return button;
    }

    private JButton createDialogButton(String text, UIColors backgroundColor, UIColors textColor, UIColors hoverColor){
        CustomButton button = new CustomButton(text,
            color(hoverColor),
            color(hoverColor).brighter());

        button.setFocusPainted(false);
        button.setForeground(color(textColor));
        button.setBackground(color(backgroundColor));
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return button;
    }

    public void showConfirmDialog(String title, String message, int timeoutMs, DialogButton onClose, DialogButton... buttons){
        JDialog dialog = new JDialog(appWindow, title, Dialog.ModalityType.APPLICATION_MODAL){
            private boolean actionPerformed = false;

            @Override
            public void dispose(){
                if(!actionPerformed){
                    actionPerformed = true;

                    onClose.getAction().accept(false);
                }

                super.dispose();
            }
        };

        dialog.setAlwaysOnTop(true);
        dialog.setSize(500, 300);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        try{
            dialog.setIconImage(getAppIcon());
        }catch(IOException e){
            main.handleException(e);
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(color(BACKGROUND));

        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setOpaque(false);
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JLabel dialogLabel = new JLabel(wrapText(50, message), SwingConstants.CENTER);
        dialogLabel.setForeground(color(FOREGROUND));
        dialogPanel.add(dialogLabel, BorderLayout.CENTER);

        panel.add(dialogPanel, BorderLayout.CENTER);

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setOpaque(false);
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JCheckBox rememberCheckBox = new JCheckBox(get("gui.remember_choice"));
        rememberCheckBox.setUI(new CustomCheckBoxUI());
        rememberCheckBox.setBackground(color(BACKGROUND));
        rememberCheckBox.setForeground(color(FOREGROUND));
        rememberCheckBox.setOpaque(false);
        checkboxPanel.add(rememberCheckBox);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(color(SIDE_PANEL_SELECTED));
        buttonPanel.setOpaque(false);

        for(DialogButton dialogButton : buttons){
            JButton button = createDialogButton(
                dialogButton.getTitle(),
                BUTTON_BACKGROUND,
                BUTTON_FOREGROUND,
                BUTTON_HOVER);

            button.addActionListener(e -> {
                dialogButton.getAction().accept(rememberCheckBox.isSelected());
                dialog.dispose();
            });

            buttonPanel.add(button);
        }

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BorderLayout());
        southPanel.setOpaque(false);

        southPanel.add(checkboxPanel, BorderLayout.NORTH);

        CustomProgressBar dialogProgressBar = new CustomProgressBar();
        dialogProgressBar.setValue(100);
        dialogProgressBar.setStringPainted(false);
        dialogProgressBar.setForeground(color(FOREGROUND));
        dialogProgressBar.setBackground(color(BACKGROUND));
        //dialogProgressBar.setBorderPainted(false);
        dialogProgressBar.setPreferredSize(new Dimension(dialog.getWidth() - 10, 7));

        Timer timer = new Timer(50, new ActionListener(){
            int elapsed = 0;

            @Override
            public void actionPerformed(ActionEvent e){
                elapsed += 50;

                int progress = 100 - (elapsed * 100) / timeoutMs;
                dialogProgressBar.setValue(progress);

                if(elapsed >= timeoutMs){
                    ((Timer)e.getSource()).stop();
                    dialog.dispose();
                }
            }
        });

        dialog.addWindowListener(new WindowAdapter(){
            @Override
            public void windowOpened(WindowEvent e){
                timer.start();
            }
        });

        southPanel.add(dialogProgressBar, BorderLayout.CENTER);

        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(southPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    public void showMessage(String title, String message, int durationMillis, MessageType messageType, boolean playTone){
        messageQueue.add(new Message(title, message, durationMillis, messageType, playTone));

        if(!isShowingMessage){
            displayNextMessage();
        }
    }

    private void displayNextMessage(){
        if(messageQueue.isEmpty()){
            isShowingMessage = false;
            return;
        }

        isShowingMessage = true;
        Message nextMessage = messageQueue.poll();

        if(messageWindow != null){
            messageWindow.dispose();
        }

        messageWindow = new JWindow();
        messageWindow.setAlwaysOnTop(true);

        messageWindow.setSize(350, 110);

        Color titleColor;
        Color textColor;
        switch(nextMessage.getMessageType()){
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

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(color(BACKGROUND));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel(wrapText(45, nextMessage.getTitle()));
        titleLabel.setForeground(titleColor);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        AtomicBoolean cancelHook = new AtomicBoolean(false);

        titlePanel.add(createButton(
            loadIcon("/assets/x-mark.png", ICON, 12),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 12),
            "gui.close.tooltip",
            e -> cancelHook.set(true)
        ), BorderLayout.EAST);

        panel.add(titlePanel, BorderLayout.NORTH);

        messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

        JLabel messageLabel = new JLabel(wrapText(50, nextMessage.getMessage()), SwingConstants.CENTER);
        messageLabel.setForeground(textColor);
        messagePanel.add(messageLabel, BorderLayout.CENTER);

        panel.add(messagePanel, BorderLayout.CENTER);

        CustomProgressBar messageProgressBar = new CustomProgressBar();
        messageProgressBar.setValue(nextMessage.getDurationMillis());
        messageProgressBar.setStringPainted(false);
        messageProgressBar.setForeground(color(FOREGROUND));
        messageProgressBar.setBackground(color(BACKGROUND));
        //messageProgressBar.setBorderPainted(false);
        messageProgressBar.setPreferredSize(new Dimension(messageWindow.getWidth() - 10, 5));

        Timer timer = new Timer(50, new ActionListener(){
            int elapsed = 0;

            @Override
            public void actionPerformed(ActionEvent e){
                elapsed += 50;

                int progress = 100 - (elapsed * 100) / nextMessage.getDurationMillis();
                messageProgressBar.setValue(progress);

                if(elapsed >= nextMessage.getDurationMillis()){
                    ((Timer)e.getSource()).stop();

                    SwingUtilities.invokeLater(() -> {
                        messageWindow.setVisible(false);
                        displayNextMessage();
                    });
                }
            }
        });

        panel.add(messageProgressBar, BorderLayout.SOUTH);

        messageWindow.add(panel);

        updateMessageWindowSize();
        messageWindow.setVisible(true);

        timer.start();

        if(nextMessage.isPlayTone() && main.getConfig().isPlaySounds()){
            AudioEngine.playNotificationTone();
        }
    }

    private void updateMessageWindowSize(){
        int newHeight = calculateMessageWindowHeight();
        messageWindow.setSize(350, newHeight);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(messageWindow.getGraphicsConfiguration());
        int taskbarHeight = screenInsets.bottom;

        int windowX = screenSize.width - messageWindow.getWidth() - 10;
        int windowY = screenSize.height - messageWindow.getHeight() - taskbarHeight - 10;

        int minHeight = screenSize.height - messageWindow.getHeight() - taskbarHeight - 10;
        if(windowY < minHeight){
            messageWindow.setSize(messageWindow.getWidth(), screenSize.height - minHeight);
            windowY = minHeight;
        }

        messageWindow.setLocation(windowX, windowY);

        adjustMessageWindowPosition(true);
    }

    private int calculateMessageWindowHeight(){
        int totalHeight = 0;
        if(messagePanel != null){
            for(Component comp : messagePanel.getComponents()){
                totalHeight += comp.getPreferredSize().height;
            }
        }else{
            totalHeight = 10;
        }

        return Math.min(totalHeight + 110, 220);
    }

    public void refreshWindow(){
        if(appWindow == null){
            throw new RuntimeException("Called before initialization");
        }

        appWindow.setAlwaysOnTop(main.getConfig().isKeepWindowAlwaysOnTop());
        appWindow.setDefaultCloseOperation(main.getConfig().isExitOnClose() ? JFrame.EXIT_ON_CLOSE : JFrame.HIDE_ON_CLOSE);
    }

    private void setUpAppWindow(){
        if(appWindow == null){
            // note to self, tooltips only show up when focused
            appWindow = new JFrame(GDownloader.REGISTRY_APP_NAME);
            refreshWindow();

            //appWindow.setResizable(false);
            //appWindow.setUndecorated(true);
            try{
                appWindow.setIconImage(getAppIcon());
            }catch(IOException e){
                main.handleException(e);
            }

            appWindow.addWindowStateListener((WindowEvent e) -> {
                if((e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH){
                    appWindow.setAlwaysOnTop(false);
                }else{
                    appWindow.setAlwaysOnTop(main.getConfig().isKeepWindowAlwaysOnTop());
                }
            });

            appWindow.addWindowListener(new WindowAdapter(){
                @Override
                public void windowClosing(WindowEvent e){
                    adjustMessageWindowPosition();
                }

                @Override
                public void windowIconified(WindowEvent e){
                    adjustMessageWindowPosition();
                }

                @Override
                public void windowDeiconified(WindowEvent e){
                    adjustMessageWindowPosition();
                }
            });

            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventDispatcher((KeyEvent e) -> {
                if(e.getID() == KeyEvent.KEY_PRESSED){
                    if((e.getKeyCode() == KeyEvent.VK_V) && ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)){
                        main.resetClipboard();

                        main.updateClipboard(null, true);
                    }
                }

                return false;
            });

            adjustWindowSize();

            adjustMessageWindowPosition();

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            mainPanel.setBackground(color(BACKGROUND));

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(color(BACKGROUND));

            constructToolBar(headerPanel);

            mainPanel.add(headerPanel, BorderLayout.NORTH);

            queuePanel = new JPanel();
            queuePanel.setLayout(new BoxLayout(queuePanel, BoxLayout.Y_AXIS));
            queuePanel.setBackground(color(BACKGROUND));

            new DropTarget(queuePanel, new DropTargetListener(){
                @Override
                public void dragEnter(DropTargetDragEvent dtde){
                    //Not implemented
                }

                @Override
                public void dragOver(DropTargetDragEvent dtde){
                    //Not implemented
                }

                @Override
                public void dropActionChanged(DropTargetDragEvent dtde){
                    //Not implemented
                }

                @Override
                public void dragExit(DropTargetEvent dte){
                    //Not implemented
                }

                @Override
                public void drop(DropTargetDropEvent dtde){
                    try{
                        dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

                        Transferable transferable = dtde.getTransferable();

                        boolean result = main.updateClipboard(transferable, true);

                        dtde.dropComplete(result);
                    }catch(Exception e){
                        main.handleException(e);

                        dtde.dropComplete(false);
                    }
                }
            });

            queuePanel.add(getEmptyQueuePanel(), BorderLayout.CENTER);

            queueScrollPane = new JScrollPane(queuePanel);
            queueScrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
            queueScrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());

            queueScrollPane.setBorder(BorderFactory.createEmptyBorder());
            queueScrollPane.setBackground(color(BACKGROUND));
            //queueScrollPane.getViewport().setBackground(color(BACKGROUND));
            queueScrollPane.getVerticalScrollBar().setUnitIncrement(4);
            queueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            queueScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

            mainPanel.add(queueScrollPane, BorderLayout.CENTER);

            appWindow.add(mainPanel);
        }
    }

    private JPanel emptyQueuePanel;

    private JPanel getEmptyQueuePanel(){
        if(emptyQueuePanel != null){
            return emptyQueuePanel;
        }

        emptyQueuePanel = new JPanel(new BorderLayout());
        emptyQueuePanel.setOpaque(false);
        emptyQueuePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JLabel messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setForeground(color(FOREGROUND));
        emptyQueuePanel.add(messageLabel, BorderLayout.CENTER);

        updateQueuePanelMessage();

        return emptyQueuePanel;
    }

    public MediaCard addMediaCard(boolean video, String... mediaLabel){
        setUpAppWindow();

        if(!appWindow.isVisible()){
            appWindow.setVisible(true);
        }

        int id = mediaCardId.incrementAndGet();

        JPanel card = new JPanel();
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 5));
        card.setBackground(color(MEDIA_CARD));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, main.getConfig().getFontSize() >= 15 ? 150 : 130));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        JPanel thumbnailPanel = new JPanel();
        thumbnailPanel.setPreferredSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setMinimumSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
        thumbnailPanel.setLayout(new BorderLayout());

        if(video){
            ImageIcon noImageIcon = loadIcon("/assets/video.png", ICON, 64);
            JLabel imageLabel = new JLabel(noImageIcon);
            thumbnailPanel.add(imageLabel, BorderLayout.CENTER);
        }else{
            ImageIcon noImageIcon = loadIcon("/assets/winamp.png", ICON, 64);
            JLabel imageLabel = new JLabel(noImageIcon);
            thumbnailPanel.add(imageLabel, BorderLayout.CENTER);
        }

//        JLabel noImageLabel = new JLabel("No Image", SwingConstants.CENTER);
//        noImageLabel.setForeground(color(FOREGROUND));
//        thumbnailPanel.add(noImageLabel, BorderLayout.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(thumbnailPanel, gbc);

        JLabel mediaNameLabel = new JLabel(wrapText(50, mediaLabel));
        mediaNameLabel.setForeground(color(FOREGROUND));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        card.add(mediaNameLabel, gbc);

        CustomProgressBar progressBar = new CustomProgressBar(Color.WHITE);
        progressBar.setValue(100);
        progressBar.setStringPainted(true);
        progressBar.setString(get("enums.download_status.queued"));
        progressBar.setForeground(Color.GRAY);
        progressBar.setBackground(Color.GRAY);
        //progressBar.setBorderPainted(false);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        card.add(progressBar, gbc);

        JButton closeButton = createButton(
            loadIcon("/assets/x-mark.png", ICON, 16),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 16),
            "gui.remove_from_queue.tooltip",
            e -> removeMediaCard(id)
        );
        closeButton.setPreferredSize(new Dimension(16, 16));

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        card.add(closeButton, gbc);

        if(mediaCards.isEmpty()){
            queuePanel.remove(getEmptyQueuePanel());
        }

        queuePanel.add(card);

        MediaCard mediaCard = new MediaCard(id, card, mediaNameLabel, thumbnailPanel, progressBar);

        MouseAdapter listener = new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e){
                if(mediaCard.getOnClick() != null){
                    mediaCard.getOnClick().apply();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e){
                card.setBackground(color(MEDIA_CARD_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e){
                card.setBackground(color(MEDIA_CARD));
            }
        };

        card.addMouseListener(listener);
        mediaNameLabel.addMouseListener(listener);

        mediaCards.put(id, mediaCard);

        appWindow.revalidate();
        appWindow.repaint();

        scrollToBottom(queueScrollPane);

        return mediaCard;
    }

    //https://stackoverflow.com/questions/5147768/scroll-jscrollpane-to-bottom
    private void scrollToBottom(JScrollPane scrollPane){
        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        verticalBar.addAdjustmentListener(new AdjustmentListener(){
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e){
                Adjustable adjustable = e.getAdjustable();
                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        });
    }

    private void adjustWindowSize(){
        SwingUtilities.invokeLater(() -> {
            appWindow.setSize(610, 370);
            appWindow.setMinimumSize(new Dimension(appWindow.getWidth(), 220));

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(appWindow.getGraphicsConfiguration());
            int taskbarHeight = screenInsets.bottom;

            int windowX = screenSize.width - appWindow.getWidth() - 10;
            int windowY = screenSize.height - appWindow.getHeight() - taskbarHeight - 10;

            int minHeight = screenSize.height - appWindow.getHeight() - taskbarHeight - 10;
            if(windowY < minHeight){
                appWindow.setSize(appWindow.getWidth(), screenSize.height - minHeight);
                windowY = minHeight;
            }

            appWindow.setLocation(windowX, windowY);
        });
    }

    private void adjustMessageWindowPosition(){
        adjustMessageWindowPosition(false);
    }

    private void adjustMessageWindowPosition(boolean immediate){
        Runnable task = () -> {
            if(messageWindow != null){
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(messageWindow.getGraphicsConfiguration());
                int taskbarHeight = screenInsets.bottom;

                int newX = screenSize.width - messageWindow.getWidth() - 10;
                int newY = screenSize.height - messageWindow.getHeight() - taskbarHeight - 10;

                messageWindow.setLocation(newX, newY);

                //TODO check if the window is actually on top
                if(appWindow != null && appWindow.isVisible() && (appWindow.getExtendedState() & Frame.ICONIFIED) != 1){
                    if(messageWindow.getBounds().intersects(appWindow.getBounds())){
                        newY = appWindow.getY() - messageWindow.getHeight() - 10;

                        messageWindow.setLocation(newX, newY);
                    }
                }
            }
        };

        if(immediate){
            task.run();
        }else{
            SwingUtilities.invokeLater(task);
        }
    }

    public void removeMediaCard(int id){
        MediaCard progressBar = mediaCards.remove(id);

        if(progressBar != null){
            progressBar.close();

            queuePanel.remove(progressBar.getPanel());

            if(mediaCards.isEmpty()){
                queuePanel.add(getEmptyQueuePanel(), BorderLayout.CENTER);
            }

            appWindow.revalidate();
            appWindow.repaint();
        }
    }

    public void closeAppWindow(){
        if(appWindow != null){
            appWindow.setVisible(false);

//          mediaCards.clear();
//          queuePanel.removeAll();
            adjustMessageWindowPosition();
        }
    }

    @Data
    private static class ImageCacheKey{

        private final String path;
        private final Color color;
        private final int scale;
    }

    private final Map<ImageCacheKey, ImageIcon> _imageCache = new HashMap<>();

    public ImageIcon loadIcon(String path, UIColors color){
        return loadIcon(path, color, 36);
    }

    public ImageIcon loadIcon(String path, UIColors color, int scale){
        Color themeColor = color(color);
        ImageCacheKey key = new ImageCacheKey(path, themeColor, scale);

        if(_imageCache.containsKey(key)){
            return _imageCache.get(key);
        }

        try(InputStream resourceStream = GUIManager.class.getResourceAsStream(path)){
            BufferedImage originalImage = ImageIO.read(resourceStream);
            if(originalImage == null){
                throw new IOException("Failed to load image: " + path);
            }

            WritableRaster raster = originalImage.getRaster();

            for(int y = 0; y < originalImage.getHeight(); y++){
                for(int x = 0; x < originalImage.getWidth(); x++){
                    int[] pixel = raster.getPixel(x, y, (int[])null);

                    int alpha = pixel[3];

                    if(alpha != 0){
                        pixel[0] = themeColor.getRed();
                        pixel[1] = themeColor.getGreen();
                        pixel[2] = themeColor.getBlue();
                        pixel[3] = themeColor.getAlpha();

                        raster.setPixel(x, y, pixel);
                    }
                }
            }

            Image image = originalImage.getScaledInstance(scale, scale, Image.SCALE_SMOOTH);

            ImageIcon icon = new ImageIcon(image);

            _imageCache.put(key, icon);

            return icon;
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    protected static String wrapText(int maxLineLength, String... lines){
        if(maxLineLength < 20){
            throw new IllegalArgumentException("Line length too short" + maxLineLength);
        }

        StringBuilder wrappedText = new StringBuilder();

        for(String text : lines){
            text = text.replace("\n", "<br>");

            for(String line : text.split("<br>")){
                //String cutWord = line.substring(0, maxLineLength - 3) + "...";
                if(line.length() > maxLineLength){
                    int count = 0;

                    for(int i = 0; i < line.length(); i++){
                        char c = line.charAt(i);
                        wrappedText.append(c);
                        count++;
                        if(count == maxLineLength){
                            wrappedText.append("<br>");
                            count = 0;
                        }
                    }
                }else{
                    String[] words = line.split(" ");
                    int lineLength = 0;

                    for(String word : words){
                        if(lineLength + word.length() > maxLineLength){
                            wrappedText.append("<br>").append(word).append(" ");
                            lineLength = word.length() + 1; // reset lineLength
                        }else{
                            wrappedText.append(word).append(" ");
                            lineLength += word.length() + 1;
                        }
                    }
                }

                if(!wrappedText.toString().trim().endsWith("<br>")){
                    wrappedText.append("<br>");
                }
            }
        }

        String result = wrappedText.toString().trim();
        if(result.endsWith("<br>")){
            result = result.substring(0, result.length() - 4);
        }

        return "<html>" + result + "</html>";
    }

    public static enum MessageType{
        ERROR,
        WARNING,
        INFO
    }

    @Data
    private static class Message{

        private final String title;
        private final String message;
        private final int durationMillis;
        private final MessageType messageType;
        private final boolean playTone;

    }

    @FunctionalInterface
    public interface CallFunction{

        void apply();
    }

    @FunctionalInterface
    public interface ButtonFunction{

        void accept(boolean selected);
    }

    @Data
    public static class DialogButton{

        private final String title;
        private final ButtonFunction action;
    }

}
