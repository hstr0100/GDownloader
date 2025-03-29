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
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.plaf.ColorUIResource;
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
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.custom.*;
import net.brlns.gdownloader.ui.dnd.WindowDragSourceListener;
import net.brlns.gdownloader.ui.dnd.WindowDropTargetListener;
import net.brlns.gdownloader.ui.dnd.WindowTransferHandler;
import net.brlns.gdownloader.ui.menu.RightClickMenu;
import net.brlns.gdownloader.ui.menu.RightClickMenuEntries;
import net.brlns.gdownloader.ui.menu.RunnableMenuEntry;
import net.brlns.gdownloader.ui.message.AbstractMessenger;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.updater.AbstractGitUpdater;
import net.brlns.gdownloader.util.collection.ConcurrentLinkedHashSet;

import static net.brlns.gdownloader.lang.Language.*;
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
    private JFrame appWindow;
    private JPanel queuePanel;

    @Getter
    private JScrollPane queueScrollPane;

    private JPanel emptyQueuePanel;
    private JPanel updaterPanel;

    private final AtomicBoolean currentlyUpdatingMediaCards = new AtomicBoolean();
    private final AtomicLong lastMediaCardQueueUpdate = new AtomicLong();
    private final Queue<MediaCardUIUpdateEntry> mediaCardUIUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Map<Integer, MediaCard> mediaCards = new ConcurrentHashMap<>();

    private final AtomicInteger mediaCardId = new AtomicInteger();

    private final ConcurrentLinkedHashSet<Integer> selectedMediaCards = new ConcurrentLinkedHashSet<>();
    private final AtomicReference<MediaCard> lastSelectedMediaCard = new AtomicReference<>(null);
    private final AtomicBoolean isMultiSelectMode = new AtomicBoolean();

    @Getter
    private final GDownloader main;

    private final SettingsPanel settingsPanel;

    public GUIManager(GDownloader mainIn) {
        main = mainIn;

        if (mainIn.getConfig().isDebugMode()) {
            EDTMonitor.install();
        }

        //uiScale = Math.clamp(mainIn.getConfig().getUiScale(), 0.5, 3.0);
        settingsPanel = new SettingsPanel(main, this);

        UIManager.put("ToolTip.background", color(TOOLTIP_BACKGROUND));
        UIManager.put("ToolTip.foreground", color(TOOLTIP_FOREGROUND));
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(5, 5, 5, 5));

        UIManager.put("ComboBox.background", new ColorUIResource(color(COMBO_BOX_BACKGROUND)));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(color(COMBO_BOX_SELECTION_BACKGROUND)));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(color(COMBO_BOX_SELECTION_FOREGROUND)));
        UIManager.put("ComboBox.borderPaintsFocus", Boolean.FALSE);

        Timer mediaCardQueueTimer = new Timer(50, e -> processMediaCardQueue());
        mediaCardQueueTimer.start();
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

    private AbstractMessenger _popupMessenger;

    public void showPopupMessage(String title, String message, int durationMillis,
        MessageTypeEnum messageType, boolean playTone, boolean discardDuplicates) {
        if (_popupMessenger == null) {
            _popupMessenger = new PopupMessenger(this);
        }

        _popupMessenger.show(title, message, durationMillis, messageType, playTone, discardDuplicates);
    }

    private AbstractMessenger _toastMessenger;

    public void showToastMessage(String message, int durationMillis,
        MessageTypeEnum messageType, boolean playTone, boolean discardDuplicates) {
        if (_toastMessenger == null) {
            _toastMessenger = new ToastMessenger(this);
        }

        _toastMessenger.show("", message, durationMillis, messageType, playTone, discardDuplicates);
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

                adjustMediaCards();
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

            DefaultMouseAdapter mouseAdapter = new DefaultMouseAdapter();
            appWindow.addMouseListener(mouseAdapter);

            adjustWindowSize();

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            mainPanel.setBackground(color(BACKGROUND));

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(color(BACKGROUND));
            headerPanel.add(createToolbar(), BorderLayout.SOUTH);
            mainPanel.add(headerPanel, BorderLayout.NORTH);

            queuePanel = new JPanel();
            queuePanel.setLayout(new BoxLayout(queuePanel, BoxLayout.Y_AXIS));
            queuePanel.setBackground(color(BACKGROUND));
            queuePanel.addMouseListener(mouseAdapter);

            // Drag source used to initiate DnD autoscrolling
            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.addDragSourceListener(new WindowDragSourceListener(this));

            // Drag source used to listen for DnD URLs. The constructor itself assigns it to appWindow.
            DropTarget dropTarget = new DropTarget(appWindow, new WindowDropTargetListener(this));

            queuePanel.add(getOrCreateEmptyQueuePanel(), BorderLayout.CENTER);
            updateQueuePanelMessage();

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

            InputMap inputMap = queuePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = queuePanel.getActionMap();
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "selectAllCards");
            actionMap.put("selectAllCards", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selectAllMediaCards();
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedCards");
            actionMap.put("deleteSelectedCards", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteSelectedMediaCards();
                }
            });

            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
                if (e.getID() == KeyEvent.KEY_RELEASED) {
                    if (!e.isControlDown() && !e.isShiftDown()) {
                        isMultiSelectMode.set(false);
                    }
                }

                return false;
            });

            appWindow.add(mainPanel);

            appWindow.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    adjustMediaCards();
                }
            });
        }
    }

    private void adjustWindowSize() {
        assert SwingUtilities.isEventDispatchThread();

        appWindow.setSize(670, 370);
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

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        leftButtonPanel.setOpaque(false);

        leftButtonPanel.add(createButton(
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

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
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
        gbc.insets = new Insets(0, 10, 0, 20);
        topPanel.add(statusPanel, gbc);

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

        EventDispatcher.register(DownloadManager.class, (downloadManager) -> {
            runOnEDT(() -> {
                boolean shouldBeVisible = downloadManager.getFailedDownloads() != 0;
                if (retryButton.isVisible() != shouldBeVisible) {
                    retryButton.setVisible(shouldBeVisible);
                }
            });
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

                updateQueuePanelMessage();
                main.updateConfig();
            }
        ));

        EventDispatcher.register(DownloadManager.class, (downloadManager) -> {
            updateQueuePanelMessage();
        });

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
            JButton clearQueueButton = createButton(
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
            JButton settingsButton = createButton(
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
            runOnEDT(() -> {
                statusLabel.setText(updater.updateText(downloadManager));
            });
        };

        // Run once to set an initial state.
        consumer.accept(main.getDownloadManager());
        EventDispatcher.register(DownloadManager.class, consumer);

        statusPanel.add(statusLabel);
    }

    private JButton createToggleDownloadsButton(
        Function<Boolean, ImageIcon> icon,
        Function<Boolean, ImageIcon> hoverIcon,
        Function<Boolean, String> tooltip,
        Supplier<Boolean> watch, Runnable toggler) {

        JButton button = createToggleButton(icon, hoverIcon, tooltip, watch, toggler);

        EventDispatcher.register(DownloadManager.class, (downloadManager) -> {
            boolean state = downloadManager.isRunning();

            runOnEDT(() -> {
                button.setIcon(icon.apply(state));
                button.setToolTipText(l10n(tooltip.apply(state)));
            });
        });

        return button;
    }

    private JButton createToggleButton(
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

    public JButton createButton(ImageIcon icon, ImageIcon hoverIcon,
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

    private JButton createDialogButton(String text, UIColors backgroundColor,
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

    private JPanel getOrCreateEmptyQueuePanel() {
        if (emptyQueuePanel != null) {
            return emptyQueuePanel;
        }

        emptyQueuePanel = new JPanel(new BorderLayout());
        emptyQueuePanel.setOpaque(false);
        emptyQueuePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JLabel messageLabel = new JLabel("", SwingConstants.CENTER);
        messageLabel.setForeground(color(FOREGROUND));
        emptyQueuePanel.add(messageLabel, BorderLayout.CENTER);

        return emptyQueuePanel;
    }

    private JPanel getOrCreateUpdaterPanel() {
        if (updaterPanel != null) {
            return updaterPanel;
        }

        updaterPanel = new JPanel(new GridBagLayout());
        updaterPanel.setOpaque(false);
        updaterPanel.setBackground(color(BACKGROUND));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 0, 5, 0);

        JLabel upperLabel = new JLabel("", SwingConstants.CENTER);
        upperLabel.setForeground(color(FOREGROUND));
        upperLabel.setText(l10n("gui.checking_updates"));
        updaterPanel.add(upperLabel, gbc);

        JLabel bottomLabel = new JLabel("", SwingConstants.CENTER);
        bottomLabel.setForeground(color(FOREGROUND));
        bottomLabel.setText(l10n("gui.checking_updates.please_wait"));
        updaterPanel.add(bottomLabel, gbc);

        JPanel spacerPanel = new JPanel();
        spacerPanel.setOpaque(false);
        spacerPanel.setPreferredSize(new Dimension(1, 20));
        updaterPanel.add(spacerPanel, gbc);

        for (AbstractGitUpdater updater : main.getUpdaters()) {
            if (!updater.isSupported()) {
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
            updaterPanel.add(updaterRowPanel, gbc);

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

        return updaterPanel;
    }

    private void updateQueuePanelMessage() {
        runOnEDT(() -> {
            JPanel panel = getOrCreateEmptyQueuePanel();

            Component firstComponent = panel.getComponent(0);

            if (!main.getDownloadManager().isBlocked()) {
                if (!(firstComponent instanceof JLabel)) {
                    panel.removeAll();

                    JLabel label = new JLabel("", SwingConstants.CENTER);
                    label.setForeground(color(FOREGROUND));
                    panel.add(label, BorderLayout.CENTER);
                }

                JLabel label = (JLabel)panel.getComponent(0);
                label.setText(main.getConfig().isMonitorClipboardForLinks()
                    ? l10n("gui.empty_queue")
                    : l10n("gui.empty_queue.enable_clipboard"));
            } else {
                if (!(firstComponent instanceof JPanel)) {
                    panel.removeAll();
                    panel.add(getOrCreateUpdaterPanel(), BorderLayout.CENTER);
                }
            }

            panel.revalidate();
            panel.repaint();
        });
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

    public boolean isFullScreen() {
        return (appWindow.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    private void showRightClickMenu(Component parentComponent, RightClickMenuEntries actions, int x, int y) {
        showRightClickMenu(parentComponent, actions, Collections.emptyList(), x, y);
    }

    private void showRightClickMenu(Component parentComponent, RightClickMenuEntries actions,
        Collection<RightClickMenuEntries> dependents, int x, int y) {

        RightClickMenu rightClickMenu = new RightClickMenu(main.getConfig().isKeepWindowAlwaysOnTop());
        rightClickMenu.showMenu(parentComponent, actions, dependents, x, y);
    }

    private void adjustMediaCards() {
        assert SwingUtilities.isEventDispatchThread();

        for (MediaCard card : mediaCards.values()) {
            card.adjustScale(appWindow.getWidth());
        }

        queuePanel.revalidate();
        queuePanel.repaint();
    }

    private void processMediaCardQueue() {
        if (mediaCardUIUpdateQueue.isEmpty()
            || currentlyUpdatingMediaCards.get()
            // Give the EDT some room for breathing
            || (System.currentTimeMillis() - lastMediaCardQueueUpdate.get()) < 100) {
            return;
        }

        runOnEDT(() -> {
            if (log.isDebugEnabled()) {
                log.debug("Items in queue: {}", mediaCardUIUpdateQueue.size());
            }

            boolean scrollToBottom = false;

            queuePanel.setIgnoreRepaint(true);

            try {
                int count = 0;
                while (!mediaCardUIUpdateQueue.isEmpty()) {
                    if (++count == 256) {// Process in batches of 255 items every 100ms
                        break;
                    }

                    MediaCardUIUpdateEntry entry = mediaCardUIUpdateQueue.poll();
                    if (entry != null) {
                        MediaCard mediaCard = entry.getMediaCard();

                        if (entry.getUpdateType() == CARD_ADD) {
                            JPanel card = new JPanel() {
                                @Override
                                public Dimension getMaximumSize() {
                                    int availableWidth = appWindow.getWidth() - getInsets().left - getInsets().right;
                                    return new Dimension(availableWidth, super.getMaximumSize().height);
                                }
                            };
                            card.setLayout(new GridBagLayout());
                            card.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 5));
                            card.setBackground(color(MEDIA_CARD));

                            int fontSize = main.getConfig().getFontSize();
                            Dimension cardDimension = new Dimension(Integer.MAX_VALUE, fontSize >= 15 ? 150 + (fontSize - 15) * 3 : 135);
                            card.setMaximumSize(cardDimension);

                            GridBagConstraints gbc = new GridBagConstraints();
                            gbc.insets = new Insets(10, 10, 10, 10);
                            gbc.fill = GridBagConstraints.BOTH;

                            // Dragidy-draggy-nub-thingy
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

                            // Thumbnail
                            CustomThumbnailPanel thumbnailPanel = new CustomThumbnailPanel();
                            thumbnailPanel.setPreferredSize(new Dimension(
                                CustomMediaCardUI.THUMBNAIL_WIDTH, CustomMediaCardUI.THUMBNAIL_HEIGHT));
                            thumbnailPanel.setMinimumSize(new Dimension(
                                CustomMediaCardUI.THUMBNAIL_WIDTH, CustomMediaCardUI.THUMBNAIL_HEIGHT));
                            thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
                            thumbnailPanel.setLayout(new BorderLayout());
                            thumbnailPanel.setPlaceholderIcon(DownloadTypeEnum.ALL);

                            gbc.insets = new Insets(10, 0, 10, 0);
                            gbc.gridx = 1;
                            gbc.gridy = 0;
                            gbc.gridheight = 2;
                            gbc.weightx = 0;
                            gbc.weighty = 0;
                            card.add(thumbnailPanel, gbc);

                            CustomDynamicLabel mediaNameLabel = new CustomDynamicLabel();
                            mediaNameLabel.setForeground(color(FOREGROUND));
                            gbc.insets = new Insets(10, 10, 10, 10);
                            gbc.gridx = 2;
                            gbc.gridy = 0;
                            gbc.gridheight = 1;
                            gbc.weightx = 1;
                            gbc.fill = GridBagConstraints.HORIZONTAL;
                            gbc.weighty = 0;
                            card.add(mediaNameLabel, gbc);

                            appWindow.addComponentListener(mediaNameLabel.getListener());

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
                            gbc.fill = GridBagConstraints.BOTH;
                            card.add(progressBar, gbc);

                            JButton closeButton = createButton(
                                loadIcon("/assets/x-mark.png", ICON, 16),
                                loadIcon("/assets/x-mark.png", ICON_CLOSE, 16),
                                "gui.remove_from_queue.tooltip",
                                e -> {
                                    if (isMediaCardSelected(mediaCard.getId())) {
                                        deleteSelectedMediaCards();
                                    }

                                    removeMediaCard(mediaCard.getId(), CloseReasonEnum.MANUAL);
                                }
                            );
                            closeButton.setPreferredSize(new Dimension(16, 16));

                            gbc.gridx = 3;
                            gbc.gridy = 0;
                            gbc.gridheight = 2;
                            gbc.weightx = 0;
                            gbc.weighty = 0;
                            gbc.anchor = GridBagConstraints.CENTER;
                            card.add(closeButton, gbc);

                            card.setTransferHandler(new WindowTransferHandler(this));

                            MouseAdapter listener = new MouseAdapter() {
                                private long lastClick = System.currentTimeMillis();

                                @Override
                                public void mousePressed(MouseEvent e) {
                                    if (isMultiSelectMode.get() && selectedMediaCards.size() > 1) {
                                        return;
                                    }

                                    Component component = e.getComponent();

                                    if (component.equals(dragLabel)) {
                                        TransferHandler handler = card.getTransferHandler();

                                        if (handler != null) {// peace of mind
                                            handler.exportAsDrag(card, e, TransferHandler.MOVE);
                                        }
                                    }
                                }

                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    if (SwingUtilities.isLeftMouseButton(e)) {
                                        MediaCard lastCard = lastSelectedMediaCard.get();

                                        int cardId = mediaCard.getId();

                                        if (e.isControlDown()) {
                                            isMultiSelectMode.set(true);

                                            if (selectedMediaCards.contains(cardId)) {
                                                selectedMediaCards.remove(cardId);
                                            } else {
                                                selectedMediaCards.add(cardId);
                                            }

                                            updateMediaCardSelectionState();
                                        } else if (e.isShiftDown() && lastCard != null) {
                                            isMultiSelectMode.set(true);

                                            selectMediaCardRange(lastCard, mediaCard);
                                        } else {
                                            if (e.getClickCount() == 2) {
                                                if (mediaCard.getOnLeftClick() != null && (System.currentTimeMillis() - lastClick) > 50) {
                                                    mediaCard.getOnLeftClick().run();

                                                    lastClick = System.currentTimeMillis();
                                                }
                                            }

                                            selectedMediaCards.replaceAll(Collections.singletonList(cardId));
                                            lastSelectedMediaCard.set(mediaCard);

                                            updateMediaCardSelectionState();
                                        }
                                    } else if (SwingUtilities.isRightMouseButton(e)) {
                                        List<RightClickMenuEntries> dependents = new ArrayList<>();

                                        if (isMediaCardSelected(mediaCard)) {
                                            for (int cardId : selectedMediaCards) {
                                                MediaCard selected = mediaCards.get(cardId);
                                                if (selected == null) {
                                                    log.error("Cannot find media card, id {}", cardId);
                                                    continue;
                                                }

                                                if (selected == mediaCard) {
                                                    continue;
                                                }

                                                dependents.add(RightClickMenuEntries.fromMap(selected.getRightClickMenu()));
                                            }
                                        }

                                        showRightClickMenu(card, RightClickMenuEntries.fromMap(mediaCard.getRightClickMenu()),
                                            dependents, e.getX(), e.getY());
                                    }
                                }

                                @Override
                                public void mouseEntered(MouseEvent e) {
                                    if (!isMediaCardSelected(mediaCard) && !isMultiSelectMode.get()) {
                                        card.setBackground(color(MEDIA_CARD_HOVER));
                                    }
                                }

                                @Override
                                public void mouseExited(MouseEvent e) {
                                    if (!isMediaCardSelected(mediaCard) && !isMultiSelectMode.get()) {
                                        card.setBackground(color(MEDIA_CARD));
                                    }
                                }
                            };

                            card.addMouseListener(listener);
                            dragLabel.addMouseListener(listener);
                            mediaNameLabel.addMouseListener(listener);

                            card.putClientProperty("MEDIA_CARD", mediaCard);

                            mediaCard.setUi(new CustomMediaCardUI(
                                card, cardDimension, mediaNameLabel, thumbnailPanel, progressBar
                            ));

                            queuePanel.remove(getOrCreateEmptyQueuePanel());
                            queuePanel.add(card);

                            scrollToBottom = true;
                        } else if (entry.getUpdateType() == CARD_REMOVE) {
                            CustomMediaCardUI ui = mediaCard.getUi();
                            if (ui != null) {
                                try {
                                    if (mediaCards.isEmpty()) {
                                        queuePanel.removeAll();

                                        queuePanel.add(getOrCreateEmptyQueuePanel(), BorderLayout.CENTER);
                                    } else {
                                        queuePanel.remove(ui.getCard());
                                    }

                                    appWindow.removeComponentListener(ui.getMediaNameLabel().getListener());
                                } catch (StackOverflowError e) {
                                    // Decades-old AWT issue. We should not have to raise the stack limit for this.
                                    // AWTEventMulticaster.remove(AWTEventMulticaster.java:153)
                                    // AWTEventMulticaster.removeInternal(AWTEventMulticaster.java:983)
                                    // Rinse and repeat ∞
                                    GDownloader.handleException(e, "StackOverflowError when calling remove() or removeComponentListener().");
                                }
                            }
                        }
                    }
                }
            } finally {
                lastMediaCardQueueUpdate.set(System.currentTimeMillis());
                currentlyUpdatingMediaCards.set(false);

                if (mediaCardUIUpdateQueue.isEmpty()) {
                    queuePanel.setIgnoreRepaint(false);

                    setUpAppWindow();

                    if (!appWindow.isVisible()) {
                        appWindow.setVisible(true);
                    }

                    appWindow.revalidate();
                    appWindow.repaint();
                }

                if (main.getConfig().isAutoScrollToBottom() && scrollToBottom) {
                    scrollToBottom(queueScrollPane);
                }

                int currentMode = queueScrollPane.getViewport().getScrollMode();
                if (queuePanel.getComponentCount() > 100 && currentMode != JViewport.BACKINGSTORE_SCROLL_MODE) {
                    queueScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
                } else if (currentMode != JViewport.SIMPLE_SCROLL_MODE) {
                    queueScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                }
            }
        });
    }

    public MediaCard addMediaCard(String... mediaLabel) {
        int id = mediaCardId.incrementAndGet();

        MediaCard mediaCard = new MediaCard(id);
        mediaCard.adjustScale(appWindow.getWidth());
        mediaCard.setLabel(mediaLabel);
        mediaCards.put(id, mediaCard);

        mediaCardUIUpdateQueue.add(new MediaCardUIUpdateEntry(CARD_ADD, mediaCard));

        return mediaCard;
    }

    public void removeMediaCard(int id, CloseReasonEnum reason) {
        MediaCard mediaCard = mediaCards.remove(id);

        if (mediaCard != null) {
            mediaCard.close(reason);

            selectedMediaCards.remove(mediaCard.getId());

            mediaCardUIUpdateQueue.add(new MediaCardUIUpdateEntry(CARD_REMOVE, mediaCard));
        }
    }

    private void updateMediaCardSelectionState() {
        for (MediaCard mediaCard : mediaCards.values()) {
            boolean isSelected = isMediaCardSelected(mediaCard);

            CustomMediaCardUI ui = mediaCard.getUi();
            if (ui != null) {
                JPanel card = ui.getCard();
                card.setBackground(isSelected ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
            }
        }
    }

    private void selectAllMediaCards() {
        selectedMediaCards.replaceAll(mediaCards.keySet());

        updateMediaCardSelectionState();
    }

    private void deleteSelectedMediaCards() {
        for (int cardId : selectedMediaCards) {
            removeMediaCard(cardId, CloseReasonEnum.MANUAL);
        }

        selectedMediaCards.clear();

        updateMediaCardSelectionState();
    }

    private void deselectAllMediaCards() {
        selectedMediaCards.clear();

        updateMediaCardSelectionState();
    }

    private boolean isMediaCardSelected(MediaCard card) {
        return isMediaCardSelected(card.getId());
    }

    private boolean isMediaCardSelected(int cardId) {
        return selectedMediaCards.contains(cardId);
    }

    private void selectMediaCardRange(MediaCard start, MediaCard end) {
        if (start.getUi() == null || end.getUi() == null) {
            throw new IllegalStateException("Expected MediaCard UI to be initialized");
        }

        int startIndex = getComponentIndex(start.getUi().getCard());
        int endIndex = getComponentIndex(end.getUi().getCard());

        if (startIndex == -1 || endIndex == -1) {
            return;
        }

        int minIndex = Math.min(startIndex, endIndex);
        int maxIndex = Math.max(startIndex, endIndex);

        List<Integer> cardsToAdd = new ArrayList<>();
        for (int i = minIndex; i <= maxIndex; i++) {
            MediaCard card = getMediaCardAt(i);

            if (card == null) {
                log.error("Cannot find card for index {}", i);
                continue;
            }

            cardsToAdd.add(card.getId());
        }

        selectedMediaCards.replaceAll(cardsToAdd);

        runOnEDT(() -> {
            updateMediaCardSelectionState();
        });
    }

    public boolean handleMediaCardDnD(MediaCard mediaCard, Component dropTarget) {
        CustomMediaCardUI ui = mediaCard.getUi();

        if (ui != null) {
            JPanel sourcePanel = ui.getCard();
            Rectangle windowBounds = appWindow.getBounds();
            Point dropLocation = dropTarget.getLocationOnScreen();

            if (windowBounds.contains(dropLocation) && dropTarget instanceof JPanel jPanel) {
                runOnEDT(() -> {
                    int targetIndex = getComponentIndex(jPanel);

                    if (mediaCard.getOnDrag() != null) {
                        int validIndex = getValidMediaCardIndex(jPanel);

                        mediaCard.getOnDrag().accept(validIndex);
                    }

                    queuePanel.remove(sourcePanel);
                    queuePanel.add(sourcePanel, targetIndex);
                    queuePanel.revalidate();
                    queuePanel.repaint();
                });

                return true;
            }
        }

        return false;
    }

    private int getComponentIndex(JPanel component) {
        Component[] components = queuePanel.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == component) {
                return i;
            }
        }

        return -1;
    }

    private int getValidMediaCardIndex(JPanel componentIn) {
        int index = 0;
        for (Component component : queuePanel.getComponents()) {
            MediaCard card = (MediaCard)((JPanel)component).getClientProperty("MEDIA_CARD");
            if (card == null) {
                throw new IllegalStateException("Media card not defined for " + component.getName());
            }

            if (component == componentIn) {
                return index;
            }

            if (card.getValidateDropTarget().get()) {
                index++;
            }
        }

        return -1;
    }

    @Nullable
    private MediaCard getMediaCardAt(int index) {
        if (index < 0 || index > queuePanel.getComponents().length) {
            log.error("Index {} is out of bounds", index);
            return null;
        }

        Component component = queuePanel.getComponents()[index];

        return (MediaCard)((JPanel)component).getClientProperty("MEDIA_CARD");
    }

    @Data
    private static class ImageCacheKey {

        private final String path;
        private final Color color;
        private final int scale;
    }

    private static final Map<ImageCacheKey, ImageIcon> _imageCache = new ConcurrentHashMap<>();// Less contention, virtual-thread safe

    public static ImageIcon loadIcon(String path, UIColors color) {
        return loadIcon(path, color, 36);
    }

    public static ImageIcon loadIcon(String path, UIColors color, int scale) {
        Color themeColor = color(color);
        ImageCacheKey key = new ImageCacheKey(path, themeColor, scale);

        return _imageCache.computeIfAbsent(key, (keyIn) -> {
            try (
                InputStream resourceStream = GUIManager.class.getResourceAsStream(path)) {
                BufferedImage originalImage = ImageIO.read(resourceStream);
                if (originalImage == null) {
                    throw new IOException("Failed to load image: " + path);
                }

                WritableRaster raster = originalImage.getRaster();

                for (int y = 0; y < originalImage.getHeight(); y++) {
                    for (int x = 0; x < originalImage.getWidth(); x++) {
                        int[] pixel = raster.getPixel(x, y, (int[])null);

                        int alpha = pixel[3];

                        if (alpha != 0) {
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

                return icon;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // https://stackoverflow.com/questions/5147768/scroll-jscrollpane-to-bottom
    private static void scrollToBottom(JScrollPane scrollPane) {
        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        verticalBar.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                Adjustable adjustable = e.getAdjustable();
                if (adjustable.getValue() + adjustable.getVisibleAmount() >= adjustable.getMaximum()) {
                    return;
                }

                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        });
    }

    public static String wrapTextInHtml(int maxLineLength, String... lines) {
        if (maxLineLength < 20) {
            throw new IllegalArgumentException("Line length too short: " + maxLineLength);
        }

        StringBuilder wrappedText = new StringBuilder();

        for (String text : lines) {
            text = text.replace(System.lineSeparator(), "<br>");

            for (String line : text.split("<br>")) {
                if (line.isEmpty()) {
                    wrappedText.append("<br>");
                    continue;
                }

                if (line.length() > maxLineLength) {
                    int count = 0;

                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        wrappedText.append(c);

                        if (++count == maxLineLength) {
                            wrappedText.append("<br>");
                            count = 0;
                        }
                    }
                } else {
                    String[] words = line.split(" ");
                    int lineLength = 0;

                    for (String word : words) {
                        if (lineLength + word.length() > maxLineLength) {
                            wrappedText.append("<br>").append(word).append(" ");
                            lineLength = word.length() + 1; // reset lineLength
                        } else {
                            wrappedText.append(word).append(" ");
                            lineLength += word.length() + 1;
                        }
                    }
                }

                if (!wrappedText.toString().trim().endsWith("<br>")) {
                    wrappedText.append("<br>");
                }
            }
        }

        String result = wrappedText.toString().trim();
        if (result.endsWith("<br>")) {
            result = result.substring(0, result.length() - 4);
        }

        return "<html>" + result + "</html>";
    }

    /**
     * Executes the given {@link Runnable} on the Event Dispatch Thread (EDT).
     * If the current thread is the EDT, the {@code runnable} will be executed immediately.
     * Otherwise, it will be scheduled to run later on the EDT using {@link SwingUtilities#invokeLater}.
     *
     * @param runnable the {@link Runnable} to be executed on the Event Dispatch Thread
     */
    public static void runOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private final class DefaultMouseAdapter extends MouseAdapter {

        private final RightClickMenuEntries rightClickMenu = new RightClickMenuEntries();

        private RightClickMenuEntries getRightClickMenu() {
            rightClickMenu.putIfAbsent(l10n("gui.paste_url_from_clipboard"),
                new RunnableMenuEntry(() -> {
                    main.getClipboardManager().pasteURLsFromClipboard();
                }));

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
                deselectAllMediaCards();
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

    private static final byte CARD_REMOVE = 0x00;
    private static final byte CARD_ADD = 0x01;

    @Data
    private static class MediaCardUIUpdateEntry {

        private final byte updateType;
        private final MediaCard mediaCard;

    }
}
