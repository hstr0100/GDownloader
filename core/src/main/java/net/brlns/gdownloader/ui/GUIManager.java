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

import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.downloader.enums.CloseReasonEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.enums.QueueCategoryEnum;
import net.brlns.gdownloader.downloader.enums.QueueSortOrderEnum;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.ConnectivityStatusEvent;
import net.brlns.gdownloader.event.impl.PerformUpdateCheckEvent;
import net.brlns.gdownloader.event.impl.SettingsChangeEvent;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.ui.custom.*;
import net.brlns.gdownloader.ui.dnd.WindowDragSourceListener;
import net.brlns.gdownloader.ui.dnd.WindowDropTargetListener;
import net.brlns.gdownloader.ui.menu.NestedMenuEntry;
import net.brlns.gdownloader.ui.menu.RightClickMenu;
import net.brlns.gdownloader.ui.menu.RightClickMenuEntries;
import net.brlns.gdownloader.ui.menu.RunnableMenuEntry;
import net.brlns.gdownloader.ui.status.StatusIndicatorPanel;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.updater.AbstractGitUpdater;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.status.StatusIndicatorEnum.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO add custom tooltip to all buttons
@Slf4j
public final class GUIManager {

    static {
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(5000);
        ToolTipManager.sharedInstance().setEnabled(true);
    }

    @Getter
    private final GDownloader main;

    @Getter
    private final MediaCardManager mediaCardManager;

    @Getter
    private final DefaultMouseAdapter defaultMouseAdapter;

    private final SettingsPanel settingsPanel;
    private final WelcomeScreen welcomeScreen;

    @Getter
    private JFrame appWindow;
    private JPanel queuePanel;

    @Getter
    private JScrollPane queueScrollPane;

    private ContentPane currentContentPane;
    private JPanel emptyQueuePane;
    private JPanel updaterPane;

    public GUIManager(GDownloader mainIn) {
        main = mainIn;

        if (mainIn.getConfig().isDebugMode()) {
            EDTMonitor.install();
        }

        defaultMouseAdapter = new DefaultMouseAdapter();

        //uiScale = Math.clamp(mainIn.getConfig().getUiScale(), 0.5, 3.0);
        settingsPanel = new SettingsPanel(main, this);
        welcomeScreen = new WelcomeScreen(main, this);

        mediaCardManager = new MediaCardManager(main, this);
    }

    public String getCurrentAppIconPath() {
        return ThemeProvider.getTheme().getAppIconPath();
    }

    public String getCurrentTrayIconPath() {
        return ThemeProvider.getTheme().getTrayIconPath();
    }

    @Nullable
    public Image getAppIcon() {
        Image icon = null;

        try {
            icon = ImageIO.read(getClass().getResource(getCurrentAppIconPath()));
        } catch (IOException e) {
            GDownloader.handleException(e);
        }

        return icon;
    }

    public void displaySettingsPanel() {
        settingsPanel.createAndShowGUI();
    }

    public void displayWelcomeScreen() {
        welcomeScreen.createAndShowGUI();
    }

    public boolean isFullScreen() {
        return (appWindow.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    public void createAndShowGUI() {
        runOnEDT(() -> {
            setUpAppWindow();

            appWindow.setVisible(true);

            if ((appWindow.getExtendedState() & Frame.ICONIFIED) == 1) {
                appWindow.setExtendedState(JFrame.ICONIFIED);
                appWindow.setExtendedState(JFrame.NORMAL);
            }
        });
    }

    public void requestFocus() {
        runOnEDT(() -> {
            appWindow.requestFocus();

            if ((appWindow.getExtendedState() & Frame.ICONIFIED) != 1) {
                if (!appWindow.isVisible()) {
                    appWindow.setVisible(true);
                }

                if (appWindow.getExtendedState() == Frame.NORMAL) {
                    appWindow.toFront();
                }
            }
        });
    }

    public void refreshAppWindow() {
        runOnEDT(() -> {
            if (appWindow == null) {
                throw new RuntimeException("Called before initialization");
            }

            appWindow.setAlwaysOnTop(main.getConfig().isKeepWindowAlwaysOnTop());
            appWindow.setDefaultCloseOperation(main.getConfig().isExitOnClose()
                ? JFrame.EXIT_ON_CLOSE : JFrame.HIDE_ON_CLOSE);
        });
    }

    public void closeAppWindow() {
        runOnEDT(() -> {
            if (appWindow != null) {
                appWindow.setVisible(false);
            }
        });
    }

    private void setUpAppWindow() {
        assert SwingUtilities.isEventDispatchThread();

        if (appWindow == null) {
            // note to self, tooltips only show up when focused
            String version = System.getProperty("jpackage.app-version");

            appWindow = new JFrame(GDownloader.REGISTRY_APP_NAME + (version != null ? " v" + version : ""));
            refreshAppWindow();

            //appWindow.setResizable(false);
            //appWindow.setUndecorated(true);
            appWindow.setIconImage(getAppIcon());

            appWindow.addWindowStateListener((WindowEvent e) -> {
                if ((e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
                    appWindow.setAlwaysOnTop(false);
                } else {
                    appWindow.setAlwaysOnTop(main.getConfig().isKeepWindowAlwaysOnTop());
                }
            });

            appWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (!main.isSystemTrayInitialized()) {
                        log.info("System tray not available, exiting...");
                        main.shutdown();
                    }
                }
            });

            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventDispatcher((KeyEvent e) -> {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0
                        && e.getKeyCode() == KeyEvent.VK_V) {
                        main.getClipboardManager().updateClipboard(null, true);
                    }
                }

                return false;
            });

            appWindow.addMouseListener(defaultMouseAdapter);

            adjustWindowSize();

            JPanel mainPanel = new JPanel(new BorderLayout());
            // TODO: bug: this border leaves thumbnail artifacts behind every time the window is brought back from system tray
            mainPanel.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 5));
            mainPanel.setBackground(color(BACKGROUND));

            StatusIndicatorPanel statusIndicator = new StatusIndicatorPanel();
            appWindow.setGlassPane(statusIndicator);

            EventDispatcher.registerEDT(PerformUpdateCheckEvent.class, (event) -> {
                if (!event.isChecking()) {
                    if (!main.getFfmpegTranscoder().hasFFmpeg()) {
                        statusIndicator.addStatus(FFMPEG_NOT_FOUND);
                    } else {
                        statusIndicator.removeStatus(FFMPEG_NOT_FOUND);
                    }
                }
            });

            EventDispatcher.registerEDT(ConnectivityStatusEvent.class, (event) -> {
                if (!event.isActive()) {
                    statusIndicator.addStatus(NETWORK_OFFLINE);
                } else {
                    statusIndicator.removeStatus(NETWORK_OFFLINE);
                }
            });

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(color(BACKGROUND));
            headerPanel.add(createToolbar(), BorderLayout.SOUTH);
            mainPanel.add(headerPanel, BorderLayout.NORTH);

            queuePanel = new JPanel(new BorderLayout());
            queuePanel.setBackground(color(BACKGROUND));
            queuePanel.addMouseListener(defaultMouseAdapter);
            queuePanel.setOpaque(true);

            // Drag source used to initiate DnD autoscrolling
            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.addDragSourceListener(new WindowDragSourceListener(this));

            // Drag source used to listen for DnD URLs. The constructor itself assigns it to appWindow.
            DropTarget dropTarget = new DropTarget(appWindow, new WindowDropTargetListener(this));

            queueScrollPane = new JScrollPane(queuePanel);
            queueScrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
            queueScrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
            // BLIT_SCROLL_MODE is smooth but astonishingly slow, BACKINGSTORE_SCROLL_MODE is rough but fast.
            queueScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            queueScrollPane.setBorder(BorderFactory.createEmptyBorder());
            queueScrollPane.setBackground(color(BACKGROUND));
            queueScrollPane.getVerticalScrollBar().setUnitIncrement(8);
            queueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            queueScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            mainPanel.add(queueScrollPane, BorderLayout.CENTER);

            mediaCardManager.initializeQueueScrollPane(queueScrollPane);

            updateContentPane();

            EventDispatcher.registerEDT(PerformUpdateCheckEvent.class, (event) -> {
                updateContentPane();
            });

            appWindow.add(mainPanel);
        }
    }

    private void adjustWindowSize() {
        assert SwingUtilities.isEventDispatchThread();

        appWindow.setSize(675, 370);
        appWindow.setMinimumSize(new Dimension(appWindow.getWidth(), 225));

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(appWindow.getGraphicsConfiguration());
        int taskbarHeight = screenInsets.bottom;

        int windowX = screenSize.width - appWindow.getWidth() - 10;
        int windowY = screenSize.height - appWindow.getHeight() - taskbarHeight - 10;

        int minHeight = screenSize.height - appWindow.getHeight() - taskbarHeight - 10;
        if (windowY < minHeight) {
            appWindow.setSize(appWindow.getWidth(), screenSize.height - minHeight);
            windowY = minHeight;
        }

        appWindow.setLocation(windowX, windowY);
    }

    private JPanel createToolbar() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        topPanel.setBackground(color(BACKGROUND));
        topPanel.setOpaque(true);

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftButtonPanel.setOpaque(false);

        leftButtonPanel.add(createIconButton(
            loadIcon("/assets/add.png", ICON),
            loadIcon("/assets/add.png", ICON_HOVER),
            "gui.add_from_clipboard.tooltip",
            e -> main.getClipboardManager().pasteURLsFromClipboard()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 10, 0, 0);
        topPanel.add(leftButtonPanel, gbc);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.setOpaque(false);

        addStatusLabel(statusPanel, LIGHT_TEXT, (downloadManager) -> {
            return "<html>"
                + downloadManager.getRunningDownloads() + " " + l10n("gui.statusbar.running")
                + "<br>"
                + downloadManager.getCompletedDownloads() + " " + l10n("gui.statusbar.completed")
                + "</html>";
        });

        addStatusLabel(statusPanel, LIGHT_TEXT, (downloadManager) -> {
            return "<html>"
                + downloadManager.getQueuedDownloads() + " " + l10n("gui.statusbar.queued")
                + "<br>"
                + downloadManager.getFailedDownloads() + " " + l10n("gui.statusbar.failed")
                + "</html>";
        });

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 10, 0, 0);
        topPanel.add(statusPanel, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);

        JButton retryButton = createIconButton(
            loadIcon("/assets/redo.png", ICON),
            loadIcon("/assets/redo.png", ICON_HOVER),
            "gui.retry_failed_downloads.tooltip",
            e -> main.getDownloadManager().retryFailedDownloads()
        );
        retryButton.setVisible(false);
        buttonPanel.add(retryButton);

        EventDispatcher.registerEDT(DownloadManager.class, (downloadManager) -> {
            boolean shouldBeVisible = downloadManager.getFailedDownloads() != 0;
            if (retryButton.isVisible() != shouldBeVisible) {
                retryButton.setVisible(shouldBeVisible);
            }
        });

        buttonPanel.add(createToggleButton(
            (state) -> loadIcon("/assets/copy-link.png", state ? ICON_ACTIVE : ICON_INACTIVE),
            (state) -> loadIcon("/assets/copy-link.png", ICON_HOVER),
            (state) -> state
                ? "gui.stop_clipboard_monitor.tooltip"
                : "gui.start_clipboard_monitor.tooltip",
            main.getConfig()::isMonitorClipboardForLinks,
            () -> {
                main.getClipboardManager().invalidateClipboard();
                main.getConfig().setMonitorClipboardForLinks(!main.getConfig().isMonitorClipboardForLinks());

                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleButton(
            (state) -> loadIcon("/assets/mp3.png", state ? ICON_ACTIVE : ICON_INACTIVE),
            (state) -> loadIcon("/assets/mp3.png", ICON_HOVER),
            (state) -> state
                ? "gui.dont_download_audio.tooltip"
                : "gui.download_audio.tooltip",
            main.getConfig()::isDownloadAudio,
            () -> {
                main.getConfig().setDownloadAudio(!main.getConfig().isDownloadAudio());
                main.updateConfig();
            }
        ));

        buttonPanel.add(createToggleButton(
            (state) -> loadIcon("/assets/mp4.png", state ? ICON_ACTIVE : ICON_INACTIVE),
            (state) -> loadIcon("/assets/mp4.png", ICON_HOVER),
            (state) -> state
                ? "gui.dont_download_video.tooltip"
                : "gui.download_video.tooltip",
            main.getConfig()::isDownloadVideo,
            () -> {
                main.getConfig().setDownloadVideo(!main.getConfig().isDownloadVideo());
                main.updateConfig();
            }
        ));

        {
            JButton toogledDownloadsButton = createToggleDownloadsButton(
                (state) -> {
                    if (state) {
                        return loadIcon("/assets/pause.png", ICON);
                    } else {
                        if (main.getDownloadManager().getQueuedDownloads() > 0) {
                            return loadIcon("/assets/play.png", QUEUE_ACTIVE_ICON);
                        } else {
                            return loadIcon("/assets/play.png", ICON);
                        }
                    }
                },
                (state) -> state
                    ? loadIcon("/assets/pause.png", ICON_HOVER)
                    : loadIcon("/assets/play.png", ICON_HOVER),
                (state) -> state
                    ? "gui.stop_downloads.tooltip"
                    : "gui.start_downloads.tooltip",
                main.getDownloadManager()::isRunning,
                () -> {
                    main.getDownloadManager().toggleDownloads();
                }
            );

            toogledDownloadsButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        RightClickMenuEntries rightClickMenu = new RightClickMenuEntries();

                        for (AbstractDownloader downloader : main.getDownloadManager().getEnabledDownloaders()) {
                            DownloaderIdEnum downloaderId = downloader.getDownloaderId();
                            rightClickMenu.put(
                                l10n("gui.start_downloads.download_using", downloaderId.getDisplayName()),
                                new RunnableMenuEntry(() -> {
                                    main.getDownloadManager().stopDownloads();
                                    main.getDownloadManager().startDownloads(downloaderId);
                                })
                            );
                        }

                        showRightClickMenu(toogledDownloadsButton, rightClickMenu, e.getX(), e.getY());
                    }
                }
            });

            buttonPanel.add(toogledDownloadsButton);
        }

        {
            JButton clearQueueButton = createIconButton(
                loadIcon("/assets/erase.png", ICON),
                loadIcon("/assets/erase.png", ICON_HOVER),
                "gui.clear_download_queue.tooltip",
                e -> main.getDownloadManager().clearQueue(CloseReasonEnum.MANUAL)
            );

            RightClickMenuEntries rightClickMenu = new RightClickMenuEntries();
            rightClickMenu.put(l10n("gui.clear_download_queue.clear_failed"),
                new RunnableMenuEntry(() -> main.getDownloadManager()
                .clearQueue(QueueCategoryEnum.FAILED, CloseReasonEnum.MANUAL)));
            rightClickMenu.put(l10n("gui.clear_download_queue.clear_completed"),
                new RunnableMenuEntry(() -> main.getDownloadManager()
                .clearQueue(QueueCategoryEnum.COMPLETED, CloseReasonEnum.MANUAL)));
            rightClickMenu.put(l10n("gui.clear_download_queue.clear_queued"),
                new RunnableMenuEntry(() -> main.getDownloadManager()
                .clearQueue(QueueCategoryEnum.QUEUED, CloseReasonEnum.MANUAL)));
            rightClickMenu.put(l10n("gui.clear_download_queue.clear_running"),
                new RunnableMenuEntry(() -> main.getDownloadManager()
                .clearQueue(QueueCategoryEnum.RUNNING, CloseReasonEnum.MANUAL)));

            clearQueueButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showRightClickMenu(clearQueueButton, rightClickMenu, e.getX(), e.getY());
                    }
                }
            });

            buttonPanel.add(clearQueueButton);
        }

        {
            JButton settingsButton = createIconButton(
                loadIcon("/assets/settings.png", ICON),
                loadIcon("/assets/settings.png", ICON_HOVER),
                "settings.sidebar_title",
                e -> displaySettingsPanel()
            );

            RightClickMenuEntries rightClickMenu = new RightClickMenuEntries();
            // Why not add this to the same context menu as "Paste URLs," you ask?
            // Well, it would be quite easy to click it accidentally, which would be quite annoying.
            // Hiding it in the settings button seems like a good compromise to me.
            //
            // If the system tray is not initialized, it is still possible to bring
            // the window back up by launching another instance. No sanity check needed here.
            rightClickMenu.put(l10n("gui.minimize_to_system_tray"),
                new RunnableMenuEntry(() -> closeAppWindow()));

            settingsButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showRightClickMenu(settingsButton, rightClickMenu, e.getX(), e.getY());
                    }
                }
            });

            buttonPanel.add(settingsButton);
        }

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        topPanel.add(buttonPanel, gbc);

        return topPanel;
    }

    private void addStatusLabel(JPanel statusPanel, UIColors textColor, StatusLabelUpdater updater) {
        JLabel statusLabel = new JLabel("");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        statusLabel.setForeground(color(textColor));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setVerticalAlignment(SwingConstants.CENTER);

        Consumer<DownloadManager> consumer = (downloadManager) -> {
            statusLabel.setText(updater.updateText(downloadManager));
        };

        // Run once to set an initial state.
        consumer.accept(main.getDownloadManager());
        EventDispatcher.registerEDT(DownloadManager.class, consumer);

        statusPanel.add(statusLabel);
    }

    private static JButton createToggleDownloadsButton(
        Function<Boolean, ImageIcon> icon,
        Function<Boolean, ImageIcon> hoverIcon,
        Function<Boolean, String> tooltip,
        Supplier<Boolean> watch, Runnable toggler) {

        JButton button = createToggleButton(icon, hoverIcon, tooltip, watch, toggler);

        EventDispatcher.registerEDT(DownloadManager.class, (downloadManager) -> {
            boolean state = downloadManager.isRunning();
            button.setIcon(icon.apply(state));
            button.setToolTipText(l10n(tooltip.apply(state)));
        });

        return button;
    }

    private static JButton createToggleButton(
        Function<Boolean, ImageIcon> icon,
        Function<Boolean, ImageIcon> hoverIcon,
        Function<Boolean, String> tooltip,
        Supplier<Boolean> watch, Runnable toggler) {

        JButton button = new JButton(icon.apply(watch.get()));
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                boolean state = watch.get();

                button.setIcon(hoverIcon.apply(state));
                button.setToolTipText(l10n(tooltip.apply(state)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
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

    public static JButton createIconButton(ImageIcon icon, ImageIcon hoverIcon,
        String tooltipText, ActionListener actionListener) {
        JButton button = new JButton(icon);
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setIcon(hoverIcon);
                button.setToolTipText(l10n(tooltipText));
            }

            @Override
            public void mouseExited(MouseEvent e) {
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

    public static JButton createDialogButton(String text, UIColors backgroundColor,
        UIColors textColor, UIColors hoverColor) {
        CustomButton button = new CustomButton(text,
            color(hoverColor),
            color(hoverColor).brighter());

        button.setFocusPainted(false);
        button.setForeground(color(textColor));
        button.setBackground(color(backgroundColor));
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return button;
    }

    protected void updateContentPane() {
        runOnEDT(() -> {
            if (main.getDownloadManager().isBlocked()) {
                switchContentPane(ContentPane.UPDATER);
            } else {
                if (!mediaCardManager.isEmpty()) {
                    switchContentPane(ContentPane.MAIN_QUEUE);
                } else {
                    switchContentPane(ContentPane.EMPTY_QUEUE);
                }
            }
        });
    }

    private void switchContentPane(ContentPane pane) {
        if (currentContentPane == pane) {
            return;
        }

        currentContentPane = pane;

        queuePanel.removeAll();
        queuePanel.add(getContentPane(pane), BorderLayout.CENTER);
        queuePanel.revalidate();
        queuePanel.repaint();
    }

    private JPanel getContentPane(ContentPane pane) {
        return switch (pane) {
            case MAIN_QUEUE ->
                mediaCardManager.getOrCreateMediaQueuePanel();
            case EMPTY_QUEUE ->
                getOrCreateEmptyQueuePanel();
            case UPDATER ->
                getOrCreateUpdaterPanel();
            default ->
                throw new IllegalArgumentException("Unmapped content pane: " + pane);
        };
    }

    private JPanel getOrCreateEmptyQueuePanel() {
        if (emptyQueuePane != null) {
            return emptyQueuePane;
        }

        emptyQueuePane = new JPanel(new BorderLayout());
        emptyQueuePane.setOpaque(false);
        emptyQueuePane.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        emptyQueuePane.addMouseListener(defaultMouseAdapter);

        CustomDynamicLabel label = new CustomDynamicLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setLineWrapping(false);
        label.setCenterText(true);
        label.setForeground(color(FOREGROUND));
        emptyQueuePane.add(label, BorderLayout.CENTER);

        AtomicReference<String> lastString = new AtomicReference<>("");
        Consumer<Settings> labelUpdateTask = (config) -> {
            String newString = config.isMonitorClipboardForLinks()
                ? l10n("gui.empty_queue")
                : l10n("gui.empty_queue.enable_clipboard");

            if (!lastString.get().equals(newString)) {
                label.setFullText(newString);
                lastString.set(newString);
            }
        };

        labelUpdateTask.accept(main.getConfig());

        EventDispatcher.registerEDT(SettingsChangeEvent.class, (event) -> {
            labelUpdateTask.accept(event.getSettings());
        });

        return emptyQueuePane;
    }

    private JPanel getOrCreateUpdaterPanel() {
        if (updaterPane != null) {
            return updaterPane;
        }

        updaterPane = new JPanel(new GridBagLayout());
        updaterPane.setOpaque(false);
        updaterPane.setBackground(color(BACKGROUND));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 0, 5, 0);

        JLabel upperLabel = new JLabel("", SwingConstants.CENTER);
        upperLabel.setForeground(color(FOREGROUND));
        upperLabel.setText(l10n("gui.checking_updates"));
        updaterPane.add(upperLabel, gbc);

        JLabel bottomLabel = new JLabel("", SwingConstants.CENTER);
        bottomLabel.setForeground(color(FOREGROUND));
        bottomLabel.setText(l10n("gui.checking_updates.waiting_for_connectivity"));
        updaterPane.add(bottomLabel, gbc);

        EventDispatcher.registerEDT(PerformUpdateCheckEvent.class, (event) -> {
            if (event.isNetworkOnline()) {
                bottomLabel.setText(l10n("gui.checking_updates.please_wait"));
            } else {
                bottomLabel.setText(l10n("gui.checking_updates.waiting_for_connectivity"));
            }
        });

        JPanel spacerPanel = new JPanel();
        spacerPanel.setOpaque(false);
        spacerPanel.setPreferredSize(new Dimension(1, 20));
        updaterPane.add(spacerPanel, gbc);

        for (AbstractGitUpdater updater : main.getUpdateManager().getUpdaters()) {
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

            EventDispatcher.registerEDT(PerformUpdateCheckEvent.class, (event) -> {
                if (event.isChecking() && !event.isNetworkOnline()) {
                    progressBar.setValue(0);
                }
            });

            updaterRowPanel.add(updaterLabel, labelGbc);
            updaterRowPanel.add(progressBar, progressBarGbc);

            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            updaterPane.add(updaterRowPanel, gbc);

            updaterRowPanel.setVisible(updater.isEnabled());

            EventDispatcher.registerEDT(SettingsChangeEvent.class, (event) -> {
                updaterRowPanel.setVisible(updater.isEnabled());
            });

            updater.registerListener((status, progress) -> {
                runOnEDT(() -> {
                    switch (status) {
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
            });
        }

        return updaterPane;
    }

    public void showRightClickMenu(Component parentComponent, RightClickMenuEntries actions, int x, int y) {
        showRightClickMenu(parentComponent, actions, Collections.emptyList(), x, y);
    }

    public void showRightClickMenu(Component parentComponent, RightClickMenuEntries actions,
        Collection<RightClickMenuEntries> dependents, int x, int y) {

        RightClickMenu rightClickMenu = new RightClickMenu(main.getConfig().isKeepWindowAlwaysOnTop());
        rightClickMenu.showMenu(parentComponent, actions, dependents, x, y);
    }

    public void showConfirmDialog(String title, String message, int timeoutMs,
        DialogButton onClose, DialogButton... buttons) {

        runOnEDT(() -> {
            JDialog dialog = new JDialog(appWindow, title, Dialog.ModalityType.MODELESS) {
                private boolean actionPerformed = false;

                @Override
                public void dispose() {
                    if (!actionPerformed) {
                        actionPerformed = true;

                        onClose.getAction().accept(false);
                    }

                    super.dispose();
                }
            };

            // TODO: We might want to consider just bringing this dialog to top but not pinning it there.
            // Java doesn't directly support this but there are workarounds.
            dialog.setAlwaysOnTop(true);
            dialog.setSize(500, 300);
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(null);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setIconImage(getAppIcon());

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            panel.setBackground(color(BACKGROUND));

            JPanel dialogPanel = new JPanel(new BorderLayout());
            dialogPanel.setOpaque(false);
            dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

            CustomDynamicLabel dialogLabel = new CustomDynamicLabel();
            dialogLabel.setLineWrapping(true);
            dialogLabel.setFullText(message.split(System.lineSeparator()));
            dialogLabel.setForeground(color(FOREGROUND));
            dialogLabel.setHorizontalAlignment(SwingConstants.CENTER);
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

            for (DialogButton dialogButton : buttons) {
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

            Timer timer = new Timer(50, new ActionListener() {
                int elapsed = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    elapsed += 50;

                    int progress = 100 - (elapsed * 100) / timeoutMs;
                    dialogProgressBar.setValue(progress);

                    if (elapsed >= timeoutMs) {
                        ((Timer)e.getSource()).stop();
                        dialog.dispose();
                    }
                }
            });

            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    timer.start();
                }
            });

            southPanel.add(dialogProgressBar, BorderLayout.CENTER);
            southPanel.add(buttonPanel, BorderLayout.SOUTH);

            panel.add(southPanel, BorderLayout.SOUTH);

            dialog.add(panel);
            dialog.setVisible(true);
        });
    }

    protected final class DefaultMouseAdapter extends MouseAdapter {

        private final RightClickMenuEntries rightClickMenu = new RightClickMenuEntries();

        private RightClickMenuEntries getRightClickMenu() {
            rightClickMenu.putIfAbsent(l10n("gui.paste_url_from_clipboard"),
                new RunnableMenuEntry(() -> {
                    main.getClipboardManager().pasteURLsFromClipboard();
                }));

            NestedMenuEntry sortSubmenu = new NestedMenuEntry();
            for (QueueSortOrderEnum sortOrder : QueueSortOrderEnum.values()) {
                sortSubmenu.put(sortOrder.getDisplayName(),
                    new RunnableMenuEntry(
                        () -> main.getDownloadManager().setSortOrder(sortOrder),
                        () -> {
                            QueueSortOrderEnum current = main.getDownloadManager().getSortOrder();
                            return current == sortOrder
                                ? "/assets/selected.png"
                                : "/assets/not-selected.png";
                        }
                    ));
            }

            rightClickMenu.putIfAbsent(l10n("gui.sort_by"), sortSubmenu);

            return rightClickMenu;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                Component component = e.getComponent();
                if (component != null) {
                    showRightClickMenu(component, getRightClickMenu(), e.getX(), e.getY());
                }
            } else if (SwingUtilities.isLeftMouseButton(e)) {
                mediaCardManager.deselectAllMediaCards();
            }
        }
    }

    @FunctionalInterface
    public interface ButtonFunction {

        void accept(boolean selected);
    }

    @FunctionalInterface
    public interface StatusLabelUpdater {

        String updateText(DownloadManager downloadManager);
    }

    @Data
    public static class DialogButton {

        private final String title;
        private final ButtonFunction action;
    }

    private static enum ContentPane {
        MAIN_QUEUE,
        EMPTY_QUEUE,
        UPDATER
    }
}
