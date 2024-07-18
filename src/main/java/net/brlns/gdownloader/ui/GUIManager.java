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
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import static net.brlns.gdownloader.Language.*;
import net.brlns.gdownloader.YtDlpDownloader;
import net.brlns.gdownloader.ui.custom.CustomButton;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomToolTip;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class GUIManager{

    static{
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(5000);
        ToolTipManager.sharedInstance().setEnabled(true);

        UIManager.put("ToolTip.background", Color.DARK_GRAY);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(5, 5, 5, 5));

        UIManager.put("ComboBox.background", new ColorUIResource(Color.WHITE));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(Color.DARK_GRAY.brighter()));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(Color.LIGHT_GRAY));
        UIManager.put("ComboBox.borderPaintsFocus", Boolean.FALSE);
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

    private final ExecutorService threadPool;

    private final SettingsPanel settingsPanel;

    public GUIManager(GDownloader mainIn){
        main = mainIn;

        threadPool = Executors.newSingleThreadExecutor();
        settingsPanel = new SettingsPanel(main);

        setUpAppWindow();
    }

    public void displaySettingsPanel(){
        settingsPanel.createAndShowGUI();
    }

    public void wakeUp(){
        appWindow.setVisible(true);

        if((appWindow.getExtendedState() & Frame.ICONIFIED) == 1){
            appWindow.setExtendedState(JFrame.ICONIFIED);
            appWindow.setExtendedState(JFrame.NORMAL);
        }
    }

    //TODO: light theme
    public String getCurrentAppIconPath(){
        return "assets/app_icon.png";
    }

    public String getCurrentTrayIconPath(){
        return "assets/tray_icon.png";
    }

    public Image getAppIcon() throws IOException{
        Image icon = ImageIO.read(getClass().getClassLoader().getResource(getCurrentAppIconPath()));

        return icon;
    }

    private void constructToolBar(JPanel mainPanel){
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        topPanel.setBackground(Color.DARK_GRAY);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        statusPanel.setOpaque(false);

        {
            JLabel statusLabel = new JLabel("");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
            statusLabel.setForeground(Color.LIGHT_GRAY);
            statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
            statusLabel.setVerticalAlignment(SwingConstants.CENTER);

            Consumer<YtDlpDownloader> startConsumer = (YtDlpDownloader downloadManager) -> {
                statusLabel.setText(
                    "<html>"
                    + downloadManager.getDownloadsRunning() + " " + get("gui.statusbar.running")
                    + "<br>"
                    + downloadManager.getCompletedDownloads() + " " + get("gui.statusbar.completed")
                    + "</html>"
                );
            };
            startConsumer.accept(main.getDownloadManager());

            main.getDownloadManager().registerListener(startConsumer);

            statusPanel.add(statusLabel);
        }

        {
            JLabel statusLabel = new JLabel("");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
            statusLabel.setForeground(Color.LIGHT_GRAY);
            statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
            statusLabel.setVerticalAlignment(SwingConstants.CENTER);

            Consumer<YtDlpDownloader> startConsumer = (YtDlpDownloader downloadManager) -> {
                statusLabel.setText(
                    "<html>"
                    + downloadManager.getQueueSize() + " " + get("gui.statusbar.queued")
                    + "<br>"
                    + downloadManager.getFailedDownloads() + " " + get("gui.statusbar.failed")
                    + "</html>"
                );
            };
            startConsumer.accept(main.getDownloadManager());

            main.getDownloadManager().registerListener(startConsumer);

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
            loadIcon("assets/redo.png"),
            loadIcon("assets/redo-hover.png"),
            "gui.retry_failed_downloads.tooltip",
            e -> main.getDownloadManager().retryFailedDownloads()
        );
        retryButton.setVisible(false);
        buttonPanel.add(retryButton);

        main.getDownloadManager().registerListener((YtDlpDownloader downloadManager) -> {
            retryButton.setVisible(downloadManager.getFailedDownloads() != 0);
        });

        buttonPanel.add(createToggleButton(
            loadIcon("assets/copy-link.png"),
            loadIcon("assets/copy-link-hover.png"),
            loadIcon("assets/copy-link-inactive.png"),
            loadIcon("assets/copy-link-hover.png"),
            "gui.start_clipboard_monitor.tooltip",
            "gui.stop_clipboard_monitor.tooltip",
            main::isWatchClipboard,
            () -> {
                main.setWatchClipboard(!main.isWatchClipboard());
            }
        ));

        buttonPanel.add(createToggleButton(
            loadIcon("assets/mp3.png"),
            loadIcon("assets/mp3-hover.png"),
            loadIcon("assets/mp3-inactive.png"),
            loadIcon("assets/mp3-hover.png"),
            "gui.audio_only.tooltip",
            "gui.audio_and_video.tooltip",
            main.getConfig()::isDownloadAudioOnly,
            () -> {
                main.getConfig().setDownloadAudioOnly(!main.getConfig().isDownloadAudioOnly());
                main.updateConfig();
            }
        ));

        JButton startStopButton = createToggleDownloadsButton(
            loadIcon("assets/pause.png"),
            loadIcon("assets/pause-hover.png"),
            loadIcon("assets/play.png"),
            loadIcon("assets/play-hover.png"),
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
        );
        buttonPanel.add(startStopButton);

        JButton resumeButton = createButton(
            loadIcon("assets/erase.png"),
            loadIcon("assets/erase-hover.png"),
            "gui.clear_download_queue.tooltip",
            e -> main.getDownloadManager().clearQueue()
        );
        buttonPanel.add(resumeButton);

        JButton stopButton = createButton(
            loadIcon("assets/settings.png"),
            loadIcon("assets/settings-hover.png"),
            "settings.sidebar_title",
            e -> displaySettingsPanel()
        );
        buttonPanel.add(stopButton);

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

    private JButton createButton(ImageIcon icon, ImageIcon hoverIcon, String tooltipText, java.awt.event.ActionListener actionListener){
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

    private JButton createDialogButton(String text, Color backgroundColor, Color textColor, Color hoverColor){
        CustomButton button = new CustomButton(text);
        button.setHoverBackgroundColor(hoverColor);
        button.setPressedBackgroundColor(hoverColor.brighter());

        button.setFocusPainted(false);
        button.setForeground(textColor);
        button.setBackground(backgroundColor);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        return button;
    }

    public void showConfirmDialog(String title, String message, DialogButton... buttons){
        JDialog dialog = new JDialog((JFrame)null, title, true);
        dialog.setAlwaysOnTop(true);
        dialog.setSize(400, 260);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);

        try{
            dialog.setIconImage(getAppIcon());
        }catch(IOException e){
            main.handleException(e);
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(Color.DARK_GRAY);

        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setOpaque(false);
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JLabel dialogLabel = new JLabel(wrapText(50, message), SwingConstants.CENTER);
        dialogLabel.setForeground(Color.WHITE);
        dialogPanel.add(dialogLabel, BorderLayout.CENTER);

        panel.add(dialogPanel, BorderLayout.CENTER);

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setOpaque(false);
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JCheckBox rememberCheckBox = new JCheckBox(get("gui.remember_choice"));
        rememberCheckBox.setUI(new CustomCheckBoxUI());
        rememberCheckBox.setBackground(Color.DARK_GRAY);
        rememberCheckBox.setForeground(Color.WHITE);
        rememberCheckBox.setOpaque(false);
        checkboxPanel.add(rememberCheckBox);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);

        for(DialogButton dialogButton : buttons){
            JButton button = createDialogButton(
                dialogButton.getTitle(),
                Color.WHITE, Color.DARK_GRAY, new Color(128, 128, 128));

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

        adjustMessageWindowPosition();

        Color titleColor;
        Color textColor;
        switch(nextMessage.getMessageType()){
            case ERROR:
                titleColor = Color.RED;
                textColor = Color.GRAY;
                break;
            case WARNING:
                titleColor = Color.YELLOW;
                textColor = Color.GRAY;
                break;
            default:
                titleColor = new Color(0.85f, 0.85f, 0.85f, 1f);
                textColor = Color.WHITE;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setBackground(Color.DARK_GRAY);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel(wrapText(45, nextMessage.getTitle()));
        titleLabel.setForeground(titleColor);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        AtomicBoolean cancelHook = new AtomicBoolean(false);

        JButton closeButton = createButton(
            loadIcon("assets/x-mark.png", 12),
            loadIcon("assets/x-mark-alt.png", 12),
            "gui.close.tooltip",
            e -> cancelHook.set(true)
        );

        titlePanel.add(closeButton, BorderLayout.EAST);

        panel.add(titlePanel, BorderLayout.NORTH);

        messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

        JLabel messageLabel = new JLabel(wrapText(50, nextMessage.getMessage()), SwingConstants.CENTER);
        messageLabel.setForeground(textColor);
        messagePanel.add(messageLabel, BorderLayout.CENTER);

        panel.add(messagePanel, BorderLayout.CENTER);

        JProgressBar messageProgressBar = new JProgressBar(0, nextMessage.getDurationMillis());
        messageProgressBar.setValue(nextMessage.getDurationMillis());
        messageProgressBar.setStringPainted(false);
        messageProgressBar.setForeground(Color.WHITE);
        messageProgressBar.setBackground(Color.DARK_GRAY);
        messageProgressBar.setBorderPainted(false);
        messageProgressBar.setPreferredSize(new Dimension(messageWindow.getWidth() - 10, 5));

        panel.add(messageProgressBar, BorderLayout.SOUTH);

        messageWindow.add(panel);

        updateMessageWindowSize();
        messageWindow.setVisible(true);

        startProgressUpdate(cancelHook, nextMessage.durationMillis, messageProgressBar);

        if(nextMessage.isPlayTone() && main.getConfig().isPlaySounds()){
            AudioEngine.playNotificationTone();
        }
    }

    private void updateMessageWindowSize(){
        SwingUtilities.invokeLater(() -> {
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

            adjustMessageWindowPosition();
        });
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

    private void startProgressUpdate(AtomicBoolean cancelHook, int durationMillis, JProgressBar progressBar){
        threadPool.execute(() -> {
            for(int i = durationMillis; i > 0; i -= 50){
                if(cancelHook.get()){
                    break;
                }

                final int progressValue = i;
                SwingUtilities.invokeLater(() -> progressBar.setValue(progressValue));

                try{
                    Thread.sleep(50);
                }catch(InterruptedException e){
                    //Ignore
                }
            }

            SwingUtilities.invokeLater(() -> {
                messageWindow.setVisible(false);
                displayNextMessage();
            });
        });
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
                    SwingUtilities.invokeLater(() -> {
                        adjustMessageWindowPosition();
                    });
                }

                @Override
                public void windowIconified(WindowEvent e){
                    SwingUtilities.invokeLater(() -> {
                        adjustMessageWindowPosition();
                    });
                }

                @Override
                public void windowDeiconified(WindowEvent e){
                    SwingUtilities.invokeLater(() -> {
                        adjustMessageWindowPosition();
                    });
                }
            });

            updateProgressWindowSize();

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            mainPanel.setBackground(Color.DARK_GRAY);

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(Color.DARK_GRAY);

            constructToolBar(headerPanel);

            mainPanel.add(headerPanel, BorderLayout.NORTH);

            queuePanel = new JPanel();
            queuePanel.setLayout(new BoxLayout(queuePanel, BoxLayout.Y_AXIS));
            queuePanel.setBackground(Color.DARK_GRAY);

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
                        DataFlavor[] flavors = transferable.getTransferDataFlavors();

                        for(DataFlavor flavor : flavors){
                            try{
                                if(flavor.isFlavorTextType()){
                                    String text = transferable.getTransferData(flavor).toString();

                                    if(flavor.equals(GDownloader.FlavorType.STRING.getFlavor())
                                        || flavor.equals(GDownloader.FlavorType.HTML.getFlavor())){
                                        main.handleClipboardInput(text);
                                    }
                                }
                            }catch(UnsupportedFlavorException | IOException e){
                                log.warn(e.getLocalizedMessage());
                            }
                        }

                        dtde.dropComplete(true);
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
            queueScrollPane.setBackground(Color.DARK_GRAY);
            //queueScrollPane.getViewport().setBackground(Color.DARK_GRAY);
            queueScrollPane.getVerticalScrollBar().setUnitIncrement(4);
            queueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            queueScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

            JPanel borderPanel = new JPanel(new BorderLayout());
            borderPanel.setOpaque(false);
            borderPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

            borderPanel.add(queueScrollPane, BorderLayout.CENTER);

            mainPanel.add(borderPanel, BorderLayout.CENTER);

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

        JLabel messageLabel = new JLabel(get("gui.empty_queue"), SwingConstants.CENTER);
        messageLabel.setForeground(Color.WHITE);
        emptyQueuePanel.add(messageLabel, BorderLayout.CENTER);

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
        card.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 5));
        card.setBackground(new Color(80, 80, 80));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        JPanel thumbnailPanel = new JPanel();
        thumbnailPanel.setPreferredSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setMinimumSize(new Dimension(MediaCard.THUMBNAIL_WIDTH, MediaCard.THUMBNAIL_HEIGHT));
        thumbnailPanel.setBackground(new Color(74, 74, 74));
        thumbnailPanel.setLayout(new BorderLayout());

        if(video){
            ImageIcon noImageIcon = loadIcon("assets/video.png", 64);
            JLabel imageLabel = new JLabel(noImageIcon);
            thumbnailPanel.add(imageLabel, BorderLayout.CENTER);
        }else{
            ImageIcon noImageIcon = loadIcon("assets/winamp.png", 64);
            JLabel imageLabel = new JLabel(noImageIcon);
            thumbnailPanel.add(imageLabel, BorderLayout.CENTER);
        }

//        JLabel noImageLabel = new JLabel("No Image", SwingConstants.CENTER);
//        noImageLabel.setForeground(Color.WHITE);
//        thumbnailPanel.add(noImageLabel, BorderLayout.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(thumbnailPanel, gbc);

        JLabel mediaNameLabel = new JLabel(wrapText(50, mediaLabel));
        mediaNameLabel.setForeground(Color.WHITE);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        card.add(mediaNameLabel, gbc);

        CustomProgressBar progressBar = new CustomProgressBar(0, 100, Color.WHITE);
        progressBar.setValue(100);
        progressBar.setStringPainted(true);
        progressBar.setString(get("enums.download_status.queued"));
        progressBar.setForeground(Color.GRAY);
        progressBar.setBackground(Color.GRAY);
        progressBar.setBorderPainted(false);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        card.add(progressBar, gbc);

        JButton closeButton = createButton(
            loadIcon("assets/x-mark.png", 16),
            loadIcon("assets/x-mark-alt.png", 16),
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

        card.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e){
                if(mediaCard.getOnClick() != null){
                    mediaCard.getOnClick().apply();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e){
                card.setBackground(new Color(80, 80, 80).darker());
            }

            @Override
            public void mouseExited(MouseEvent e){
                card.setBackground(new Color(80, 80, 80));
            }
        });

        mediaCards.put(id, mediaCard);

        appWindow.revalidate();
        appWindow.repaint();

        scrollToBottom(queueScrollPane);

        appWindow.requestFocus();

        //updateProgressWindowSize();
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

    private void updateProgressWindowSize(){
        SwingUtilities.invokeLater(() -> {
            appWindow.setSize(550, 350);

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

            adjustMessageWindowPosition();
        });
    }

    private void adjustMessageWindowPosition(){
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

            //updateProgressWindowSize();
//            if(mediaCards.isEmpty()){
//                SwingUtilities.invokeLater(() -> {
//                    closeProgressWindow();
//                });
//            }
        }
    }

    public void closeProgressWindow(){
        if(appWindow != null){
            appWindow.setVisible(false);
//          mediaCards.clear();
//          queuePanel.removeAll();
            SwingUtilities.invokeLater(() -> {
                adjustMessageWindowPosition();
            });
        }
    }

    public static ImageIcon loadIcon(String path){
        return loadIcon(path, 32);
    }

    public static ImageIcon loadIcon(String path, int scale){
        ImageIcon icon = new ImageIcon(GUIManager.class.getClassLoader().getResource(path));
        Image image = icon.getImage().getScaledInstance(scale, scale, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
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
