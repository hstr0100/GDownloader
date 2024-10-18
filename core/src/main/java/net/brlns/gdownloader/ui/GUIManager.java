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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import java.util.function.Function;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.YtDlpDownloader;
import net.brlns.gdownloader.ui.custom.*;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.updater.AbstractGitUpdater;

import static javax.swing.TransferHandler.MOVE;
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

    private static final DataFlavor MEDIA_CARD_FLAVOR = new DataFlavor(MediaCard.class, "MediaCard");

    private JWindow messageWindow;
    private JPanel messagePanel;

    private JFrame appWindow;
    private JPanel queuePanel;
    private JScrollPane queueScrollPane;

    private JPanel updaterPanel;

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
        updaterPanel = createUpdaterPanel();

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

        addStatusLabel(statusPanel, LIGHT_TEXT, (downloadManager) -> {
            return "<html>"
                + downloadManager.getDownloadsRunning() + " " + l10n("gui.statusbar.running")
                + "<br>"
                + downloadManager.getCompletedDownloads() + " " + l10n("gui.statusbar.completed")
                + "</html>";
        });

        addStatusLabel(statusPanel, LIGHT_TEXT, (downloadManager) -> {
            return "<html>"
                + downloadManager.getQueueSize() + " " + l10n("gui.statusbar.queued")
                + "<br>"
                + downloadManager.getFailedDownloads() + " " + l10n("gui.statusbar.failed")
                + "</html>";
        });

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
            (state) -> loadIcon("/assets/copy-link.png", state ? ICON_ACTIVE : ICON),
            (state) -> loadIcon("/assets/copy-link.png", ICON_HOVER),
            (state) -> state ? "gui.stop_clipboard_monitor.tooltip"
                : "gui.start_clipboard_monitor.tooltip",
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
            (state) -> loadIcon("/assets/mp3.png", state ? ICON_ACTIVE : ICON),
            (state) -> loadIcon("/assets/mp3.png", ICON_HOVER),
            (state) -> state ? "gui.dont_download_audio.tooltip"
                : "gui.download_audio.tooltip",
            main.getConfig()::isDownloadAudio,
            () -> {
                main.getConfig().setDownloadAudio(!main.getConfig().isDownloadAudio());
                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleButton(
            (state) -> loadIcon("/assets/mp4.png", state ? ICON_ACTIVE : ICON),
            (state) -> loadIcon("/assets/mp4.png", ICON_HOVER),
            (state) -> state ? "gui.dont_download_video.tooltip"
                : "gui.download_video.tooltip",
            main.getConfig()::isDownloadVideo,
            () -> {
                main.getConfig().setDownloadVideo(!main.getConfig().isDownloadVideo());
                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleDownloadsButton(
            (state) -> {
                if(state){
                    return loadIcon("/assets/pause.png", ICON);
                }else{
                    if(main.getDownloadManager().getQueueSize() > 0){
                        return loadIcon("/assets/play.png", QUEUE_ACTIVE_ICON);
                    }else{
                        return loadIcon("/assets/play.png", ICON);
                    }
                }
            },
            (state) -> {
                if(state){
                    return loadIcon("/assets/pause.png", ICON_HOVER);
                }else{
                    return loadIcon("/assets/play.png", ICON_HOVER);
                }
            },
            (state) -> state ? "gui.stop_downloads.tooltip"
                : "gui.start_downloads.tooltip",
            main.getDownloadManager()::isRunning,
            () -> {
                main.getDownloadManager().toggleDownloads();
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
        Function<Boolean, ImageIcon> icon,
        Function<Boolean, ImageIcon> hoverIcon,
        Function<Boolean, String> tooltip,
        Supplier<Boolean> watch, Runnable toggler){

        JButton button = createToggleButton(icon, hoverIcon, tooltip, watch, toggler);

        main.getDownloadManager().registerListener((YtDlpDownloader downloadManager) -> {
            boolean state = downloadManager.isRunning();

            button.setIcon(icon.apply(state));
            button.setToolTipText(l10n(tooltip.apply(state)));
        });

        return button;
    }

    public void addStatusLabel(JPanel statusPanel, UIColors textColor, StatusLabelUpdater updater){
        JLabel statusLabel = new JLabel("");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        statusLabel.setForeground(color(textColor));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setVerticalAlignment(SwingConstants.CENTER);

        Consumer<YtDlpDownloader> consumer = (downloadManager) -> {
            statusLabel.setText(updater.updateText(downloadManager));
        };

        consumer.accept(main.getDownloadManager());
        main.getDownloadManager().registerListener(consumer);
        statusPanel.add(statusLabel);
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
            label.setText(main.getConfig().isMonitorClipboardForLinks()
                ? l10n("gui.empty_queue")
                : l10n("gui.empty_queue.enable_clipboard"));
        }else{
            if(!(firstComponent instanceof JPanel)){
                panel.removeAll();
            }

            panel.add(updaterPanel, BorderLayout.CENTER);
        }

        panel.revalidate();
        panel.repaint();
    }

    private JPanel createUpdaterPanel(){
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
        upperLabel.setText(l10n("gui.checking_updates"));
        innerPanel.add(upperLabel, gbc);

        JLabel bottomLabel = new JLabel("", SwingConstants.CENTER);
        bottomLabel.setForeground(color(FOREGROUND));
        bottomLabel.setText(l10n("gui.checking_updates.please_wait"));
        innerPanel.add(bottomLabel, gbc);

        JPanel spacerPanel = new JPanel();
        spacerPanel.setOpaque(false);
        spacerPanel.setPreferredSize(new Dimension(1, 20));
        innerPanel.add(spacerPanel, gbc);

        for(AbstractGitUpdater updater : main.getUpdaters()){
            if(!updater.isSupported()){
                continue;
            }

            JPanel updaterRowPanel = new JPanel(new GridBagLayout());
            updaterRowPanel.setOpaque(false);
            updaterRowPanel.setBackground(color(BACKGROUND));

            GridBagConstraints labelGbc = new GridBagConstraints();
            labelGbc.gridx = 0;
            labelGbc.gridy = 0;
            labelGbc.anchor = GridBagConstraints.WEST;
            labelGbc.fill = GridBagConstraints.NONE;
            labelGbc.insets = new Insets(0, 0, 0, 10);

            JLabel updaterLabel = new JLabel("", SwingConstants.LEFT);
            updaterLabel.setForeground(color(FOREGROUND));
            updaterLabel.setText(updater.getName());
            updaterLabel.setPreferredSize(new Dimension(100, 15));
            updaterLabel.setMaximumSize(new Dimension(100, 15));

            GridBagConstraints progressBarGbc = new GridBagConstraints();
            progressBarGbc.gridx = 1;
            progressBarGbc.gridy = 0;
            progressBarGbc.anchor = GridBagConstraints.EAST;
            progressBarGbc.fill = GridBagConstraints.NONE;

            CustomProgressBar progressBar = new CustomProgressBar(Color.WHITE);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString(l10n("enums.update_status.checking"));
            progressBar.setForeground(Color.MAGENTA);
            progressBar.setBackground(Color.GRAY);
            progressBar.setPreferredSize(new Dimension(200, 15));
            progressBar.setMaximumSize(new Dimension(200, 15));

            updaterRowPanel.add(updaterLabel, labelGbc);
            updaterRowPanel.add(progressBar, progressBarGbc);

            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            innerPanel.add(updaterRowPanel, gbc);

            updater.registerListener((status, progress) -> {
                switch(status){
                    case CHECKING -> {
                        progressBar.setValue((int)progress);
                        progressBar.setString(status.getDisplayName() + ": " + String.format("%.1f", progress) + "%");
                        progressBar.setForeground(Color.MAGENTA);
                    }
                    case DOWNLOADING -> {
                        progressBar.setValue((int)progress);
                        progressBar.setString(status.getDisplayName() + ": " + String.format("%.1f", progress) + "%");
                        progressBar.setForeground(new Color(255, 214, 0));
                    }
                    case UNPACKING -> {
                        progressBar.setValue((int)progress);
                        progressBar.setString(status.getDisplayName() + ": " + String.format("%.1f", progress) + "%");
                        progressBar.setForeground(Color.ORANGE);
                    }
                    case DONE -> {
                        progressBar.setValue(100);
                        progressBar.setString(status.getDisplayName());
                        progressBar.setForeground(new Color(0, 200, 83));
                    }
                    case FAILED -> {
                        progressBar.setValue(100);
                        progressBar.setString(status.getDisplayName());
                        progressBar.setForeground(Color.RED);
                    }
                    default ->
                        throw new RuntimeException("Unhandled status: " + status);
                }
            });
        }

        return innerPanel;
    }

    private JButton createToggleButton(
        Function<Boolean, ImageIcon> icon,
        Function<Boolean, ImageIcon> hoverIcon,
        Function<Boolean, String> tooltip,
        Supplier<Boolean> watch, Runnable toggler){

        JButton button = new JButton(icon.apply(watch.get()));
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                boolean state = watch.get();

                button.setIcon(hoverIcon.apply(state));
                button.setToolTipText(l10n(tooltip.apply(state)));
            }

            @Override
            public void mouseExited(MouseEvent e){
                boolean state = watch.get();

                button.setIcon(icon.apply(state));
                button.setToolTipText(l10n(tooltip.apply(state)));
            }
        });

        button.addActionListener(e -> {
            toggler.run();

            boolean state = watch.get();

            button.setIcon(icon.apply(state));
            button.setToolTipText(l10n(tooltip.apply(state)));
        });

        CustomToolTip ui = new CustomToolTip();
        ui.setComponent(button);
        ui.setToolTipText(l10n(tooltip.apply(watch.get())));

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
                button.setToolTipText(l10n(tooltipText));
            }

            @Override
            public void mouseExited(MouseEvent e){
                button.setIcon(icon);
                button.setToolTipText(l10n(tooltipText));
            }
        });

        button.addActionListener(actionListener);

        CustomToolTip ui = new CustomToolTip();
        ui.setComponent(button);
        ui.setToolTipText(l10n(tooltipText));

        return button;
    }

    private JButton createDialogButton(String text, UIColors backgroundColor,
        UIColors textColor, UIColors hoverColor){
        CustomButton button = new CustomButton(text,
            color(hoverColor),
            color(hoverColor).brighter());

        button.setFocusPainted(false);
        button.setForeground(color(textColor));
        button.setBackground(color(backgroundColor));
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return button;
    }

    public void showConfirmDialog(String title, String message, int timeoutMs,
        DialogButton onClose, DialogButton... buttons){
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

        dialog.setAlwaysOnTop(true);//TODO: We might wanna consider just bringing this to top but not pinning it there. Java doesn't directly support this but there are workarounds.
        dialog.setSize(500, 300);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        try{
            dialog.setIconImage(getAppIcon());
        }catch(IOException e){
            main.handleException(e);
        }

        JPanel panel = new JPanel(new BorderLayout());
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

        JCheckBox rememberCheckBox = new JCheckBox(l10n("gui.remember_choice"));
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

        JPanel southPanel = new JPanel(new BorderLayout());
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
        log.info("Popup {}: {} - {}", messageType, title, message);

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

        JPanel panel = new JPanel(new BorderLayout());
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

                if(elapsed >= nextMessage.getDurationMillis() || cancelHook.get()){
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
            String version = System.getProperty("jpackage.app-version");

            appWindow = new JFrame(GDownloader.REGISTRY_APP_NAME + (version != null ? " v" + version : ""));
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

                adjustMediaCards();
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

            new DropTarget(appWindow, new PanelDropTargetListener());

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

            appWindow.addComponentListener(new ComponentAdapter(){
                @Override
                public void componentResized(ComponentEvent e){
                    adjustMediaCards();
                }
            });
        }
    }

    private void adjustMediaCards(){
        double factor = 1;
        if(appWindow.getExtendedState() == JFrame.MAXIMIZED_BOTH){
            factor = 1.20;
        }

        for(MediaCard card : mediaCards.values()){
            card.scaleThumbnail(factor);
        }

        queuePanel.revalidate();
        queuePanel.repaint();
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

        int fontSize = main.getConfig().getFontSize();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, fontSize >= 15 ? 150 + (fontSize - 15) * 3 : 135));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        //Dragidy-draggy-nub-thingy
        JPanel dragPanel = new JPanel(new BorderLayout());
        dragPanel.setPreferredSize(new Dimension(24, 24));
        dragPanel.setMinimumSize(new Dimension(24, 24));
        dragPanel.setMaximumSize(new Dimension(24, 24));
        dragPanel.setBackground(new Color(0, 0, 0, 0));

        ImageIcon dragIcon = loadIcon("/assets/drag.png", ICON, 24);
        JLabel dragLabel = new JLabel(dragIcon);
        dragLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dragPanel.add(dragLabel, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(dragPanel, gbc);

        //Thumbnail
        CustomThumbnailPanel thumbnailPanel = new CustomThumbnailPanel();
        thumbnailPanel.setPreferredSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setMinimumSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
        thumbnailPanel.setLayout(new BorderLayout());

        ImageIcon noImageIcon = loadIcon(video ? "/assets/video.png" : "/assets/winamp.png", ICON, 78);
        JLabel imageLabel = new JLabel(noImageIcon);
        thumbnailPanel.add(imageLabel, BorderLayout.CENTER);

        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(thumbnailPanel, gbc);

        JLabel mediaNameLabel = new JLabel(wrapText(50, mediaLabel));
        mediaNameLabel.setForeground(color(FOREGROUND));
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        card.add(mediaNameLabel, gbc);

        CustomProgressBar progressBar = new CustomProgressBar(Color.WHITE);
        progressBar.setValue(100);
        progressBar.setStringPainted(true);
        progressBar.setString(l10n("enums.download_status.queued"));
        progressBar.setForeground(Color.GRAY);
        progressBar.setBackground(Color.GRAY);
        //progressBar.setBorderPainted(false);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));

        gbc.gridx = 2;
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

        gbc.gridx = 3;
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

        card.setTransferHandler(new PanelTransferHandler());

        MouseAdapter listener = new MouseAdapter(){
            private long lastClick = System.currentTimeMillis();

            @Override
            public void mousePressed(MouseEvent e){
                Component component = e.getComponent();

                if(component.equals(dragLabel)){
                    TransferHandler handler = card.getTransferHandler();

                    if(handler != null){//peace of mind
                        handler.exportAsDrag(card, e, MOVE);
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e){
                if(SwingUtilities.isLeftMouseButton(e)){
                    if(mediaCard.getOnLeftClick() != null && (System.currentTimeMillis() - lastClick) > 50){
                        mediaCard.getOnLeftClick().run();

                        lastClick = System.currentTimeMillis();
                    }
                }else if(SwingUtilities.isRightMouseButton(e)){
                    showPopupPanel(card, mediaCard.getRightClickMenu(), e.getX(), e.getY());
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
        dragLabel.addMouseListener(listener);
        mediaNameLabel.addMouseListener(listener);

        card.putClientProperty("MEDIA_CARD", mediaCard);

        mediaCards.put(id, mediaCard);

        appWindow.revalidate();
        appWindow.repaint();

        scrollToBottom(queueScrollPane);

        return mediaCard;
    }

    private void showPopupPanel(JPanel parentPanel, Map<String, Runnable> actions, int x, int y){
        if(actions.isEmpty()){
            return;
        }

        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new GridLayout(actions.size(), 1));
        popupPanel.setBackground(Color.DARK_GRAY);
        popupPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popupPanel.setOpaque(true);

        JWindow popupWindow = new JWindow();
        popupWindow.setLayout(new BorderLayout());

        for(Map.Entry<String, Runnable> entry : actions.entrySet()){
            JButton button = new CustomMenuButton(entry.getKey());

            button.addActionListener(e -> {
                entry.getValue().run();
                popupWindow.dispose();
            });

            popupPanel.add(button);
        }

        popupWindow.add(popupPanel, BorderLayout.CENTER);
        popupWindow.pack();

        Point locationOnScreen = parentPanel.getLocationOnScreen();
        popupWindow.setLocation(locationOnScreen.x + x, locationOnScreen.y + y);

        popupWindow.setVisible(true);

        AWTEventListener globalMouseListener = new AWTEventListener(){
            @Override
            public void eventDispatched(AWTEvent event){
                if(event.getID() == MouseEvent.MOUSE_CLICKED){
                    MouseEvent me = (MouseEvent)event;
                    Component component = SwingUtilities.getDeepestComponentAt(me.getComponent(), me.getX(), me.getY());

                    if(component == null || !SwingUtilities.isDescendingFrom(component, popupWindow)){
                        popupWindow.dispose();
                        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                    }
                }
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener, AWTEvent.MOUSE_EVENT_MASK);

        popupWindow.addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosed(WindowEvent e){
                Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener);
            }
        });
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
            appWindow.setSize(658, 370);
            appWindow.setMinimumSize(new Dimension(appWindow.getWidth(), 225));

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
                //Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(messageWindow.getGraphicsConfiguration());
                //int taskbarHeight = screenInsets.bottom;

                int newX = screenSize.width - messageWindow.getWidth() - 10;
                int newY = /*screenSize.height - messageWindow.getHeight() - taskbarHeight -*/ 10;

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

                        if(++count == maxLineLength){
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
    public interface ButtonFunction{

        void accept(boolean selected);
    }

    @FunctionalInterface
    public interface StatusLabelUpdater{

        String updateText(YtDlpDownloader downloadManager);
    }

    @Data
    public static class DialogButton{

        private final String title;
        private final ButtonFunction action;
    }

    private boolean tryHandleDrop(Transferable transferable){
        return main.updateClipboard(transferable, true);
    }

    private class PanelDropTargetListener implements DropTargetListener{

        private final Timer scrollTimer = new Timer(50, (ActionEvent e) -> {
            Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(mouseLocation, queueScrollPane.getViewport());

            Rectangle rect = queueScrollPane.getViewport().getViewRect();
            int tolerance = 100;

            if(mouseLocation.y < tolerance && rect.y > 0){
                queueScrollPane.getVerticalScrollBar().setValue(
                    queueScrollPane.getVerticalScrollBar().getValue() - 30);
            }else if(mouseLocation.y > rect.height - tolerance
                && rect.y + rect.height < queueScrollPane.getViewport().getView().getHeight()){
                queueScrollPane.getVerticalScrollBar().setValue(
                    queueScrollPane.getVerticalScrollBar().getValue() + 30);
            }
        });

        @Override
        public void dragEnter(DropTargetDragEvent dtde){
            scrollTimer.start();
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
            scrollTimer.stop();
        }

        @Override
        public void drop(DropTargetDropEvent dtde){
            scrollTimer.stop();

            try{
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

                Transferable transferable = dtde.getTransferable();

                boolean result = tryHandleDrop(transferable);

                dtde.dropComplete(result);
            }catch(Exception e){
                main.handleException(e);

                dtde.dropComplete(false);
            }
        }
    }

    private class PanelTransferHandler extends TransferHandler{

        @Override
        public int getSourceActions(JComponent c){
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c){
            JPanel panel = (JPanel)c;
            MediaCard card = (MediaCard)panel.getClientProperty("MEDIA_CARD");

            if(card != null){
                return new MediaCardTransferable(card);
            }

            return null;
        }

        @Override
        public boolean canImport(TransferSupport support){
            return support.isDataFlavorSupported(MEDIA_CARD_FLAVOR) || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support){
            if(!canImport(support)){
                return false;
            }

            try{
                Transferable transferable = support.getTransferable();

                if(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)){
                    return tryHandleDrop(transferable);
                }

                if(transferable.isDataFlavorSupported(MEDIA_CARD_FLAVOR)){
                    MediaCard card = (MediaCard)transferable.getTransferData(MEDIA_CARD_FLAVOR);

                    JPanel sourcePanel = card.getPanel();

                    if(sourcePanel != null){
                        Component dropTarget = support.getComponent();

                        Rectangle windowBounds = appWindow.getBounds();
                        Point dropLocation = dropTarget.getLocationOnScreen();

                        if(windowBounds.contains(dropLocation) && dropTarget instanceof JPanel jPanel){
                            int targetIndex = getComponentIndex(jPanel);

                            log.debug("Drop target index is {}", targetIndex);

                            if(card.getOnDrag() != null){
                                int validIndex = getValidComponentIndex(jPanel);

                                log.debug("Valid target index is {}", validIndex);

                                card.getOnDrag().accept(validIndex);
                            }

                            queuePanel.remove(sourcePanel);
                            queuePanel.add(sourcePanel, targetIndex);
                            queuePanel.revalidate();
                            queuePanel.repaint();

                            return true;
                        }
                    }
                }
            }catch(Exception e){
                main.handleException(e, false);
            }

            return false;
        }

        private int getComponentIndex(JPanel component){
            Component[] components = queuePanel.getComponents();
            for(int i = 0; i < components.length; i++){
                if(components[i] == component){
                    return i;
                }
            }

            return -1;
        }

        private int getValidComponentIndex(JPanel componentIn){
            int index = 0;
            for(Component component : queuePanel.getComponents()){
                MediaCard card = (MediaCard)((JPanel)component).getClientProperty("MEDIA_CARD");
                if(card == null){
                    throw new IllegalStateException("Media card not defined for " + component.getName());
                }

                if(component == componentIn){
                    return index;
                }

                if(card.getValidateDropTarget().get()){
                    index++;
                }
            }

            return -1;
        }

        private static class MediaCardTransferable implements Transferable{

            private final MediaCard card;

            public MediaCardTransferable(MediaCard card){
                this.card = card;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors(){
                return new DataFlavor[]{MEDIA_CARD_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor){
                return MEDIA_CARD_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException{
                if(!MEDIA_CARD_FLAVOR.equals(flavor)){
                    throw new UnsupportedFlavorException(flavor);
                }

                return card;
            }
        }
    }
}
